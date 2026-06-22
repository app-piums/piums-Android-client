package com.piums.cliente

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.piums.cliente.BuildConfig
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_CONVERSATION_ID
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_NOTIF_ENTITY_ID
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_NOTIF_TYPE
import com.piums.cliente.ui.navigation.NavGraph
import com.piums.cliente.ui.theme.PiumsTheme
import com.piums.cliente.ui.theme.ThemeManager
import com.piums.cliente.utils.ChatSocketManager
import com.piums.cliente.utils.DeepLinkManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var deepLinkManager: DeepLinkManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var chatSocketManager: ChatSocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!BuildConfig.DEBUG && isRooted()) {
            AlertDialog.Builder(this)
                .setTitle("Dispositivo no seguro")
                .setMessage("Esta aplicación no puede ejecutarse en dispositivos con acceso root por razones de seguridad.")
                .setCancelable(false)
                .setPositiveButton("Cerrar") { _, _ -> finish() }
                .show()
            return
        }

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

    private fun isRooted(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in rootPaths) {
            if (File(path).exists()) return true
        }
        val buildTags = android.os.Build.TAGS
        if (!buildTags.isNullOrBlank() && buildTags.contains("test-keys")) return true
        return false
    }

    private fun handleIntent(intent: Intent?) {
        val type           = intent?.getStringExtra(EXTRA_NOTIF_TYPE)     ?: return
        val entityId       = intent.getStringExtra(EXTRA_NOTIF_ENTITY_ID)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)

        if (type == "NEW_MESSAGE" && conversationId != null) {
            chatSocketManager.setPendingConversationId(conversationId)
        }
        deepLinkManager.dispatchFromExtras(type, entityId)
    }
}
