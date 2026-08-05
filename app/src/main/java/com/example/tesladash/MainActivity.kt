export default function handler(req, res) {
  const { code } = req.query;

  if (!code) {
    return res.status(400).send('Authorization code missing');
  }

  const safeCode = encodeURIComponent(code);

  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.status(200).send(`
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>Tesla Login Redirect</title>
        <style>
            body {
                background-color: #0b0c10;
                color: #f3f4f6;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                min-height: 100vh;
                margin: 0;
                padding: 20px;
                text-align: center;
            }
            .card {
                background: linear-gradient(145deg, rgba(22, 24, 32, 0.95), rgba(15, 17, 23, 0.9));
                backdrop-filter: blur(12px);
                border: 1px solid rgba(255, 255, 255, 0.08);
                border-radius: 24px;
                padding: 40px 30px;
                max-width: 400px;
                width: 100%;
                box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
            }
            .icon {
                font-size: 64px;
                margin-bottom: 20px;
            }
            h1 {
                font-size: 20px;
                font-weight: 700;
                margin-bottom: 12px;
            }
            p {
                color: #9ca3af;
                font-size: 14px;
                margin-bottom: 24px;
                line-height: 1.6;
            }
            .btn {
                display: inline-block;
                background: linear-gradient(to right, #3b82f6, #6366f1);
                color: white;
                font-weight: 700;
                padding: 14px 32px;
                border-radius: 12px;
                text-decoration: none;
                font-size: 16px;
                transition: opacity 0.2s;
                border: none;
                cursor: pointer;
            }
            .btn:active {
                opacity: 0.8;
            }
            .spinner {
                display: inline-block;
                width: 24px;
                height: 24px;
                border: 3px solid rgba(255,255,255,0.1);
                border-top-color: #3b82f6;
                border-radius: 50%;
                animation: spin 0.8s linear infinite;
                margin: 0 auto 16px;
            }
            @keyframes spin {
                to { transform: rotate(360deg); }
            }
            .status {
                font-size: 13px;
                color: #6b7280;
                margin-top: 12px;
            }
            .hidden {
                display: none;
            }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="icon">✅</div>
            <h1>로그인 인증 완료</h1>
            <p>테슬라 계정 인증이 성공적으로 완료되었습니다.<br>앱으로 돌아가는 중입니다...</p>
            
            <div id="loading">
                <div class="spinner"></div>
                <div class="status">잠시만 기다려주세요...</div>
            </div>
            
            <div id="manualLink" class="hidden">
                <p style="color:#f59e0b;font-size:13px;">⚠️ 자동으로 이동하지 않으면 아래 버튼을 눌러주세요</p>
                <a href="tesladashk://oauth-callback?code=${safeCode}" class="btn">
                    🔗 앱으로 돌아가기
                </a>
            </div>
        </div>

        <script>
            console.log('🔐 Vercel Callback: code received');
            console.log('📋 Code: ${safeCode.substring(0, 20)}...');
            
            // 1️⃣ Android Bridge로 전달 시도
            function tryAndroidBridge() {
                if (window.AndroidBridge && window.AndroidBridge.sendOAuthCode) {
                    console.log('✅ Android Bridge found, sending code...');
                    window.AndroidBridge.sendOAuthCode('${safeCode}');
                    return true;
                }
                console.log('⚠️ Android Bridge not found');
                return false;
            }

            // 2️⃣ 딥링크로 이동
            function tryDeepLink() {
                console.log('🔗 Trying deep link...');
                var deepLinkUrl = "tesladashk://oauth-callback?code=${safeCode}";
                window.location.href = deepLinkUrl;
            }

            // 실행: Bridge 먼저 시도, 실패하면 딥링크
            var bridgeSuccess = tryAndroidBridge();
            
            if (!bridgeSuccess) {
                // Bridge 실패 → 딥링크 시도
                setTimeout(function() {
                    tryDeepLink();
                }, 500);
                
                // 3초 후에도 안 돌아가면 수동 링크 표시
                setTimeout(function() {
                    document.getElementById('loading').classList.add('hidden');
                    document.getElementById('manualLink').classList.remove('hidden');
                }, 3000);
            } else {
                // Bridge 성공 → 2초 후 자동 닫힘
                setTimeout(function() {
                    document.getElementById('loading').innerHTML = '<div class="status">✅ 앱으로 복귀 완료</div>';
                }, 1500);
            }
        </script>
    </body>
    </html>
  `);
}
