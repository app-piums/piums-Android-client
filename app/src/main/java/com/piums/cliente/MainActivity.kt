package com.piums.cliente

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_CONVERSATION_ID
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_NOTIF_ENTITY_ID
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_NOTIF_TYPE
import com.piums.cliente.data.auth.OAuthCallbackManager
import com.piums.cliente.ui.navigation.NavGraph
import com.piums.cliente.ui.theme.PiumsTheme
import com.piums.cliente.ui.theme.ThemeManager
import com.piums.cliente.utils.ChatSocketManager
import com.piums.cliente.utils.DeepLinkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var deepLinkManager: DeepLinkManager
    @Inject lateinit var oAuthCallbackManager: OAuthCallbackManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var chatSocketManager: ChatSocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val themeOverride by themeManager.override.collectAsState()
            val isDark = themeOverride != 0
            PiumsTheme(darkTheme = isDark) {
                NavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // OAuth callback: piums://auth/callback?jwt=...
        val data = intent?.data
        if (data?.scheme == "piums" && data.host == "auth") {
            val jwt = data.getQueryParameter("jwt") ?: data.getQueryParameter("token")
            if (jwt != null) {
                oAuthCallbackManager.dispatch(jwt)
                return
            }
        }

        val type           = intent?.getStringExtra(EXTRA_NOTIF_TYPE)     ?: return
        val entityId       = intent.getStringExtra(EXTRA_NOTIF_ENTITY_ID)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)

        if (type == "NEW_MESSAGE" && conversationId != null) {
            chatSocketManager.setPendingConversationId(conversationId)
        }
        deepLinkManager.dispatchFromExtras(type, entityId)
    }
}
