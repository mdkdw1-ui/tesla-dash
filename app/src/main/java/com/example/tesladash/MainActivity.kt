package com.example.tesladash  // 👈 본인의 패키지명으로 유지

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

        // 👇 서드파티 쿠키 허용 (테슬라 OAuth 인증 완료 단계에서 필요)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // 2. JavaScript Bridge 등록 (HTML에서 window.AndroidBridge로 접근)
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        // 3. WebViewClient (OAuth 리다이렉트를 WebView 내에서 가로채기)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // 👇 화면(가디언 탭 로그창)에 URL을 직접 출력 (Logcat 대체용 디버그)
                view?.evaluateJavascript(
                    "if (typeof addLog === 'function') addLog('🌐 URL: ' + '${url.replace("'", "\\'")}');",
                    null
                )

                // OAuth 콜백 감지: BASE_URL로 code 파라미터와 함께 돌아오는 경우
                if (url.startsWith(BASE_URL) && url.contains("code=")) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        Log.d(TAG, "🔐 OAuth code 감지: ${code.take(10)}...")
                        view?.evaluateJavascript(
                            "if (typeof addLog === 'function') addLog('🔐 code 감지: ${code.take(10)}...');" +
                            "window.handleOAuthCode('$code');",
                            null
                        )
                    } else {
                        view?.evaluateJavascript(
                            "if (typeof addLog === 'function') addLog('⚠️ code 파라미터 없음');",
                            null
                        )
                    }
                    return true  // 실제 페이지 이동 차단 (origin/상태 유지)
                }
                // 그 외 URL(테슬라 로그인 페이지 이동 등)은 WebView 내에서 정상 처리
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

        // 4. HTML 로드: 로컬 asset을 https origin(BASE_URL)으로 로드
        //    -> localStorage가 GitHub Pages와 동일 origin으로 취급되어 유지됨
        val htmlContent = assets.open("index.html")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        webView.loadDataWithBaseURL(
            BASE_URL,
            htmlContent,
            "text/html",
            "UTF-8",
            BASE_URL
        )

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
