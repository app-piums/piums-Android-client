package com.piums.cliente

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class PiumsClienteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = "PiumsClienteApp/1.0 (Android)"
            load(this@PiumsClienteApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            osmdroidTileCache = java.io.File(cacheDir, "osmdroid")
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Piums",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reservas, mensajes y actualizaciones de Piums"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID  = "piums_main"
        const val EXTRA_NOTIF_TYPE         = "notif_type"
        const val EXTRA_NOTIF_ENTITY_ID    = "notif_entity_id"
        const val EXTRA_CONVERSATION_ID    = "conversation_id"
    }
}
