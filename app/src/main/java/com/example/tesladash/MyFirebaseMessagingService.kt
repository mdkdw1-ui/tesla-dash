package com.example.tesladash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_WARNING = "guardian_warning"
        const val CHANNEL_ALARM = "guardian_alarm"

        fun createNotificationChannels(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val warningChannel = NotificationChannel(
                CHANNEL_WARNING, "가디언 경계 알림", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SentryGuard 상태 변화 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(soundUri, null)
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM, "가디언 침입 경보", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SentryGuard 침입 감지 경보"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(soundUri, null)
            }

            manager.createNotificationChannel(warningChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        MainActivity.injectFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "🛡️ 가디언 알림"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val level = message.data["level"] ?: "warning"
        val channelId = if (level == "alarm") CHANNEL_ALARM else CHANNEL_WARNING

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (level == "alarm") {
            builder.setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
        } else {
            builder.setVibrate(longArrayOf(0, 300, 150, 300))
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
