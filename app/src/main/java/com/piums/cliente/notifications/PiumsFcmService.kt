package com.piums.cliente.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.piums.cliente.MainActivity
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_CONVERSATION_ID
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_NOTIF_ENTITY_ID
import com.piums.cliente.PiumsClienteApp.Companion.EXTRA_NOTIF_TYPE
import com.piums.cliente.PiumsClienteApp.Companion.NOTIFICATION_CHANNEL_ID
import com.piums.cliente.R
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.FcmTokenRequest
import com.piums.cliente.utils.ChatSocketManager
import com.piums.cliente.utils.DeepLinkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class PiumsFcmService : FirebaseMessagingService() {

    @Inject lateinit var api: PiumsApiService
    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var deepLinkManager: DeepLinkManager
    @Inject lateinit var chatSocketManager: ChatSocketManager
    @Inject lateinit var notificationsStore: NotificationsStore

    override fun onNewToken(token: String) {
        tokenStorage.fcmToken = token
        if (tokenStorage.isLoggedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { api.registerPushToken(FcmTokenRequest(token)) }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type           = message.data["type"] ?: message.notification?.let { "GENERIC" } ?: return
        val entityId       = message.data["entityId"]
        val conversationId = message.data["conversationId"] ?: message.data["chatId"]
        val title          = message.data["title"]   ?: message.notification?.title ?: "Piums"
        val body           = message.data["message"] ?: message.notification?.body  ?: ""

        if (type == "NEW_MESSAGE" && conversationId != null) {
            chatSocketManager.setPendingConversationId(conversationId)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                notificationsStore.refresh()
            }
        }

        deepLinkManager.dispatchFromExtras(type, entityId)
        showNotification(title, body, type, entityId, conversationId)
    }

    private fun showNotification(
        title: String, body: String, type: String,
        entityId: String?, conversationId: String?
    ) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIF_TYPE, type)
            entityId?.let { putExtra(EXTRA_NOTIF_ENTITY_ID, it) }
            conversationId?.let { putExtra(EXTRA_CONVERSATION_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            abs(System.currentTimeMillis().toInt()),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(this)
                    .notify(abs(System.currentTimeMillis().toInt()), notification)
            } catch (_: SecurityException) { }
        }
    }
}
