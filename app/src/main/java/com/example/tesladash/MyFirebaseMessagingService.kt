package com.example.tesladash

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 토큰이 갱신되면 WebView에 주입
        MainActivity.injectFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // 푸시 알림 수신 시 처리 (선택)
        message.notification?.let {
            // 필요시 알림 표시
        }
    }
}
