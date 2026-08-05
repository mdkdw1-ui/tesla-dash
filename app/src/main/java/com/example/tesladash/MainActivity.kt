package com.example.tesladash  // 👈 본인의 패키지명으로 유지

import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val client = OkHttpClient()
    private var keepAliveJob: Thread? = null
    private var isKeepAliveRunning = false

    companion object {
        private const val TAG = "TeslaDash"
        private const val RENDER_BASE_URL = "https://tesla-sentry.onrender.com"
        private var mainActivityInstance: MainActivity? = null

        // FCM 서비스에서 호출할 토큰 주입 함수
        fun injectFcmToken(token: String) {
            mainActivityInstance?.runOnUiThread {
                mainActivityInstance?.webView?.evaluateJavascript(
                    "window.fcmToken = '$token'; console.log('✅ FCM Token injected');",
                    null
                )
                Log.d(TAG, "✅ FCM Token injected: $token")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivityInstance = this

        // 1. WebView 설정
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
        }

        // 2. JavaScript Bridge 등록 (HTML에서 window.AndroidBridge로 접근)
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        // 3. WebViewClient (OAuth 리다이렉트를 WebView 내에서 처리)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // 모든 URL을 WebView에서 직접 처리 (외부 브라우저로 열지 않음)
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 페이지 로드 완료 시 FCM 토큰 재주입 (안전장치)
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        task.result?.let { token ->
                            view?.evaluateJavascript(
                                "window.fcmToken = '$token'; console.log('🔄 FCM Token re-injected');",
                                null
                            )
                        }
                    }
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        // 4. HTML 로드 (assets/index.html)
        webView.loadUrl("file:///android_asset/index.html")

        // 5. 초기 FCM 토큰 가져오기 (onPageFinished에서도 하지만 미리 한 번)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.result?.let { token ->
                    webView.evaluateJavascript(
                        "window.fcmToken = '$token'; console.log('🔑 FCM Token pre-injected');",
                        null
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopKeepAlive()
        mainActivityInstance = null
    }

    // ============================================================
    // JavaScript Interface (HTML에서 호출 가능)
    // ============================================================
    inner class AndroidBridge {
        @JavascriptInterface
        fun startGuardianService(accessToken: String, vehicleId: String, interval: Int, topic: String) {
            Log.d(TAG, "🚀 Guardian START | Token: ${accessToken.take(20)}... Vehicle: $vehicleId")
            // Keep-Alive 시작 (Render 서버 수면 방지)
            startKeepAlive()
        }

        @JavascriptInterface
        fun stopGuardianService() {
            Log.d(TAG, "🛑 Guardian STOP")
            stopKeepAlive()
        }
    }

    // ============================================================
    // Keep-Alive (8분마다 /health 핑)
    // ============================================================
    private fun startKeepAlive() {
        if (isKeepAliveRunning) return
        isKeepAliveRunning = true

        keepAliveJob = Thread {
            while (isKeepAliveRunning) {
                try {
                    val request = Request.Builder()
                        .url("$RENDER_BASE_URL/health")
                        .build()
                    client.newCall(request).execute().close()
                    Log.d(TAG, "💓 Keep-Alive ping sent")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Keep-Alive failed: ${e.message}")
                }
                Thread.sleep(8 * 60 * 1000L) // 8분
            }
        }.apply { start() }
    }

    private fun stopKeepAlive() {
        isKeepAliveRunning = false
        keepAliveJob?.interrupt()
        keepAliveJob = null
        Log.d(TAG, "⏹️ Keep-Alive stopped")
    }
}
