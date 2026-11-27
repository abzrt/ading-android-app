(function () {
  if (window.__adingExtHooked) return;
  window.__adingExtHooked = true;

  window.__adingOrdersBySc = window.__adingOrdersBySc || {};
  window.__adingTeamsCache = window.__adingTeamsCache || [];
  window.__adingLastCopiedText = "";

  // Hook clipboard writes so programmatic click on "Salin" captures exact ADING text
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      const origWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);
      navigator.clipboard.writeText = function (txt) {
        if (typeof txt === "string" && txt.trim().length > 10) {
          window.__adingLastCopiedText = txt.trim();
        }
        return origWriteText(txt).catch(() => {});
      };
    }
    const origExec = document.execCommand ? document.execCommand.bind(document) : null;
    if (origExec) {
      document.execCommand = function (cmd) {
        if (String(cmd).toLowerCase() === "copy") {
          const sel = window.getSelection ? String(window.getSelection()) : "";
          const act = document.activeElement;
          const val = act && (act.value || act.textContent) ? String(act.value || act.textContent) : "";
          const cap = sel.trim().length > 10 ? sel.trim() : (val.trim().length > 10 ? val.trim() : "");
          if (cap) window.__adingLastCopiedText = cap;
        }
        return origExec.apply(document, arguments);
      };
    }
  } catch (_) {}

  function preloadTeams(endpoint) {
    fetch(window.location.origin + endpoint + "?_t=" + Date.now())
      .then((r) => (r.ok ? r.json() : []))
      .then((arr) => {
        if (Array.isArray(arr) && arr.length > 0) {
          window.__adingTeamsCache = window.__adingTeamsCache.concat(arr);
        }
      })
      .catch(() => {});
  }
  preloadTeams("/api/provisioning/team-options");
  preloadTeams("/api/mapping-tim");

  const origFetch = window.fetch;
  window.fetch = function (input, init) {
    const url = typeof input === "string" ? input : (input && input.url ? input.url : "");
    const method = init && init.method ? init.method.toUpperCase() : "GET";
    const reqBody = init && typeof init.body === "string" ? init.body : null;
    const promise = origFetch.apply(this, arguments);

    promise
      .then((resp) => {
        try {
          if (url.includes("/api/provisioning/team-options") || url.includes("/api/mapping-tim")) {
            resp.clone().json().then((arr) => {
              if (Array.isArray(arr) && arr.length > 0) {
                window.__adingTeamsCache = window.__adingTeamsCache.concat(arr);
              }
            }).catch(() => {});
          } else if (url.includes("/api/provisioning/undispatch") || url.includes("/api/provisioning/search-order")) {
            resp.clone().json().then((json) => {
              if (json && Array.isArray(json.data)) {
                json.data.forEach((row) => {
                  if (row && row.NO_SC) window.__adingOrdersBySc[row.NO_SC] = row;
                });
              }
            }).catch(() => {});
          } else if (
            method === "POST" &&
            (url.includes("/api/provisioning/dispatch-bulk") || url.includes("/api/provisioning/redispatch"))
          ) {
            if (resp.ok && reqBody) {
              const bodyObj = JSON.parse(reqBody);
              const item = bodyObj.items && bodyObj.items[0] ? bodyObj.items[0] : bodyObj;
              const noSc = item.no_sc || bodyObj.no_sc || "";
              const timId = bodyObj.mapping_tim_id || "";
              const namaTim = bodyObj.nama_tim || bodyObj.nama_tim_baru || "";
              const sa = item.service_area || bodyObj.service_area_baru || "";
              const tgl = bodyObj.tanggal_dispatch || bodyObj.tanggal_dispatch_baru || "";
              const assignedBy = bodyObj.dispatched_by_name || "";
              const cachedOrder = window.__adingOrdersBySc[noSc] || {};

              let cachedTeam = null;
              for (const t of window.__adingTeamsCache) {
                if (String(t.id) === String(timId) || t.nama_tim === namaTim) {
                  cachedTeam = t;
                  break;
                }
              }

              const cleanLeader =
                cachedTeam && cachedTeam.leader_name
                  ? String(cachedTeam.leader_name)
                  : namaTim
                  ? namaTim.split("(")[0].trim()
                  : "";
              const cleanNik =
                cachedTeam && cachedTeam.leader_nik
                  ? String(cachedTeam.leader_nik)
                  : cachedTeam && cachedTeam.chief_code
                  ? String(cachedTeam.chief_code)
                  : "";

              let resolvedWonum = item.wonum || cachedOrder.WONUM || "";
              if ((!resolvedWonum || resolvedWonum === "-") && noSc) {
                const domNodes = document.querySelectorAll("tr, .card, div");
                for (const node of domNodes) {
                  const dTxt = node.innerText || "";
                  if (dTxt.includes(noSc) && dTxt.includes("WO") && dTxt.length < 1800) {
                    const wm = dTxt.match(/\b(WO[0-9]{6,})\b/i);
                    if (wm) {
                      resolvedWonum = wm[1].toUpperCase();
                      break;
                    }
                  }
                }
              }

              // Click "Salin" button on the order card/row to capture exact formatted text
              window.__adingLastCopiedText = "";
              try {
                let salinBtn = null;
                const cards = document.querySelectorAll("tr, .card, div");
                for (const card of cards) {
                  const cTxt = card.innerText || "";
                  if (noSc && cTxt.includes(noSc) && cTxt.length < 2500) {
                    const btns = card.querySelectorAll('button, a, [role="button"]');
                    for (const b of btns) {
                      const lbl = (b.innerText || b.textContent || "").trim().toLowerCase();
                      if (lbl === "salin" || lbl.includes("salin")) {
                        salinBtn = b;
                        break;
                      }
                    }
                    if (salinBtn) break;
                  }
                }
                if (salinBtn) salinBtn.click();
              } catch (_) {}

              const payload = {
                no_sc: noSc,
                wonum: resolvedWonum === "-" ? "" : resolvedWonum,
                sto: item.sto || cachedOrder.STO || "",
                service_area: sa || (cachedTeam ? cachedTeam.service_area : ""),
                nama_tim: namaTim,
                leader_nik: cleanNik,
                leader_name: cleanLeader,
                paket: cachedOrder.PAKET || "",
                odp: cachedOrder.ODP || "",
                koordinat: cachedOrder.KORDINAT || "",
                booking_date: cachedOrder.BOOKING_DATE || (tgl ? tgl + " 08:00:00" : ""),
                assigned_by: assignedBy,
                copied_text: "",
              };

              setTimeout(() => {
                payload.copied_text = window.__adingLastCopiedText || "";
                if (noSc) {
                  window.postMessage({ source: "ADING_EXT_HOOK", type: "ADING_DISPATCH_TRIGGERED", payload }, "*");
                }
              }, 150);
            }
          }
        } catch (_) {}
      })
      .catch(() => {});

    return promise;
  };
})();
