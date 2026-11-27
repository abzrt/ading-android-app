const RAILWAY_WFM_STANDALONE_URL = "https://bot-production-a5484be7.up.railway.app/api/wfm/dispatch";
const ADING_API_KEY = "ading-wfm-2026-telkom";
const DEFAULT_FALLBACK_CHAT_ID = "217411824";

async function getFallbackChatId() {
  const stored = await chrome.storage.local.get(["fallbackChatId"]);
  return (stored.fallbackChatId || DEFAULT_FALLBACK_CHAT_ID).trim();
}

function notifyAdingTab(tabId, title, body, kind = "info") {
  if (!tabId) return;
  chrome.tabs.sendMessage(tabId, { type: "ADING_TOAST", title, body, kind }).catch(() => {});
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg && msg.type === "ADING_DISPATCH_TRIGGERED" && msg.payload) {
    const adingTabId = sender.tab ? sender.tab.id : null;
    startDispatchFlow(msg.payload, adingTabId);
    sendResponse({ ok: true });
    return true;
  }
});

async function startDispatchFlow(payload, adingTabId) {
  const wonum = (payload.wonum || "").trim();
  const noSc = (payload.no_sc || "").trim();
  const techDisplay = payload.leader_name
    ? `${payload.leader_name}${payload.leader_nik ? " (" + payload.leader_nik + ")" : ""}`
    : payload.nama_tim || "Teknisi";

  if (!wonum) {
    notifyAdingTab(adingTabId, "WONUM tidak ditemukan", `Order ${noSc} tidak memiliki WONUM untuk diproses di FLOW.`, "error");
    return;
  }

  notifyAdingTab(
    adingTabId,
    `Memproses ${wonum} (Standalone)`,
    `Server Railway sedang mengambil SS HD FLOW & mengirim ke channel ${techDisplay}...`,
    "info"
  );

  try {
    const fallbackChatId = await getFallbackChatId();
    const resp = await fetch(RAILWAY_WFM_STANDALONE_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Ading-Key": ADING_API_KEY,
      },
      body: JSON.stringify({
        wonum,
        leader_name: payload.leader_name || "",
        leader_nik: payload.leader_nik || "",
        nama_tim: payload.nama_tim || "",
        caption: (payload.copied_text || "").trim(),
        fallback_chat_id: fallbackChatId,
      }),
    });
    const res = await resp.json().catch(() => null);
    if (resp.ok && res && res.ok) {
      const targetLabel = (res.matched_titles || []).join(", ") || techDisplay;
      if (res.fallback) {
        notifyAdingTab(
          adingTabId,
          `⚠️ Dialihkan ke Chat Pribadi`,
          `Channel ${techDisplay} belum ditemukan. SS HD ${wonum} dikirim ke chat pribadi Anda.`,
          "info"
        );
      } else {
        notifyAdingTab(
          adingTabId,
          `✅ Terkirim ke Channel`,
          `SS HD ${wonum} + Data Unmasked otomatis terkirim ke ${targetLabel}!`,
          "success"
        );
      }
      return;
    }
    const errMsg = res && res.error ? res.error : `HTTP ${resp.status}`;
    notifyAdingTab(adingTabId, `❌ Gagal WFM Standalone`, `${wonum}: ${errMsg}`, "error");
  } catch (err) {
    notifyAdingTab(adingTabId, "Error Koneksi Railway", String(err && err.message ? err.message : err), "error");
  }
}
