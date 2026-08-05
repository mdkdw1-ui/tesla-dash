package com.example.tesladash

import android.content.Intent
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
import android.widget.Toast
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

        // 딥링크로 실행된 경우 code 처리
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
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                Log.d(TAG, "🌐 shouldOverrideUrlLoading: $url")

                // 🔥 커스텀 스킴 (딥링크) 처리
                if (url.startsWith("tesladashk://")) {
                    Log.d(TAG, "✅ Custom scheme detected: $url")
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        Log.d(TAG, "✅ Deep link intent launched")
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Deep link failed: ${e.message}")
                        // fallback: code 추출해서 직접 처리
                        val code = Uri.parse(url).getQueryParameter("code")
                        if (code != null) {
                            view?.evaluateJavascript(
                                "window.handleOAuthCode('$code');",
                                null
                            )
                        }
                        return true
                    }
                }

                // 🔥 Vercel callback에서 code 감지 (보험)
                if (url.contains("code=") && url.contains("tesla-sync-api.vercel.app")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        Log.d(TAG, "🔐 Code in Vercel URL: ${code.take(10)}...")
                        view?.evaluateJavascript(
                            "if (typeof addLog === 'function') addLog('🔐 Vercel code: ${code.take(10)}...');" +
                            "window.handleOAuthCode('$code');",
                            null
                        )
                        return true
                    }
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "📄 onPageFinished: $url")

                // URL에 code가 있으면 직접 처리 (최종 안전장치)
                if (url != null && url.contains("code=")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        Log.d(TAG, "🔐 Code in onPageFinished: ${code.take(10)}...")
                        view?.evaluateJavascript(
                            "if (typeof addLog === 'function') addLog('🔐 onPageFinished code: ${code.take(10)}...');" +
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    private fun handleDeepLink(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Log.d(TAG, "🔐 Deep Link code: ${code.take(10)}...")
            showToast("✅ code 수신: ${code.take(10)}...")
            webView.evaluateJavascript(
                "if (typeof addLog === 'function') addLog('🔐 Deep Link code: ${code.take(10)}...');" +
                "window.handleOAuthCode('$code');",
                null
            )
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
            showToast("🛡️ 가디언 시작")
            startKeepAlive()
        }

        @JavascriptInterface
        fun stopGuardianService() {
            Log.d(TAG, "🛑 Guardian STOP")
            showToast("🛑 가디언 중지")
            stopKeepAlive()
        }

        @JavascriptInterface
        fun sendOAuthCode(code: String) {
            Log.d(TAG, "🔐 OAuth from Vercel: ${code.take(10)}...")
            showToast("✅ code 수신: ${code.take(10)}...")
            runOnUiThread {
                webView.evaluateJavascript(
                    "if (typeof addLog === 'function') addLog('🔐 code 수신: ${code.take(10)}...');" +
                    "window.handleOAuthCode('$code');",
                    null
                )
            }
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
