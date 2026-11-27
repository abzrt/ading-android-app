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

document.addEventListener("DOMContentLoaded", async () => {
  const stored = await chrome.storage.local.get(["tgToken", "fallbackChatId", "techMapping"]);
  document.getElementById("tgToken").value = stored.tgToken || DEFAULT_CONFIG.tgToken;
  document.getElementById("fallbackChatId").value = stored.fallbackChatId || DEFAULT_CONFIG.fallbackChatId;
  document.getElementById("techMapping").value = stored.techMapping || DEFAULT_CONFIG.techMapping;

  document.getElementById("btnSave").addEventListener("click", async () => {
    await chrome.storage.local.set({
      tgToken: document.getElementById("tgToken").value.trim() || DEFAULT_CONFIG.tgToken,
      fallbackChatId: document.getElementById("fallbackChatId").value.trim() || DEFAULT_CONFIG.fallbackChatId,
      techMapping: document.getElementById("techMapping").value.trim() || DEFAULT_CONFIG.techMapping,
    });
    const st = document.getElementById("status");
    st.textContent = "✅ Pengaturan tersimpan!";
    setTimeout(() => (st.textContent = ""), 2500);
  });

  document.getElementById("btnFlow").addEventListener("click", () => {
    chrome.tabs.create({ url: "https://flow.telkomakses.co.id/" });
  });
});
