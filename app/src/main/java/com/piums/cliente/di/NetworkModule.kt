package com.piums.cliente.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
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

// Gson adapter: acepta números decimales (15.0) para campos Int/Int? del backend
// El backend usa JS/Node que no distingue int de double — p.ej. "servicesCount": 3.0
@Suppress("UNCHECKED_CAST")
private object FlexibleIntAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != Int::class.java && type.rawType != java.lang.Integer::class.java) return null
        val isPrimitive = type.rawType == Int::class.java
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) {
                if (value == null) out.nullValue() else out.value((value as Int).toLong())
            }
            override fun read(reader: JsonReader): T? = when (reader.peek()) {
                JsonToken.NULL    -> { reader.nextNull(); if (isPrimitive) 0 as T else null }
                JsonToken.NUMBER  -> reader.nextDouble().toInt() as T
                JsonToken.STRING  -> (reader.nextString().toDoubleOrNull()?.toInt() ?: 0) as T
                JsonToken.BOOLEAN -> (if (reader.nextBoolean()) 1 else 0) as T
                else              -> { reader.skipValue(); if (isPrimitive) 0 as T else null }
            }
        } as TypeAdapter<T>
    }
}

// SHA-256 pins for client.piums.io (verificados 2026-06-10)
// Obtén el pin real con: openssl s_client -connect client.piums.io:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
// El dominio está detrás de Cloudflare: el cert edge rota ~90 días y el emisor
// puede alternar entre Let's Encrypt y Google Trust Services, por eso se
// incluyen pins de respaldo a nivel raíz de ambas CAs.
private val CERTIFICATE_PINNER = CertificatePinner.Builder()
    .add("client.piums.io", "sha256/ER8v0GmGJasfGMzQ5zfDC86y7MQaWq1o+JgQ8Ob2z6c=") // leaf piums.io (vence 2026-07-21)
    .add("client.piums.io", "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU=") // Let's Encrypt E8 (intermediate)
    .add("client.piums.io", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1 (backup)
    .add("client.piums.io", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=") // GTS Root R1 (backup Cloudflare)
    .add("client.piums.io", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=") // GTS Root R4 (backup Cloudflare)
    .build()

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapterFactory(FlexibleIntAdapterFactory)
        .create()

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
