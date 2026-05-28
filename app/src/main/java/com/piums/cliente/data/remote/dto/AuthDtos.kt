package com.piums.cliente.data.remote.dto

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(val nombre: String, val email: String, val password: String)

data class FirebaseAuthRequest(val idToken: String, val role: String = "cliente")

data class RefreshRequest(val refreshToken: String)

data class ForgotPasswordRequest(val email: String)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

data class UpdateProfileRequest(val nombre: String)

data class FcmTokenRequest(val token: String, val platform: String = "android")

data class AuthResponse(
    val accessToken: String?,
    val token: String?,           // backend puede usar cualquiera
    val refreshToken: String?,
    val user: UserDto?
) {
    val jwt: String? get() = accessToken ?: token
}

data class UserDto(
    val id: String,
    val email: String,
    val nombre: String?,
    val role: String,
    val avatar: String?,
    val isVerified: Boolean?
) {
    val displayName: String get() = nombre?.takeIf { it.isNotBlank() } ?: email
    val avatarUrl: String? get() = avatar
}

data class MeResponse(val user: UserDto?, val id: String?, val email: String?,
                      val nombre: String?, val role: String?, val avatar: String?,
                      val isVerified: Boolean? = null) {
    fun toUserDto() = user ?: UserDto(
        id = id ?: "", email = email ?: "", nombre = nombre,
        role = role ?: "cliente", avatar = avatar, isVerified = isVerified
    )
}
