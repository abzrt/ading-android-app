// Bridge between page MAIN world (ading_page_hook.js) and Chrome Extension background service worker
window.addEventListener("message", (event) => {
  if (event.source !== window || !event.data || event.data.source !== "ADING_EXT_HOOK") return;
  if (event.data.type === "ADING_DISPATCH_TRIGGERED" && event.data.payload) {
    chrome.runtime.sendMessage({
      type: "ADING_DISPATCH_TRIGGERED",
      payload: event.data.payload,
    });
  }
});

chrome.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === "ADING_TOAST") {
    showAdingToast(msg.title || "ADING Auto-Dispatch", msg.body || "", msg.kind || "info");
  }
});

function showAdingToast(title, body, kind) {
  let container = document.getElementById("__ading_ext_toast_box");
  if (!container) {
    container = document.createElement("div");
    container.id = "__ading_ext_toast_box";
    container.style.cssText =
      "position:fixed;right:20px;bottom:20px;z-index:999999;display:flex;flex-direction:column;gap:8px;max-width:360px;font-family:system-ui,-apple-system,sans-serif;";
    document.body.appendChild(container);
  }

  const accent = kind === "success" ? "#059669" : kind === "error" ? "#DC2626" : "#1E3A8A";
  const badgeBg = kind === "success" ? "#ECFDF5" : kind === "error" ? "#FEF2F2" : "#EEF2FF";

  const toast = document.createElement("div");
  toast.style.cssText =
    `background:#FFFFFF;border:1px solid #E5E7EB;border-left:4px solid ${accent};border-radius:12px;` +
    "padding:12px 14px;box-shadow:0 10px 25px rgba(0,0,0,0.12);color:#111827;font-size:13px;line-height:1.4;";
  toast.innerHTML =
    `<div style="display:inline-block;background:${badgeBg};color:${accent};font-weight:700;font-size:10px;padding:2px 8px;border-radius:99px;margin-bottom:6px;">ADING • WFM FLOW</div>` +
    `<div style="font-weight:700;color:#111827;margin-bottom:2px;">${escapeHtml(title)}</div>` +
    `<div style="color:#4B5563;font-size:12px;white-space:pre-line;">${escapeHtml(body)}</div>`;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transition = "opacity 0.3s ease";
    setTimeout(() => toast.remove(), 350);
  }, 6000);
}

function escapeHtml(s) {
  return String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
