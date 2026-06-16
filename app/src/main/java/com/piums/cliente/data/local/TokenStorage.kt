package com.piums.cliente.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val secure: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "piums_cliente_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val plain: SharedPreferences =
        context.getSharedPreferences("piums_cliente_prefs", Context.MODE_PRIVATE)

    var accessToken: String?
        get()      = secure.getString(KEY_ACCESS_TOKEN, null)
        set(value) = secure.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get()      = secure.getString(KEY_REFRESH_TOKEN, null)
        set(value) = secure.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var userId: String?
        get()      = plain.getString(KEY_USER_ID, null)
        set(value) = plain.edit().putString(KEY_USER_ID, value).apply()

    var userName: String?
        get()      = plain.getString(KEY_USER_NAME, null)
        set(value) = plain.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String?
        get()      = plain.getString(KEY_USER_EMAIL, null)
        set(value) = plain.edit().putString(KEY_USER_EMAIL, value).apply()

    var avatarUrl: String?
        get()      = plain.getString(KEY_AVATAR_URL, null)
        set(value) = plain.edit().putString(KEY_AVATAR_URL, value).apply()

    var onboardingDone: Boolean
        get()      = plain.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = plain.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var tutorialDone: Boolean
        get()      = plain.getBoolean(KEY_TUTORIAL_DONE, false)
        set(value) = plain.edit().putBoolean(KEY_TUTORIAL_DONE, value).apply()

    var fcmToken: String?
        get()      = plain.getString(KEY_FCM_TOKEN, null)
        set(value) = plain.edit().putString(KEY_FCM_TOKEN, value).apply()

    var biometricEnabled: Boolean
        get()      = plain.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = plain.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    // -1 = follow system, 0 = force light, 1 = force dark
    var darkModeOverride: Int
        get()      = plain.getInt(KEY_DARK_MODE_OVERRIDE, 1)
        set(value) = plain.edit().putInt(KEY_DARK_MODE_OVERRIDE, value).apply()

    // "not_submitted" | "pending" | "approved"
    var identityVerificationStatus: String
        get()      = plain.getString(KEY_IDENTITY_STATUS, "not_submitted") ?: "not_submitted"
        set(value) = plain.edit().putString(KEY_IDENTITY_STATUS, value).apply()

    var dpiVerificationFrontDone: Boolean
        get()      = plain.getBoolean(KEY_DPI_FRONT_DONE, false)
        set(value) = plain.edit().putBoolean(KEY_DPI_FRONT_DONE, value).apply()

    var dpiVerificationBackDone: Boolean
        get()      = plain.getBoolean(KEY_DPI_BACK_DONE, false)
        set(value) = plain.edit().putBoolean(KEY_DPI_BACK_DONE, value).apply()

    var dpiVerificationSelfieDone: Boolean
        get()      = plain.getBoolean(KEY_SELFIE_DONE, false)
        set(value) = plain.edit().putBoolean(KEY_SELFIE_DONE, value).apply()

    var savedInterests: String
        get()      = plain.getString(KEY_SAVED_INTERESTS, "") ?: ""
        set(value) = plain.edit().putString(KEY_SAVED_INTERESTS, value).apply()

    val isLoggedIn: Boolean get() = accessToken != null

    fun clear() {
        // Preserve device-level prefs that should survive logout
        val savedOnboarding = onboardingDone
        val savedTutorial   = tutorialDone
        val savedTheme      = darkModeOverride
        val savedInt        = savedInterests
        secure.edit().clear().apply()
        plain.edit().clear().apply()
        onboardingDone   = savedOnboarding
        tutorialDone     = savedTutorial
        darkModeOverride = savedTheme
        savedInterests   = savedInt
    }

    companion object {
        private const val KEY_ACCESS_TOKEN      = "jwt"
        private const val KEY_REFRESH_TOKEN     = "refresh"
        private const val KEY_USER_ID           = "user_id"
        private const val KEY_USER_NAME         = "user_name"
        private const val KEY_USER_EMAIL        = "user_email"
        private const val KEY_AVATAR_URL        = "avatar_url"
        private const val KEY_ONBOARDING_DONE   = "onboarding_done"
        private const val KEY_TUTORIAL_DONE     = "tutorial_done"
        private const val KEY_FCM_TOKEN         = "fcm_token"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DARK_MODE_OVERRIDE = "dark_mode_override"
        private const val KEY_IDENTITY_STATUS   = "identity_status"
        private const val KEY_DPI_FRONT_DONE    = "dpi_front_done"
        private const val KEY_DPI_BACK_DONE     = "dpi_back_done"
        private const val KEY_SELFIE_DONE       = "selfie_done"
        private const val KEY_SAVED_INTERESTS   = "saved_interests"
    }
}
