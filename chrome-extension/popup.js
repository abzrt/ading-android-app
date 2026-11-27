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
      healthEl.textContent = `🟢 Server Railway Aktif (${data.known_chats || 0} channel terdeteksi)`;
      healthEl.style.color = "#059669";
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
