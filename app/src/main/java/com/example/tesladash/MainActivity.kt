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

    // 🔽 OAuth code 처리용 상태
    private val cachedHtmlContent: String by lazy {
        assets.open("index.html")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }
    private var pendingOAuthCode: String? = null
    private var lastProcessedCode: String? = null

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

                // 🔥 커스텀 스킴(딥링크) — OAuth 콜백의 정상 경로
                if (url.startsWith("tesladashk://")) {
                    Log.d(TAG, "✅ Custom scheme detected: $url")
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        Log.d(TAG, "✅ Deep link intent launched")
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Deep link intent failed: ${e.message}, fallback으로 직접 처리")
                        handleDeepLink(Uri.parse(url))
                        return true
                    }
                }

                // ⚠️ auth.tesla.com, Vercel callback 등 나머지는 절대 여기서 가로채지 않는다.
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "📄 onPageFinished: $url")

                // 🛟 안전장치: Vercel 콜백 페이지가 실제로 로드된 경우, code를 바로 회수
                if (url != null && url.contains("code=") && url.contains("tesla-sync-api.vercel.app")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        Log.d(TAG, "🔐 [Fallback] Vercel 콜백 URL에서 code 회수: ${code.take(10)}...")
                        processOAuthCode(code)
                        return
                    }
                }

                // index.html이 (재)로드 완료된 시점에 대기 중인 code를 주입
                pendingOAuthCode?.let { code ->
                    Log.d(TAG, "🔐 대기 중이던 code 주입: ${code.take(10)}...")
                    webView.evaluateJavascript(
                        "if (typeof addLog === 'function') addLog('🔐 로그인 코드 처리 중: ${code.take(10)}...');" +
                        "if (typeof window.handleOAuthCode === 'function') { window.handleOAuthCode('$code'); } " +
                        "else { console.error('handleOAuthCode 함수를 찾을 수 없습니다.'); }",
                        null
                    )
                    pendingOAuthCode = null
                }

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

        // 초기 HTML 로드
        webView.loadDataWithBaseURL(
            BASE_URL,
            cachedHtmlContent,
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

        // 콜드 스타트(딥링크로 앱이 처음 실행된 경우) — webView 준비 이후 처리
        intent?.data?.let { uri -> handleDeepLink(uri) }
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
            processOAuthCode(code)
        }
    }

    // 🔽 OAuth code 처리 공통 로직
    private fun processOAuthCode(code: String) {
        if (code == lastProcessedCode) {
            Log.d(TAG, "⏭️ 이미 처리된 code, 중복 스킵: ${code.take(10)}...")
            return
        }
        lastProcessedCode = code

        Log.d(TAG, "🔐 OAuth code 처리 시작: ${code.take(10)}...")
        showToast("✅ 로그인 코드 수신, 처리 중...")

        pendingOAuthCode = code
        webView.post {
            webView.loadDataWithBaseURL(
                BASE_URL,
                cachedHtmlContent,
                "text/html",
                "UTF-8",
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
            Log.d(TAG, "🔐 OAuth from JS bridge: ${code.take(10)}...")
            runOnUiThread { processOAuthCode(code) }
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
