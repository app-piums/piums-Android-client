package com.piums.cliente.data.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE_OAUTH_URL = "https://client.piums.io/api/auth/oauth"
private const val CALLBACK_URI   = "piums://auth/callback"
private const val TIMEOUT_MS     = 5 * 60 * 1000L

@Singleton
class OAuthWebLoginHelper @Inject constructor(
    private val callbackManager: OAuthCallbackManager
) {
    suspend fun login(context: Context, provider: String): String {
        val url = "$BASE_OAUTH_URL/$provider?redirect_uri=${Uri.encode(CALLBACK_URI)}"
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK,
                CustomTabColorSchemeParams.Builder().build()
            )
            .build()
        intent.launchUrl(context, Uri.parse(url))

        return withTimeout(TIMEOUT_MS) {
            callbackManager.callbacks.first()
        }
    }
}
