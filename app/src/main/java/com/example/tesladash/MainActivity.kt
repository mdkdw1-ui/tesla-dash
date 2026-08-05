package com.example.tesladash

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            allowUniversalAccessFromFileURLs = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {

            // 🔥 핵심: 페이지 로드 시작 시 code 감지 (동일 origin 리다이렉트도 잡음)
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // 디버그 로그
                view?.evaluateJavascript(
                    "if (typeof addLog === 'function') addLog('🔄 onPageStarted: ' + '${url?.replace("'", "\\'") ?: ""}');",
                    null
                )

                if (url != null && url.contains("code=")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        Log.d(TAG, "🔐 OAuth code 감지 (onPageStarted): ${code.take(10)}...")
                        view?.stopLoading() // 페이지 로드 중단!
                        view?.evaluateJavascript(
                            "if (typeof addLog === 'function') addLog('🔐 code 감지됨 (onPageStarted): ${code.take(10)}...');" +
                            "window.handleOAuthCode('$code');",
                            null
                        )
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // 디버그 로그
                view?.evaluateJavascript(
                    "if (typeof addLog === 'function') addLog('🌐 shouldOverrideUrlLoading: ' + '${url.replace("'", "\\'")}');",
                    null
                )

                // 혹시 여기서도 잡을 수 있으면 잡음 (보험)
                if (url.contains("code=")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        Log.d(TAG, "🔐 OAuth code 감지 (shouldOverride): ${code.take(10)}...")
                        view?.evaluateJavascript(
                            "if (typeof addLog === 'function') addLog('🔐 code 감지됨 (shouldOverride): ${code.take(10)}...');" +
                            "window.handleOAuthCode('$code');",
                            null
                        )
                    }
                    return true
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 🔥 안전장치: 페이지 로드 완료 후에도 code가 있으면 처리
                if (url != null && url.contains("code=")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        view?.evaluateJavascript(
                            "window.handleOAuthCode('$code');",
                            null
                        )
                    }
                }

                // FCM 토큰 재주입
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

    override fun onDestroy() {
        super.onDestroy()
        stopKeepAlive()
        mainActivityInstance = null
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun startGuardianService(accessToken: String, vehicleId: String, interval: Int, topic: String) {
            Log.d(TAG, "🚀 Guardian START | Token: ${accessToken.take(20)}... Vehicle: $vehicleId")
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
