package com.piums.cliente.data.remote

import com.google.gson.Gson
import com.piums.cliente.BuildConfig
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.utils.AuthEventBus
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val gson: Gson,
    private val authEventBus: AuthEventBus
) : Authenticator {

    // Client sin autenticador — evita dependencia circular con el cliente principal
    private val refreshClient by lazy { OkHttpClient() }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Sin token previo o ya reintentado → no reintentar
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenStorage.refreshToken ?: run {
            tokenStorage.clear()
            authEventBus.emitLogoutRequired()
            return null
        }

        return synchronized(this) {
            // Si otro hilo ya refrescó el token, reusar el nuevo
            val stored = tokenStorage.accessToken
            if (stored != null && "Bearer $stored" != response.request.header("Authorization")) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $stored")
                    .build()
            }

            val body = gson.toJson(mapOf("refreshToken" to refreshToken))
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder()
                .url("${BuildConfig.BASE_URL}api/auth/refresh")
                .patch(body)
                .build()

            try {
                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                when {
                    refreshResponse.isSuccessful -> {
                        @Suppress("UNCHECKED_CAST")
                        val json = gson.fromJson(
                            refreshResponse.body?.string() ?: return@synchronized null,
                            Map::class.java
                        ) as Map<String, Any?>

                        val newAccess = json["accessToken"] as? String ?: json["token"] as? String
                            ?: return@synchronized null  // malformed response — no logout, just retry later

                        (json["refreshToken"] as? String)?.let { tokenStorage.refreshToken = it }
                        tokenStorage.accessToken = newAccess

                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccess")
                            .build()
                    }
                    refreshResponse.code == 401 -> {
                        // Token revocado explícitamente — único caso donde cerramos sesión
                        tokenStorage.clear()
                        authEventBus.emitLogoutRequired()
                        null
                    }
                    else -> {
                        // 429, 5xx u otro error temporal — mantener sesión, no forzar logout
                        null
                    }
                }
            } catch (_: Exception) {
                // Error de red / timeout — mantener sesión activa, la próxima request reintentará
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var r = response.priorResponse
        while (r != null) { count++; r = r.priorResponse }
        return count
    }
}
