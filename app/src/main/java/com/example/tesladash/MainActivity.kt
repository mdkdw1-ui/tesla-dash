package com.example.tesladash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
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
        private const val BASE_URL = "https://mdkdw1-ui.github.io/tesla-dash"
        private var mainActivityInstance: MainActivity? = null

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

        // 🔥 딥링크로 실행된 경우 code 처리
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

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

        // HTML 로드 (BASE_URL origin으로 고정)
        val htmlContent = assets.open("index.html")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        webView.loadDataWithBaseURL(
            BASE_URL,
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )

        // 초기 FCM 토큰 주입
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

    // 🔥 새로운 Intent로 실행될 때 (딥링크)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    // 🔥 딥링크 처리
    private fun handleDeepLink(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Log.d(TAG, "🔐 Deep Link code: ${code.take(10)}...")
            webView.evaluateJavascript(
                "if (typeof addLog === 'function') addLog('🔐 Deep Link code: ${code.take(10)}...');" +
                "window.handleOAuthCode('$code');",
                null
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopKeepAlive()
        mainActivityInstance = null
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun startGuardianService(accessToken: String, vehicleId: String, interval: Int, topic: String) {
            Log.d(TAG, "🚀 Guardian START")
            startKeepAlive()
        }

        @JavascriptInterface
        fun stopGuardianService() {
            Log.d(TAG, "🛑 Guardian STOP")
            stopKeepAlive()
        }
    }

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
                Thread.sleep(8 * 60 * 1000L)
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
