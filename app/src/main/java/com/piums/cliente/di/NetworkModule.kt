package com.piums.cliente.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.piums.cliente.BuildConfig
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.AuthInterceptor
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.TokenAuthenticator
import com.piums.cliente.utils.AuthEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// SHA-256 pins for client.piums.io
// Obtén el pin real con: openssl s_client -connect client.piums.io:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
// TODO: reemplazar el pin del leaf cert con el valor real antes de release
private val CERTIFICATE_PINNER = CertificatePinner.Builder()
    .add("client.piums.io", "sha256/REEMPLAZAR_CON_PIN_REAL_DEL_LEAF_CERT=")
    .add("client.piums.io", "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=") // Let's Encrypt E8 (intermediate)
    .build()

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    @Provides @Singleton
    fun provideOkHttpClient(
        tokenStorage: TokenStorage,
        gson: Gson,
        authEventBus: AuthEventBus
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStorage))
            .authenticator(TokenAuthenticator(tokenStorage, gson, authEventBus))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false) // evita POSTs duplicados (bookings/pagos)

        // Certificate pinning — disabled in debug builds to allow proxies/Charles
        if (!BuildConfig.DEBUG) {
            builder.certificatePinner(CERTIFICATE_PINNER)
        }

        return builder.build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides @Singleton
    fun providePiumsApiService(retrofit: Retrofit): PiumsApiService =
        retrofit.create(PiumsApiService::class.java)
}
