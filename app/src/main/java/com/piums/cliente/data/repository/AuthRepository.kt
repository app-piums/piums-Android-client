package com.piums.cliente.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: PiumsApiService,
    private val tokenStorage: TokenStorage
) {
    val isLoggedIn: Boolean get() = tokenStorage.isLoggedIn
    val currentUserId: String? get() = tokenStorage.userId

    suspend fun login(email: String, password: String): Result<UserDto> = runCatching {
        val resp = api.login(LoginRequest(email.trim().lowercase(), password))
        persist(resp)
        resp.user ?: error("No user in response")
    }

    suspend fun register(nombre: String, email: String, password: String): Result<UserDto> = runCatching {
        val resp = api.register(RegisterRequest(nombre, email, password))
        persist(resp)
        resp.user ?: error("No user in response")
    }

    suspend fun loginWithGoogle(idToken: String): Result<UserDto> = runCatching {
        val resp = api.loginWithFirebase(FirebaseAuthRequest(idToken = idToken, role = "cliente"))
        persist(resp)
        if (tokenStorage.avatarUrl == null) {
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()?.let {
                tokenStorage.avatarUrl = it
            }
        }
        resp.user ?: error("No user in response")
    }

    suspend fun loginWithOAuth(jwt: String): Result<UserDto> = runCatching {
        tokenStorage.accessToken = jwt
        // OAuth JWTs don't have a refreshToken — call getMe to verify and populate profile.
        // Network/server errors don't fail the login; only a 401 does (per iOS behavior).
        val user = runCatching { api.getMe().toUserDto() }.getOrElse { e ->
            if (e is HttpException && e.code() == 401) throw e
            null
        }
        user?.let {
            tokenStorage.userId    = it.id
            tokenStorage.userName  = it.nombre
            tokenStorage.userEmail = it.email
            tokenStorage.avatarUrl = it.avatar
        }
        user ?: UserDto(id = "", email = "", nombre = null, role = "cliente", avatar = null, isVerified = null)
    }

    suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
        api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase()))
    }

    suspend fun logout() {
        runCatching { api.logout() }
        tokenStorage.clear()
    }

    suspend fun getMe(): Result<UserDto> = runCatching {
        val resp = api.getMe()
        resp.toUserDto()
    }

    suspend fun completeOnboarding() {
        tokenStorage.onboardingDone = true
        runCatching { api.completeOnboarding() }
    }

    private fun persist(resp: AuthResponse) {
        resp.jwt?.let { tokenStorage.accessToken = it }
        resp.refreshToken?.let { tokenStorage.refreshToken = it }
        resp.user?.let { u ->
            tokenStorage.userId    = u.id
            tokenStorage.userName  = u.nombre
            tokenStorage.userEmail = u.email
            tokenStorage.avatarUrl = u.avatar
            tokenStorage.identityVerificationStatus = when {
                u.identityApproved        -> "approved"
                u.hasSubmittedIdentity    -> "pending"
                else                      -> "not_submitted"
            }
        }
    }
}
