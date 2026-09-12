package dev.agentbayu.app.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dev.agentbayu.app.R

class AssistantKeepaliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun startInForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keepalive_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_spark)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.keepalive_notification))
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        private const val TAG = "AgentBayu"
        private const val CHANNEL_ID = "assistant_keepalive"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, AssistantKeepaliveService::class.java)
                )
            } catch (error: Exception) {
                Log.e(TAG, "Unable to start assistant keepalive", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantKeepaliveService::class.java))
        }
    }
}
