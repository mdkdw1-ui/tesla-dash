package com.example.tesladash

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val TAG = "TeslaMainActivity"
        private const val OAUTH_SCHEME = "tesladashk"
        private const val OAUTH_HOST = "oauth-callback"
        private const val TESLA_AUTH_HOST = "auth.tesla.com"
        private const val VERCEL_CALLBACK_HOST = "my-tesla-app-six.vercel.app"
    }

    /** OAuth code that arrives before the page finishes loading (timing race) */
    private var pendingOauthCode: String? = null
    /** true after loadDataWithBaseURL has been called and onPageFinished fired */
    private var pageReady: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "$userAgentString TeslaDashK/1.0"
            }

            // Enable cookies (Tesla OAuth needs them)
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@apply, true)
            }

            webViewClient = TeslaWebViewClient()
            webChromeClient = WebChromeClient()

            // Expose AndroidBridge for Guardian service control
            addJavascriptInterface(
                object {
                    @android.webkit.JavascriptInterface
                    fun startGuardianService(intervalSec: Int, ntfyTopic: String) {
                        Log.d(TAG, "startGuardianService: interval=$intervalSec topic=$ntfyTopic")
                        // GuardianService 클래스를 만든 후 아래 주석 해제
                        // val svc = Intent(this@MainActivity, GuardianService::class.java).apply {
                        //     putExtra("interval", intervalSec)
                        //     putExtra("ntfy_topic", ntfyTopic)
                        // }
                        // startForegroundService(svc)
                        Log.d(TAG, "GuardianService not implemented yet")
                    }

                    @android.webkit.JavascriptInterface
                    fun stopGuardianService() {
                        Log.d(TAG, "stopGuardianService")
                        // stopService(Intent(this@MainActivity, GuardianService::class.java))
                    }
                },
                "AndroidBridge"
            )
        }

        setContentView(webView)

        // Load the assembled HTML
        loadAssembledHtml()

        // If launched via OAuth callback intent, capture the code
        handleOAuthIntent(intent)
    }

    // ──────────────────────────────────────────────────────────
    //  HTML Assembly: read 6 asset files and concatenate
    // ──────────────────────────────────────────────────────────
    private fun loadAssembledHtml() {
        val assetFiles = listOf("index.html", "part1.html", "part2.html", "part3.html", "part4.html", "part5.html")
        val sb = StringBuilder()

        for (name in assetFiles) {
            try {
                val reader = BufferedReader(
                    InputStreamReader(assets.open(name), Charsets.UTF_8)
                )
                var line = reader.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = reader.readLine()
                }
                reader.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read asset: $name", e)
            }
        }

        val fullHtml = sb.toString()
        Log.d(TAG, "Assembled HTML size: ${fullHtml.length} chars")

        webView.loadDataWithBaseURL(
            "https://tesla-dash-k.local/",
            fullHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    // ──────────────────────────────────────────────────────────
    //  WebViewClient: intercept OAuth redirect, inject code
    // ──────────────────────────────────────────────────────────
    inner class TeslaWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val url = request?.url ?: return false
            val scheme = url.scheme ?: return false
            val host = url.host ?: ""

            Log.d(TAG, "shouldOverrideUrlLoading: $url")

            // 1. Custom scheme: tesladashk://oauth-callback?code=XXX
            if (scheme == OAUTH_SCHEME && host == OAUTH_HOST) {
                val code = url.getQueryParameter("code")
                if (!code.isNullOrEmpty()) {
                    Log.d(TAG, "OAuth code captured from custom scheme")
                    injectOauthCode(code)
                }
                return true  // handled
            }

            // 2. Vercel callback redirect (fallback)
            if (host == VERCEL_CALLBACK_HOST && url.path?.contains("callback") == true) {
                val code = url.getQueryParameter("code")
                if (!code.isNullOrEmpty()) {
                    Log.d(TAG, "OAuth code captured from Vercel callback URL")
                    injectOauthCode(code)
                }
                return true
            }

            // 3. Tesla auth page — let it load in WebView
            if (host == TESLA_AUTH_HOST || host.endsWith(".$TESLA_AUTH_HOST")) {
                return false
            }

            // 4. Everything else (CDN scripts, fetch API calls) — let WebView handle
            return false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "onPageFinished: $url")
            pageReady = true

            // If OAuth code arrived while page was still loading, inject now
            pendingOauthCode?.let { code ->
                Log.d(TAG, "Injecting pending OAuth code")
                pendingOauthCode = null
                injectOauthCode(code)
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  OAuth Code Injection
    // ──────────────────────────────────────────────────────────
    private fun injectOauthCode(code: String) {
        val safeCode = code.replace("'", "\\'").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
        val js = "window.ANDROID_OAUTH_CODE = '$safeCode'; if (typeof window.handleAndroidOauthCode === 'function') { window.handleAndroidOauthCode('$safeCode'); }"

        if (pageReady) {
            webView.post {
                webView.evaluateJavascript(js, null)
                Log.d(TAG, "OAuth code injected via evaluateJavascript")
            }
        } else {
            // Page not ready yet — stash for onPageFinished
            pendingOauthCode = safeCode
            Log.d(TAG, "OAuth code stashed (page not ready)")
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Intent Handling (singleTask → onNewIntent)
    // ──────────────────────────────────────────────────────────
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        val scheme = data.scheme ?: return
        val host = data.host ?: return

        Log.d(TAG, "handleOAuthIntent: scheme=$scheme host=$host url=$data")

        if (scheme == OAUTH_SCHEME && host == OAUTH_HOST) {
            val code = data.getQueryParameter("code")
            if (!code.isNullOrEmpty()) {
                Log.d(TAG, "OAuth code from intent: ${code.take(8)}...")
                injectOauthCode(code)
            } else {
                Log.w(TAG, "OAuth callback intent without code parameter")
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        webView.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
