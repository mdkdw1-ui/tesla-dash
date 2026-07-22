package com.example.tesladash

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GuardianService : Service() {

    private val CHANNEL_ID = "TeslaGuardianChannel"
    private val NOTIF_ID = 8888
    private var serviceJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "STOP") {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        val token = intent?.getStringExtra("TOKEN") ?: ""
        val vehicleId = intent?.getStringExtra("VEHICLE_ID") ?: ""
        val interval = intent?.getIntExtra("INTERVAL", 5) ?: 5
        val ntfyTopic = intent?.getStringExtra("NTFY_TOPIC") ?: "MJYAz6ZyjXiujaTDpJ"

        val notification = buildNotification("🛡️ 테슬라 감시 가디언 작동 중...")
        startForeground(NOTIF_ID, notification)

        startMonitoring(token, vehicleId, interval, ntfyTopic)

        return START_STICKY
    }

    private fun startMonitoring(token: String, vehicleId: String, intervalSec: Int, topic: String) {
        serviceJob?.cancel()
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkSentryStatus(token, vehicleId, topic)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun checkSentryStatus(token: String, vehicleId: String, topic: String) {
        if (token.isEmpty() || vehicleId.isEmpty()) return

        val jsonObj = JSONObject().apply {
            put("token", token)
            put("vehicleId", vehicleId)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonObj.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://my-tesla-app-six.vercel.app/api/sentry")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resText = response.body?.string() ?: ""
                    val json = JSONObject(resText)
                    val sentryActive = json.optBoolean("sentry_mode", false)

                    if (!sentryActive) {
                        // 감시 모드 OFF 감지 시 푸시 알림 후 가디언 자동 종료
                        sendNtfyNotification(topic, "⚠️ 테슬라 경고", "차량의 감시 모드(Sentry Mode)가 OFF로 전환되었습니다.")
                        stopSelf()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendNtfyNotification(topic: String, title: String, message: String) {
        val request = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .header("Title", title)
            .header("Priority", "high")
            .header("Tags", "car,warning")
            .post(message.toRequestBody("text/plain".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopMonitoring() {
        serviceJob?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tesla Guardian Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tesla Dash Guardian")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
