package com.example.tesladash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // 알림 권한 요청 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setupWebView()
        webView.loadUrl("https://mdkdw1-ui.github.io/tesla-dash")
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // 데스크톱/표준 모바일 브라우저로 위장하여 OAuth 차단 방지
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // 웹뷰 내부에서 웹 JS -> 안드로이드 네이티브 호출 가능하도록 설정
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                // 외부 브라우저 이탈 방지: 테슬라 인증 및 GitHub Pages 내에서만 이동
                if (url.contains("auth.tesla.com") || url.contains("github.io/tesla-dash")) {
                    return false // WebView 내부에서 페이지 이동 허용
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 페이지 로드 완료 시 필요한 JS 주입이 있다면 수행
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    // JS 브릿지 클래스
    inner class WebAppInterface {
        @JavascriptInterface
        fun startGuardianService(token: String, vehicleId: String, intervalSec: Int, topic: String) {
            val intent = Intent(this@MainActivity, GuardianService::class.java).apply {
                action = "START"
                putExtra("TOKEN", token)
                putExtra("VEHICLE_ID", vehicleId)
                putExtra("INTERVAL", intervalSec)
                putExtra("NTFY_TOPIC", topic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, "🛡️ 안드로이드 백그라운드 가디언 가동", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun stopGuardianService() {
            val intent = Intent(this@MainActivity, GuardianService::class.java).apply {
                action = "STOP"
            }
            startService(intent)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "⏹️ 백그라운드 가디언 정지됨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
