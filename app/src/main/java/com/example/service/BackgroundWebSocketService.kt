package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.pref.ServerConfig

class BackgroundWebSocketService : Service() {

    companion object {
        private const val CHANNEL_ID = "streampay_service"
        private const val NOTIFICATION_ID = 4859

        fun startService(context: Context) {
            try {
                val intent = Intent(context, BackgroundWebSocketService::class.java)
                // Start as standard service to avoid strict startForegroundService background transition crashes
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("BackgroundService", "Failed starting Stream service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundWebSocketService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("BackgroundService", "Service onCreate triggered")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BackgroundService", "Service onStartCommand triggered")

        startBackgroundWebSocketConnection()

        return START_STICKY
    }

    private fun startBackgroundWebSocketConnection() {
        val config = ServerConfig(applicationContext)
        val ip = config.ipAddress
        val port = config.port
        val userId = config.lastSavedUserId
        Log.i("BackgroundService", "Starting mock socket context pointing to: ws://$ip:$port/ws/user/$userId")
    }

    override fun onDestroy() {
        Log.d("BackgroundService", "Foreground Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de sincronización StreamPay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
