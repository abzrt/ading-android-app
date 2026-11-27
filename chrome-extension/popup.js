const DEFAULT_FALLBACK_CHAT_ID = "217411824";
const RAILWAY_HEALTH_URL = "https://bot-production-a5484be7.up.railway.app/health";

document.addEventListener("DOMContentLoaded", async () => {
  const stored = await chrome.storage.local.get(["fallbackChatId"]);
  document.getElementById("fallbackChatId").value = stored.fallbackChatId || DEFAULT_FALLBACK_CHAT_ID;

  const healthEl = document.getElementById("health");
  try {
    const resp = await fetch(RAILWAY_HEALTH_URL);
    const data = await resp.json();
    if (resp.ok && data && data.ok) {
      const flowTxt = data.wfm_session_active ? "FLOW Aktif" : "FLOW butuh /otp";
      const woCount = data.dispatches_today || 0;
      healthEl.textContent = `🟢 Server OK • ${flowTxt} • ${data.known_chats || 0} ch • ${woCount} WO hari ini`;
      healthEl.style.color = data.wfm_session_active ? "#059669" : "#D97706";
    } else {
      healthEl.textContent = "🟠 Server Railway merespons tidak normal";
      healthEl.style.color = "#D97706";
    }
  } catch (_) {
    healthEl.textContent = "🔴 Tidak dapat menghubungi server Railway";
    healthEl.style.color = "#DC2626";
  }

  document.getElementById("btnSave").addEventListener("click", async () => {
    await chrome.storage.local.set({
      fallbackChatId: document.getElementById("fallbackChatId").value.trim() || DEFAULT_FALLBACK_CHAT_ID,
    });
    const st = document.getElementById("status");
    st.textContent = "✅ Pengaturan tersimpan!";
    setTimeout(() => (st.textContent = ""), 2500);
  });

  document.getElementById("btnBot").addEventListener("click", () => {
    chrome.tabs.create({ url: "https://t.me/biarndalupa_bot" });
  });
});
