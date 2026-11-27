const RAILWAY_BRIDGE_URL = "https://bot-production-a5484be7.up.railway.app/api/ading/dispatch";

const DEFAULT_CONFIG = {
  tgToken: "9111712229:AAERRc7RmQMXy4LSIvA-mty5Ckw6q-b0cUQ",
  fallbackChatId: "217411824",
  techMapping: [
    "ARIFIN=-1002202217652,@Mimpi_bergadang",
    "MUHAMMAD RISQI=-1002165761538",
    "AHMAD SYAHRIZAK RAMADHANI=-1002280689139",
    "ANJELLO=-1002960116555,@embu_mamo_SK",
    "ANDI MUHAMMAD RAJAB=-1003818907805",
    "MUHAMMAD NAZAR ALFATH=-1004329000380",
    "ALIF JUNAIDI=-1002975699950",
  ].join("\n"),
};

async function getConfig() {
  const stored = await chrome.storage.local.get(["tgToken", "fallbackChatId", "techMapping"]);
  return {
    tgToken: (stored.tgToken || DEFAULT_CONFIG.tgToken).trim(),
    fallbackChatId: (stored.fallbackChatId || DEFAULT_CONFIG.fallbackChatId).trim(),
    techMapping: (stored.techMapping || DEFAULT_CONFIG.techMapping).trim(),
  };
}

function notifyAdingTab(tabId, title, body, kind = "info") {
  if (!tabId) return;
  chrome.tabs.sendMessage(tabId, { type: "ADING_TOAST", title, body, kind }).catch(() => {});
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg && msg.type === "ADING_DISPATCH_TRIGGERED" && msg.payload) {
    const adingTabId = sender.tab ? sender.tab.id : null;
    const adingWinId = sender.tab ? sender.tab.windowId : null;
    handleDispatchToFlowAndTelegram(msg.payload, adingTabId, adingWinId);
    sendResponse({ ok: true });
    return true;
  }
});

async function handleDispatchToFlowAndTelegram(payload, adingTabId, adingWinId) {
  const wonum = (payload.wonum || "").trim();
  const noSc = (payload.no_sc || "").trim();
  const techDisplay = payload.leader_name
    ? `${payload.leader_name}${payload.leader_nik ? " (" + payload.leader_nik + ")" : ""}`
    : payload.nama_tim || "Teknisi";

  if (!wonum) {
    notifyAdingTab(adingTabId, "WONUM tidak ditemukan", `Order ${noSc} tidak memiliki WONUM untuk dibuka di FLOW.`, "error");
    return;
  }

  notifyAdingTab(adingTabId, `Memproses ${wonum}`, `Membuka FLOW & mengambil SS HD untuk ${techDisplay}...`, "info");

  const targetUrl = `https://flow.telkomakses.co.id/EditWorkorder/${encodeURIComponent(wonum)}`;
  let flowTab = null;

  try {
    // Reuse an existing FLOW tab if open, otherwise create one in the same window
    const existingFlowTabs = await chrome.tabs.query({ url: "https://flow.telkomakses.co.id/*" });
    if (existingFlowTabs.length > 0) {
      flowTab = existingFlowTabs[0];
      await chrome.tabs.update(flowTab.id, { url: targetUrl, active: true });
    } else {
      flowTab = await chrome.tabs.create({ url: targetUrl, active: true, windowId: adingWinId || undefined });
    }

    // Wait for Workorder card to appear (up to 90s in case user needs to enter OTP once)
    const ready = await waitForWorkorderCard(flowTab.id, targetUrl, adingTabId);
    if (!ready) {
      notifyAdingTab(adingTabId, "Timeout FLOW Telkom Akses", `Gagal memuat kartu ${wonum}. Pastikan Anda sudah login di tab FLOW.`, "error");
      return;
    }

    // Make sure FLOW tab is active & focused right before captureVisibleTab
    await chrome.tabs.update(flowTab.id, { active: true });
    await sleep(350);

    // Extract unmasked FLOW fields + hide nav + scroll + get exact crop bounding box
    const [{ result: cardInfo }] = await chrome.scripting.executeScript({
      target: { tabId: flowTab.id },
      func: prepareFlowCardAndMeasure,
    });

    await sleep(200);
    const dataUrl = await chrome.tabs.captureVisibleTab(flowTab.windowId, { format: "png" });

    // Restore nav bar on FLOW tab
    await chrome.scripting.executeScript({
      target: { tabId: flowTab.id },
      func: () => {
        document.querySelectorAll("nav, header, .navbar").forEach((n) => (n.style.display = ""));
      },
    }).catch(() => {});

    // Return focus right back to ADING tab
    if (adingTabId) {
      await chrome.tabs.update(adingTabId, { active: true }).catch(() => {});
    }

    // Crop PNG to exact Workorder card box
    const croppedBlob = await cropDataUrlToBlob(dataUrl, cardInfo && cardInfo.rect ? cardInfo.rect : null);

    // Unmask ADING "Salin" caption using unmasked FLOW fields
    const rawCaption = (payload.copied_text || "").trim() || buildFallbackCaption(payload, cardInfo);
    const unmaskedCaption = unmaskCaptionWithFlowData(
      rawCaption,
      cardInfo ? cardInfo.customer_name : "",
      cardInfo ? cardInfo.service_address : "",
      cardInfo ? cardInfo.contact_number : "",
      cardInfo ? cardInfo.service_number : ""
    );

    // 1. Try Auto-Discovery Railway Bot Bridge first (auto-matches ANY channel where @biarndalupa_bot is Admin by NIK/Name!)
    const cfg = await getConfig();
    const bridgeResult = await sendViaRailwayAutoDiscovery(croppedBlob, unmaskedCaption, payload, cfg.fallbackChatId);
    if (bridgeResult && bridgeResult.ok) {
      const targetLabel = (bridgeResult.matched_titles || []).join(", ") || techDisplay;
      if (bridgeResult.fallback) {
        notifyAdingTab(adingTabId, `⚠️ Dialihkan ke Chat Pribadi`, `Channel ${techDisplay} belum ditemukan. SS ${wonum} dikirim ke chat pribadi Anda.`, "info");
      } else {
        notifyAdingTab(adingTabId, `✅ Terkirim ke Channel`, `${wonum} otomatis terkirim ke ${targetLabel}!`, "success");
      }
      return;
    }

    // 2. Fallback direct Telegram API if Railway bridge is unreachable
    const { numericTargets, mentions } = resolveTechnicianTargets(
      cfg.techMapping,
      payload.leader_name,
      payload.nama_tim,
      payload.leader_nik,
      cfg.fallbackChatId
    );
    const finalCaption = mentions.length > 0 ? `${unmaskedCaption}\n\n👷 ${mentions.join(" ")}` : unmaskedCaption;

    let sentCount = 0;
    for (const cid of numericTargets) {
      const ok = await uploadPhotoToTelegram(cfg.tgToken, cid, croppedBlob, finalCaption);
      if (ok) sentCount++;
    }

    let fallbackSent = false;
    if (sentCount === 0 && cfg.fallbackChatId && !numericTargets.includes(cfg.fallbackChatId)) {
      const fbCap = `⚠️ Gagal kirim ke channel ${techDisplay} (pastikan @biarndalupa_bot jadi Admin channel).\n\n${finalCaption}`;
      fallbackSent = await uploadPhotoToTelegram(cfg.tgToken, cfg.fallbackChatId, croppedBlob, fbCap);
    }

    if (sentCount > 0) {
      notifyAdingTab(adingTabId, `✅ Terkirim ke Telegram`, `${wonum} (${techDisplay}) berhasil dikirim ke channel teknisi!`, "success");
    } else if (fallbackSent) {
      notifyAdingTab(adingTabId, `⚠️ Dialihkan ke Chat Pribadi`, `Channel ${techDisplay} gagal (bot belum Admin). SS ${wonum} dikirim ke chat pribadi Anda.`, "info");
    } else {
      notifyAdingTab(adingTabId, `❌ Gagal Kirim Telegram`, `SS ${wonum} gagal dikirim ke Telegram. Cek token/koneksi.`, "error");
    }
  } catch (err) {
    notifyAdingTab(adingTabId, "Error Otomasi FLOW", String(err && err.message ? err.message : err), "error");
  }
}

async function waitForWorkorderCard(tabId, targetUrl, adingTabId) {
  let warnedLogin = false;
  for (let i = 0; i < 180; i++) {
    await sleep(500);
    let tab;
    try {
      tab = await chrome.tabs.get(tabId);
    } catch (_) {
      return false;
    }
    const url = (tab.url || "").toLowerCase();
    if (url.includes("/unauthorize")) {
      await chrome.tabs.update(tabId, { url: "https://flow.telkomakses.co.id/" });
      continue;
    }
    if (url === "https://flow.telkomakses.co.id/" || url.includes("/otp") || url.includes("/login")) {
      if (!warnedLogin) {
        warnedLogin = true;
        notifyAdingTab(adingTabId, "Sesi FLOW Butuh Login/OTP", "Silakan selesaikan login/OTP di tab FLOW. Otomasi akan langsung lanjut!", "info");
      }
      continue;
    }
    if (url.includes("/home") || url.includes("/dashboard")) {
      await chrome.tabs.update(tabId, { url: targetUrl });
      continue;
    }
    if (url.includes("/editworkorder/")) {
      try {
        const [{ result: isReady }] = await chrome.scripting.executeScript({
          target: { tabId },
          func: () => {
            const t = (document.body && document.body.innerText) || "";
            return t.includes("OSS Order ID") || t.includes("Service Number") || t.includes("Customer Name");
          },
        });
        if (isReady) return true;
      } catch (_) {}
    }
  }
  return false;
}

function prepareFlowCardAndMeasure() {
  function getValByLabel(lbl) {
    const nodes = document.querySelectorAll("label, div, span, td, th");
    for (const n of nodes) {
      if (n.children.length > 1) continue;
      const t = (n.innerText || n.textContent || "").trim();
      if (t.toLowerCase().includes(lbl.toLowerCase())) {
        const sib = n.nextElementSibling;
        if (sib) {
          const inp = sib.matches("input,textarea,select") ? sib : sib.querySelector("input,textarea,select,.form-control");
          if (inp) return (inp.value || inp.innerText || "").trim();
        }
        const p = n.parentElement;
        if (p) {
          const pInps = p.querySelectorAll("input,textarea,select,.form-control");
          if (pInps.length === 1 && pInps[0] !== n) return (pInps[0].value || pInps[0].innerText || "").trim();
          const psib = p.nextElementSibling;
          if (psib) {
            const inp3 = psib.matches("input,textarea,select") ? psib : psib.querySelector("input,textarea,select,.form-control");
            if (inp3) return (inp3.value || inp3.innerText || "").trim();
          }
        }
      }
    }
    return "";
  }

  const wo = getValByLabel("Workorder:");
  const srv = getValByLabel("Service Number");
  const contact = getValByLabel("Contact Number");
  const cust = getValByLabel("Customer Name");
  const addr = getValByLabel("Service Address");

  document.querySelectorAll("nav, header, .navbar").forEach((n) => (n.style.display = "none"));

  let tabBtnEl = null;
  let redBarEl = null;
  let prodLblEl = null;
  let schedEl = null;
  const scanNodes = document.querySelectorAll("a, button, li, div, label, span, h5, h6");
  for (const sn of scanNodes) {
    if (sn.children.length > 2) continue;
    const st = (sn.innerText || "").trim();
    if (!tabBtnEl && (st === "Related Record" || st === "Plans")) tabBtnEl = sn;
    if (!redBarEl && st === "Workorder" && sn.getBoundingClientRect().width > 400) redBarEl = sn;
    if (!prodLblEl && st.toLowerCase().startsWith("product name")) prodLblEl = sn;
    if (!schedEl && st === "Scheduling Information") schedEl = sn;
  }

  let rect = null;
  const anchorEl = tabBtnEl || redBarEl || document.querySelector("main form");
  if (anchorEl) {
    const absTop = anchorEl.getBoundingClientRect().top + window.scrollY;
    window.scrollTo(0, Math.max(0, absTop - 12));
    const br = (redBarEl || anchorEl).getBoundingClientRect();
    const topY = tabBtnEl ? tabBtnEl.getBoundingClientRect().top - 8 : br.top - 8;
    let botY = br.bottom + 420;
    if (prodLblEl) {
      let prodBottom = prodLblEl.getBoundingClientRect().bottom;
      const pSib = prodLblEl.nextElementSibling;
      if (pSib) prodBottom = Math.max(prodBottom, pSib.getBoundingClientRect().bottom);
      else if (prodLblEl.parentElement && prodLblEl.parentElement.nextElementSibling) {
        prodBottom = Math.max(prodBottom, prodLblEl.parentElement.nextElementSibling.getBoundingClientRect().bottom);
      }
      botY = prodBottom + 18;
    }
    if (schedEl) {
      const schedTop = schedEl.getBoundingClientRect().top - 10;
      if (schedTop > topY + 180) botY = prodLblEl ? Math.min(botY, schedTop) : schedTop;
    }
    const leftX = Math.max(0, br.left - 16);
    const rightX = Math.min(window.innerWidth, br.right + 16);
    rect = {
      left: leftX,
      top: Math.max(0, topY),
      width: Math.max(200, rightX - leftX),
      height: Math.max(200, botY - Math.max(0, topY)),
      vw: window.innerWidth,
      vh: window.innerHeight,
    };
  }

  return { wo, service_number: srv, contact_number: contact, customer_name: cust, service_address: addr, rect };
}

async function cropDataUrlToBlob(dataUrl, rect) {
  const resp = await fetch(dataUrl);
  const fullBlob = await resp.blob();
  if (!rect || !rect.vw || !rect.vh) return fullBlob;

  const bmp = await createImageBitmap(fullBlob);
  const scaleX = bmp.width / rect.vw;
  const scaleY = bmp.height / rect.vh;
  const sx = Math.max(0, Math.round(rect.left * scaleX));
  const sy = Math.max(0, Math.round(rect.top * scaleY));
  const sw = Math.min(bmp.width - sx, Math.round(rect.width * scaleX));
  const sh = Math.min(bmp.height - sy, Math.round(rect.height * scaleY));

  if (sw < 200 || sh < 120) {
    bmp.close();
    return fullBlob;
  }

  const canvas = new OffscreenCanvas(sw, sh);
  const ctx = canvas.getContext("2d");
  ctx.drawImage(bmp, sx, sy, sw, sh, 0, 0, sw, sh);
  bmp.close();
  return await canvas.convertToBlob({ type: "image/png" });
}

function unmaskCaptionWithFlowData(rawCaption, flowCust, flowAddr, flowContact, flowSrv) {
  return rawCaption
    .split(/\r?\n/)
    .map((line) => {
      const upper = line.trim().toUpperCase();
      if (upper.startsWith("CUSTOMER NAME:") && flowCust && !flowCust.includes("*")) return `CUSTOMER NAME: ${flowCust}`;
      if (upper.startsWith("ALAMAT:") && flowAddr && !flowAddr.includes("*")) return `ALAMAT: ${flowAddr}`;
      if (upper.startsWith("CONTACT NUMBER:") && line.includes("*") && flowContact && !flowContact.includes("*"))
        return `CONTACT NUMBER: ${flowContact}`;
      if (upper.startsWith("NO INTERNET:") && line.includes("*") && flowSrv && !flowSrv.includes("*"))
        return `NO INTERNET: ${flowSrv}`;
      return line;
    })
    .join("\n");
}

function buildFallbackCaption(payload, cardInfo) {
  const c = cardInfo || {};
  return [
    `NO SC: ${payload.no_sc || "-"}`,
    `WONUM: ${c.wo || payload.wonum || "-"}`,
    `BOOKING DATE: ${payload.booking_date || "-"}`,
    `STO: ${payload.sto || payload.service_area || "-"}`,
    `PAKET: ${payload.paket || "-"}`,
    `NO INTERNET: ${c.service_number || "-"}`,
    `CUSTOMER NAME: ${c.customer_name || "-"}`,
    `CONTACT NUMBER: ${c.contact_number || "-"}`,
    `ALAMAT: ${c.service_address || "-"}`,
    `ODP: ${payload.odp || "-"}`,
    `KORDINAT: ${payload.koordinat || "-"}`,
  ].join("\n");
}

function resolveTechnicianTargets(mappingRaw, leaderName, namaTim, leaderNik, fallbackChatId) {
  const numericTargets = [];
  const mentions = [];
  const hayLeader = (leaderName || "").trim().toUpperCase();
  const hayTim = (namaTim || "").trim().toUpperCase();
  const hayNik = (leaderNik || "").trim().toUpperCase();

  for (const line of (mappingRaw || "").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || !trimmed.includes("=")) continue;
    const eq = trimmed.indexOf("=");
    const key = trimmed.slice(0, eq).trim().toUpperCase();
    const vals = trimmed.slice(eq + 1).trim();
    if (!key || !vals) continue;

    const matched =
      (hayLeader && (hayLeader.includes(key) || key.includes(hayLeader))) ||
      (hayTim && hayTim.includes(key)) ||
      (hayNik && key === hayNik);

    if (matched) {
      for (const part of vals.split(",")) {
        const v = part.trim();
        if (!v) continue;
        if (v.startsWith("@")) {
          if (!mentions.includes(v)) mentions.push(v);
        } else if (!numericTargets.includes(v)) {
          numericTargets.push(v);
        }
      }
    }
  }
  if (numericTargets.length === 0 && fallbackChatId) {
    numericTargets.push(fallbackChatId);
  }
  return { numericTargets, mentions };
}

async function sendViaRailwayAutoDiscovery(pngBlob, caption, payload, fallbackChatId) {
  try {
    const buf = await pngBlob.arrayBuffer();
    const bytes = new Uint8Array(buf);
    let binary = "";
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    const resp = await fetch(RAILWAY_BRIDGE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        leader_name: payload.leader_name || "",
        leader_nik: payload.leader_nik || "",
        nama_tim: payload.nama_tim || "",
        caption,
        photo_base64: btoa(binary),
        fallback_chat_id: fallbackChatId || "",
      }),
    });
    if (!resp.ok) return null;
    return await resp.json();
  } catch (_) {
    return null;
  }
}

async function uploadPhotoToTelegram(token, chatId, pngBlob, caption) {
  try {
    const fd = new FormData();
    fd.append("chat_id", chatId);
    fd.append("caption", caption.length > 1020 ? caption.slice(0, 1020) : caption);
    fd.append("photo", pngBlob, "workorder.png");
    const resp = await fetch(`https://api.telegram.org/bot${token}/sendPhoto`, {
      method: "POST",
      body: fd,
    });
    return resp.ok;
  } catch (_) {
    return false;
  }
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}
