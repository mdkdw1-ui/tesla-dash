// ============================================================
// 🔄 Fast Sync (sync_fast.js)
// ============================================================
const VERCEL_FAST_SYNC_URL = "https://my-tesla-app.vercel.app/api/sync_fast";

async function handleFastSync() {
  if (typeof addLog === 'function') addLog('⚡ 빠른 동기화 (sync_fast) 요청 중...');
  const indicator = document.getElementById('sync-status-indicator');
  if (indicator) indicator.innerText = "⏳ Fast 동기화 중...";

  try {
    const res = await fetch(VERCEL_FAST_SYNC_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        token: accessToken, 
        vehicleId: configObj.vehicleId 
      })
    });
    const data = await res.json();
    if (res.ok && data.success) {
      if (typeof addLog === 'function') addLog(`✅ Fast Sync 완료! (${data.summary?.driving || 0}건 driving, ${data.summary?.vehicle || 0}건 vehicle)`);
    } else {
      if (typeof addLog === 'function') addLog(`⚠️ Fast Sync 응답: ${JSON.stringify(data)}`);
    }
  } catch(e) {
    if (typeof addLog === 'function') addLog(`⚠️ Fast Sync 호출 예외: ${e.message}`);
  }

  await handleRefresh(false);
}

// ============================================================
// 🔄 Full Sync (sync.js)
// ============================================================
const VERCEL_SYNC_URL = "https://my-tesla-app.vercel.app/api/sync";

async function handleFullSync() {
  if (typeof addLog === 'function') addLog('⚡ 전체 동기화 (sync.js) 요청 중...');
  const indicator = document.getElementById('sync-status-indicator');
  if (indicator) indicator.innerText = "⏳ 전체 동기화 중...";

  try {
    const res = await fetch(VERCEL_SYNC_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        token: accessToken, 
        vehicleId: configObj.vehicleId 
      })
    });
    const data = await res.json();
    if (res.ok && data.success) {
      if (typeof addLog === 'function') addLog('✅ Full Sync 완료!');
    } else {
      if (typeof addLog === 'function') addLog(`⚠️ Full Sync 응답: ${JSON.stringify(data)}`);
    }
  } catch(e) {
    if (typeof addLog === 'function') addLog(`⚠️ Full Sync 호출 예외: ${e.message}`);
  }

  await handleRefresh(false);
}
