package com.piums.cliente.data.remote

import com.piums.cliente.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage
) : Interceptor {

    // Endpoints that don't require authentication — matches iOS requiresAuth = false
    private val publicPaths = setOf(
        "api/auth/login",
        "api/auth/register/client",
        "api/auth/firebase",
        "api/auth/forgot-password",
        "api/auth/refresh"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimStart('/')

        // Skip auth header for public endpoints — prevents TokenAuthenticator from
        // retrying login/register on 401, which would double-count server rate limit hits
        if (publicPaths.any { path.startsWith(it) }) {
            return chain.proceed(request)
        }

        val token = tokenStorage.accessToken
        val authed = if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}
