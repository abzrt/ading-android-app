package id.web.ading;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WfmFlowAutomationManager {

    public static final String FLOW_LOGIN_URL = "https://flow.telkomakses.co.id/";
    public static final String FLOW_WORKORDER_URL = "https://flow.telkomakses.co.id/workorder";
    public static final String FLOW_EDIT_WORKORDER_URL = "https://flow.telkomakses.co.id/EditWorkorder/";
    public static final String ADING_URL = "https://ading.web.id/";

    public static final String DEFAULT_FLOW_USER = "16830482";
    public static final String DEFAULT_FLOW_PASS = "narindo1";
    public static final String DEFAULT_TG_TOKEN = "9111712229:AAERRc7RmQMXy4LSIvA-mty5Ckw6q-b0cUQ";
    public static final String DEFAULT_TG_CHAT_ID = "217411824";
    public static final String RAILWAY_BRIDGE_URL = "https://bot-production-a5484be7.up.railway.app/api/ading/dispatch";
    public static final String RAILWAY_WFM_STANDALONE_URL = "https://bot-production-a5484be7.up.railway.app/api/wfm/dispatch";
    public static final String RAILWAY_API_KEY = "ading-wfm-2026-telkom";
    public static final String DEFAULT_TECH_MAPPING =
            "ARIFIN=-1002202217652,@Mimpi_bergadang\n" +
            "MUHAMMAD RISQI=-1005839757356\n" +
            "AHMAD SYAHRIZAL RAMADHANI=-1002280689139\n" +
            "ANJELLO=-1002960116555,@embu_mamo_SK\n" +
            "ANDI MUHAMMAD RAJAB=-1003818907805\n" +
            "MUHAMMAD NAZAR ALFATH=-1004329000380\n" +
            "ALIF JUNAIDI=-1002975699950";

    private static final String PREFS_NAME = "ading_wfm_prefs";
    private static final String KEY_FLOW_USER = "flow_username";
    private static final String KEY_FLOW_PASS = "flow_password";
    private static final String KEY_TG_TOKEN = "tg_bot_token";
    private static final String KEY_TG_CHAT_ID = "tg_chat_id";
    private static final String KEY_TG_THREAD_ID = "tg_thread_id";
    private static final String KEY_TECH_MAPPING = "tg_tech_mapping";

    private static final String KEY_FLOW_COOKIES = "flow_saved_cookies";

    private static final String BG_CHANNEL_ID = "ading_bg_dispatch_pipeline";
    private static final String BG_CHANNEL_NAME = "Otomasi Background WFM FLOW";
    private static final int NOTIF_PROGRESS_ID = 8801;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService tgExecutor = Executors.newSingleThreadExecutor();
    private static long sActiveRunId = 0L;

    public static SharedPreferences getPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if ("Narindo@2026".equals(prefs.getString(KEY_FLOW_PASS, ""))) {
            prefs.edit().putString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS).apply();
        }
        return prefs;
    }

    public static void saveCredentials(
            Context context,
            String flowUser,
            String flowPass
    ) {
        SharedPreferences prefs = getPrefs(context);
        prefs.edit()
                .putString(KEY_FLOW_USER, (flowUser != null && !flowUser.trim().isEmpty()) ? flowUser.trim() : DEFAULT_FLOW_USER)
                .putString(KEY_FLOW_PASS, (flowPass != null && !flowPass.isEmpty()) ? flowPass : DEFAULT_FLOW_PASS)
                .apply();
    }

    public static void saveTelegramConfig(Context context, String token, String chatId, String threadId) {
        saveTelegramConfig(context, token, chatId, threadId, null);
    }

    public static void saveTelegramConfig(Context context, String token, String chatId, String threadId, String techMapping) {
        SharedPreferences prefs = getPrefs(context);
        SharedPreferences.Editor ed = prefs.edit()
                .putString(KEY_TG_TOKEN, (token != null && !token.trim().isEmpty()) ? token.trim() : DEFAULT_TG_TOKEN)
                .putString(KEY_TG_CHAT_ID, (chatId != null && !chatId.trim().isEmpty()) ? chatId.trim() : DEFAULT_TG_CHAT_ID)
                .putString(KEY_TG_THREAD_ID, threadId != null ? threadId.trim() : "");
        if (techMapping != null) {
            ed.putString(KEY_TECH_MAPPING, techMapping.trim().isEmpty() ? DEFAULT_TECH_MAPPING : techMapping.trim());
        }
        ed.apply();
    }


    /**
     * Flushes WebView CookieManager to disk so FLOW session & Remember Me cookies persist natively
     * across dispatches and app restarts without corrupting HttpOnly/Expires attributes.
     */
    public static void persistCookies(Context context) {
        if (context == null) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.flush();
        } catch (Exception ignored) {
        }
    }

    /**
     * Ensures CookieManager is enabled so Android WebView uses its native persistent cookie store.
     * Does NOT overwrite HttpOnly/Remember-Me cookies with stripped values.
     */
    public static void restoreCookies(Context context) {
        if (context == null) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
        } catch (Exception ignored) {
        }
    }

    private static void ensureBgNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(BG_CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        BG_CHANNEL_ID,
                        BG_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                );
                ch.setDescription("Notifikasi proses latar belakang pencarian WFM FLOW");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private static void updateProgressNotification(Context context, String title, String message, boolean ongoing) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            ensureBgNotificationChannel(context);
            NotificationCompat.Builder b = new NotificationCompat.Builder(context, BG_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setColor(Color.parseColor("#F9010F"))
                    .setSubText("Ading Mobile • Dispatch")
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(message))
                    .setOngoing(ongoing)
                    .setPriority(ongoing ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_HIGH)
                    .setOnlyAlertOnce(true);
            Intent openAppIntent = new Intent(context, MainActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
            PendingIntent pi = PendingIntent.getActivity(context, NOTIF_PROGRESS_ID, openAppIntent, piFlags);
            b.setContentIntent(pi);
            if (ongoing) {
                b.setProgress(0, 0, true);
            }
            NotificationManagerCompat.from(context).notify(NOTIF_PROGRESS_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    private static void cancelProgressNotification(Context context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIF_PROGRESS_ID);
        } catch (Exception ignored) {
        }
    }

    /**
     * Handles failure gracefully with specific attribution to FLOW Telkom Akses.
     */
    private static void handlePipelineFailure(Activity activity, String targetWeb, String detailMessage, Runnable cleanup) {
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            if (cleanup != null) {
                cleanup.run();
            }
            cancelProgressNotification(activity);
            updateProgressNotification(activity, "❌ Gagal: " + targetWeb, detailMessage, false);
            Toast.makeText(activity, "❌ Gagal di " + targetWeb + ": " + detailMessage, Toast.LENGTH_LONG).show();
            showAdingStyledErrorDialog(activity, targetWeb, detailMessage);
        });
    }

    /**
     * Shows an ADING-styled Dialog informing the user which website failed and why.
     */
    private static void showAdingStyledErrorDialog(Activity activity, String targetWeb, String detailMessage) {
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout container = new LinearLayout(activity);
                container.setOrientation(LinearLayout.VERTICAL);
                int pad = dp(activity, 20);
                container.setPadding(pad, pad, pad, pad);
                container.setBackgroundColor(Color.WHITE);

                // Header with red accent bar
                LinearLayout headerRow = new LinearLayout(activity);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                View accentPill = new View(activity);
                accentPill.setBackgroundColor(Color.parseColor("#F9010F"));
                LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(dp(activity, 4), dp(activity, 22));
                pillLp.setMargins(0, 0, dp(activity, 10), 0);
                accentPill.setLayoutParams(pillLp);
                headerRow.addView(accentPill);

                TextView tvTitle = new TextView(activity);
                tvTitle.setText("Gagal Terhubung: " + targetWeb);
                tvTitle.setTextSize(16f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setTextColor(Color.parseColor("#111827"));
                headerRow.addView(tvTitle);
                container.addView(headerRow);

                TextView tvMessage = new TextView(activity);
                tvMessage.setText(detailMessage);
                tvMessage.setTextSize(13f);
                tvMessage.setTextColor(Color.parseColor("#4B5563"));
                tvMessage.setPadding(0, dp(activity, 14), 0, dp(activity, 18));
                container.addView(tvMessage);

                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);

                Button btnDismiss = new Button(activity);
                btnDismiss.setText("Tutup");
                btnDismiss.setTextColor(Color.parseColor("#4B5563"));
                btnDismiss.setBackgroundColor(Color.parseColor("#F3F4F6"));
                btnDismiss.setAllCaps(false);
                LinearLayout.LayoutParams dismissLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dismissLp.setMargins(0, 0, dp(activity, 8), 0);
                btnDismiss.setLayoutParams(dismissLp);

                builder.setView(container);
                builder.setCancelable(true);
                AlertDialog dialog = builder.create();

                btnDismiss.setOnClickListener(v -> dialog.dismiss());
                btnRow.addView(btnDismiss);

                if (targetWeb.contains("FLOW")) {
                    Button btnLogin = new Button(activity);
                    btnLogin.setText("🔑 Buka Sesi FLOW");
                    btnLogin.setTextColor(Color.WHITE);
                    btnLogin.setBackgroundColor(Color.parseColor("#F9010F"));
                    btnLogin.setAllCaps(false);
                    btnLogin.setOnClickListener(v -> {
                        dialog.dismiss();
                        openSessionHelperDialog(activity, FLOW_WORKORDER_URL, "🔑 Sesi Login FLOW Telkom Akses");
                    });
                    btnRow.addView(btnLogin);
                }

                container.addView(btnRow);
                dialog.show();
            } catch (Exception ignored) {
            }
        });
    }

    public static void onPageLoaded(WebView webView, String url) {
        if (webView == null || url == null) return;
        restoreCookies(webView.getContext());

        if (url.contains("ading.web.id")) {
            injectAdingDispatchInterceptor(webView);
        }
    }

    private static void injectAdingDispatchInterceptor(WebView webView) {
        String js = "(function(){" +
                "if(window.__adingDispatchHooked) return;" +
                "window.__adingDispatchHooked = true;" +
                "window.__adingOrdersBySc = window.__adingOrdersBySc || {};" +
                "window.__adingTeamsCache = window.__adingTeamsCache || [];" +
                "window.__adingLastCopiedText = '';" +
                "try {" +
                "  if(navigator.clipboard && navigator.clipboard.writeText){" +
                "    var _origWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);" +
                "    navigator.clipboard.writeText = function(txt){" +
                "      if(typeof txt === 'string' && txt.trim().length > 10){ window.__adingLastCopiedText = txt.trim(); }" +
                "      return _origWriteText(txt).catch(function(){});" +
                "    };" +
                "  }" +
                "  var _origExec = document.execCommand ? document.execCommand.bind(document) : null;" +
                "  if(_origExec){" +
                "    document.execCommand = function(cmd){" +
                "      if(String(cmd).toLowerCase() === 'copy'){" +
                "        var sel = window.getSelection ? String(window.getSelection()) : '';" +
                "        var act = document.activeElement;" +
                "        var val = (act && (act.value || act.textContent)) ? String(act.value || act.textContent) : '';" +
                "        var cap = (sel && sel.trim().length > 10) ? sel.trim() : (val.trim().length > 10 ? val.trim() : '');" +
                "        if(cap){ window.__adingLastCopiedText = cap; }" +
                "      }" +
                "      return _origExec.apply(document, arguments);" +
                "    };" +
                "  }" +
                "} catch(ce){}" +
                "function fetchTeams(endpoint){" +
                "  fetch(window.location.origin + endpoint + '?_t=' + Date.now())" +
                "    .then(function(r){ return r.ok ? r.json() : []; })" +
                "    .then(function(arr){ if(Array.isArray(arr) && arr.length > 0) window.__adingTeamsCache = window.__adingTeamsCache.concat(arr); })" +
                "    .catch(function(){});" +
                "}" +
                "fetchTeams('/api/provisioning/team-options');" +
                "fetchTeams('/api/mapping-tim');" +
                "var origFetch = window.fetch;" +
                "window.fetch = function(input, init){" +
                "  var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');" +
                "  var method = (init && init.method) ? init.method.toUpperCase() : 'GET';" +
                "  var reqBody = (init && typeof init.body === 'string') ? init.body : null;" +
                "  var p = origFetch.apply(this, arguments);" +
                "  p.then(function(resp){" +
                "    try {" +
                "      if(url.indexOf('/api/provisioning/team-options') !== -1 || url.indexOf('/api/mapping-tim') !== -1){" +
                "        resp.clone().json().then(function(arr){" +
                "          if(Array.isArray(arr) && arr.length > 0) window.__adingTeamsCache = window.__adingTeamsCache.concat(arr);" +
                "        }).catch(function(){});" +
                "      } else if(url.indexOf('/api/provisioning/undispatch') !== -1 || url.indexOf('/api/provisioning/search-order') !== -1){" +
                "        resp.clone().json().then(function(json){" +
                "          if(json && Array.isArray(json.data)){" +
                "            json.data.forEach(function(row){" +
                "              if(row && row.NO_SC) window.__adingOrdersBySc[row.NO_SC] = row;" +
                "            });" +
                "          }" +
                "        }).catch(function(){});" +
                "      } else if(method === 'POST' && (url.indexOf('/api/provisioning/dispatch-bulk') !== -1 || url.indexOf('/api/provisioning/redispatch') !== -1)){" +
                "        if(resp.ok && reqBody){" +
                "          var bodyObj = JSON.parse(reqBody);" +
                "          var item = (bodyObj.items && bodyObj.items[0]) ? bodyObj.items[0] : bodyObj;" +
                "          var noSc = item.no_sc || bodyObj.no_sc || '';" +
                "          var timId = bodyObj.mapping_tim_id || '';" +
                "          var namaTim = bodyObj.nama_tim || bodyObj.nama_tim_baru || '';" +
                "          var sa = item.service_area || bodyObj.service_area_baru || '';" +
                "          var tgl = bodyObj.tanggal_dispatch || bodyObj.tanggal_dispatch_baru || '';" +
                "          var assignedBy = bodyObj.dispatched_by_name || '';" +
                "          var cachedOrder = window.__adingOrdersBySc[noSc] || {};" +
                "          var cachedTeam = null;" +
                "          for(var i=0; i<window.__adingTeamsCache.length; i++){" +
                "            var t = window.__adingTeamsCache[i];" +
                "            if(String(t.id) === String(timId) || t.nama_tim === namaTim){ cachedTeam = t; break; }" +
                "          }" +
                "          var cleanLeader = cachedTeam && cachedTeam.leader_name ? String(cachedTeam.leader_name) : (namaTim ? namaTim.split('(')[0].trim() : '');" +
                "          var cleanNik = cachedTeam && cachedTeam.leader_nik ? String(cachedTeam.leader_nik) : (cachedTeam && cachedTeam.chief_code ? String(cachedTeam.chief_code) : '');" +
                "          var resolvedWonum = item.wonum || cachedOrder.WONUM || '';" +
                "          if((!resolvedWonum || resolvedWonum === '-') && noSc){" +
                "            var domNodes = document.querySelectorAll('tr, .card, div');" +
                "            for(var di=0; di<domNodes.length; di++){" +
                "              var dTxt = domNodes[di].innerText || '';" +
                "              if(dTxt.indexOf(noSc) !== -1 && dTxt.indexOf('WO') !== -1 && dTxt.length < 1500){" +
                "                var wm = dTxt.match(/\\b(WO[0-9]{6,})\\b/i);" +
                "                if(wm){ resolvedWonum = wm[1].toUpperCase(); break; }" +
                "              }" +
                "            }" +
                "          }" +
                "          window.__adingLastCopiedText = '';" +
                "          try {" +
                "            var salinBtn = null;" +
                "            var cards = document.querySelectorAll('tr, .card, div');" +
                "            for(var ci=0; ci<cards.length; ci++){" +
                "              var cTxt = cards[ci].innerText || '';" +
                "              if(noSc && cTxt.indexOf(noSc) !== -1 && cTxt.length < 2500){" +
                "                var btns = cards[ci].querySelectorAll('button, a, [role=\"button\"]');" +
                "                for(var bi=0; bi<btns.length; bi++){" +
                "                  var bLabel = (btns[bi].innerText || btns[bi].textContent || '').trim().toLowerCase();" +
                "                  if(bLabel === 'salin' || bLabel.indexOf('salin') !== -1){ salinBtn = btns[bi]; break; }" +
                "                }" +
                "                if(salinBtn) break;" +
                "              }" +
                "            }" +
                "            if(salinBtn){ salinBtn.click(); }" +
                "          } catch(se){}" +
                "          var payload = {" +
                "            no_sc: noSc," +
                "            wonum: resolvedWonum," +
                "            sto: item.sto || cachedOrder.STO || ''," +
                "            service_area: sa || (cachedTeam ? cachedTeam.service_area : '')," +
                "            nama_tim: namaTim," +
                "            leader_nik: cleanNik," +
                "            leader_name: cleanLeader," +
                "            paket: cachedOrder.PAKET || ''," +
                "            odp: cachedOrder.ODP || ''," +
                "            koordinat: cachedOrder.KORDINAT || ''," +
                "            booking_date: cachedOrder.BOOKING_DATE || (tgl ? tgl + ' 08:00:00' : '')," +
                "            assigned_by: assignedBy," +
                "            copied_text: ''" +
                "          };" +
                "          setTimeout(function(){" +
                "            payload.copied_text = window.__adingLastCopiedText || '';" +
                "            if(noSc && window.AdingAndroid && window.AdingAndroid.onDispatchOrderTriggered){" +
                "              window.AdingAndroid.onDispatchOrderTriggered(JSON.stringify(payload));" +
                "            }" +
                "          }, 120);" +
                "        }" +
                "      }" +
                "    } catch(e){}" +
                "  }).catch(function(){});" +
                "  return p;" +
                "};" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public static void onAdingDispatchTriggered(Activity activity, String dispatchJson) {
        if (activity == null || activity.isFinishing()) return;
        try {
            JSONObject obj = new JSONObject(dispatchJson);
            String noSc = obj.optString("no_sc", "").trim();
            if (noSc.isEmpty()) return;

            String wonum = obj.optString("wonum", "").trim();
            if ("-".equals(wonum)) wonum = "";
            String namaTim = obj.optString("nama_tim", "").trim();
            String leaderNik = obj.optString("leader_nik", "").trim();
            String leaderName = obj.optString("leader_name", "").trim();
            String serviceArea = obj.optString("service_area", "").trim();
            String paket = obj.optString("paket", "").trim();
            String odp = obj.optString("odp", "").trim();
            String koordinat = obj.optString("koordinat", "").trim();
            String bookingDate = obj.optString("booking_date", "").trim();
            String assignedBy = obj.optString("assigned_by", "").trim();
            String copiedText = obj.optString("copied_text", "").trim();

            if (wonum.isEmpty() && !copiedText.isEmpty()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bWO\\d{6,}\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(copiedText);
                if (m.find()) wonum = m.group().toUpperCase(Locale.ROOT);
            }

            if (!wonum.isEmpty()) {
                dispatchViaRailwayStandalone(activity, noSc, wonum, leaderName, leaderNik, namaTim, copiedText);
                return;
            }

            runFullDispatchToFlowPipeline(
                    activity,
                    noSc,
                    wonum,
                    serviceArea,
                    namaTim,
                    leaderNik,
                    leaderName,
                    paket,
                    odp,
                    koordinat,
                    bookingDate,
                    assignedBy,
                    copiedText
            );
        } catch (Exception ignored) {
        }
    }

    private static void dispatchViaRailwayStandalone(
            Activity activity,
            String noSc,
            String wonum,
            String leaderName,
            String leaderNik,
            String namaTim,
            String copiedText
    ) {
        String techDisplay = !leaderName.isEmpty()
                ? leaderName + (!leaderNik.isEmpty() ? " (" + leaderNik + ")" : "")
                : (!namaTim.isEmpty() ? namaTim : "Teknisi");
        String fallbackChatId = getPrefs(activity).getString(KEY_TG_CHAT_ID, DEFAULT_TG_CHAT_ID);

        mainHandler.post(() -> {
            Toast.makeText(activity, "⚡ Mengirim " + wonum + " via WFM Standalone Railway...", Toast.LENGTH_SHORT).show();
            updateProgressNotification(
                    activity,
                    "⚡ Dispatch Standalone: " + wonum,
                    "Server Railway sedang mengambil SS HD FLOW & mengirim ke channel " + techDisplay + "...",
                    true
            );
        });

        tgExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("wonum", wonum);
                payload.put("no_sc", noSc);
                payload.put("leader_name", leaderName);
                payload.put("leader_nik", leaderNik);
                payload.put("nama_tim", namaTim);
                payload.put("caption", copiedText);
                payload.put("fallback_chat_id", fallbackChatId != null ? fallbackChatId.trim() : DEFAULT_TG_CHAT_ID);

                URL url = new URL(RAILWAY_WFM_STANDALONE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(210000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("X-Ading-Key", RAILWAY_API_KEY);

                byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                    os.flush();
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                }
                JSONObject res = new JSONObject(sb.toString());
                if (code >= 200 && code < 300 && res.optBoolean("ok", false)) {
                    boolean isFallback = res.optBoolean("fallback", false);
                    JSONArray titlesArr = res.optJSONArray("matched_titles");
                    StringBuilder titlesSb = new StringBuilder();
                    if (titlesArr != null) {
                        for (int i = 0; i < titlesArr.length(); i++) {
                            if (i > 0) titlesSb.append(", ");
                            titlesSb.append(titlesArr.optString(i, ""));
                        }
                    }
                    String targetLabel = titlesSb.length() > 0 ? titlesSb.toString() : techDisplay;
                    mainHandler.post(() -> {
                        if (isFallback) {
                            updateProgressNotification(
                                    activity,
                                    "⚠️ Dialihkan ke Chat Pribadi — " + wonum,
                                    "Channel " + techDisplay + " belum ditemukan. SS HD dikirim ke chat pribadi Anda.",
                                    false
                            );
                            Toast.makeText(activity, "⚠️ Channel belum ditemukan, SS " + wonum + " dikirim ke chat pribadi.", Toast.LENGTH_LONG).show();
                        } else {
                            updateProgressNotification(
                                    activity,
                                    "✅ Terkirim ke Telegram — " + targetLabel,
                                    "SS HD " + wonum + " + Data Unmasked berhasil dikirim via WFM Standalone Railway.",
                                    false
                            );
                            Toast.makeText(activity, "✅ SS HD " + wonum + " terkirim ke " + targetLabel + "!", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    String errMsg = res.optString("error", "HTTP " + code);
                    mainHandler.post(() -> {
                        updateProgressNotification(activity, "❌ Gagal WFM Standalone — " + wonum, errMsg, false);
                        Toast.makeText(activity, "❌ Gagal " + wonum + ": " + errMsg, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> {
                    updateProgressNotification(activity, "❌ Error Koneksi Railway — " + wonum, msg, false);
                    Toast.makeText(activity, "❌ Error Railway: " + msg, Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * HIGH-SPEED BACKGROUND PIPELINE:
     * - Restores persistent cookies so session is active immediately.
     * - Fast-loop polls every 350ms, auto-submits login, fast-overrides SweetAlert timers.
     * - Instantly shows native OTP dialog on screen when OTP is ready.
     * - Once OTP is entered, immediately continues to /EditWorkorder/<WONUM>, crops screenshot,
     *   saves to Gallery/Clipboard, and auto-sends SS + Salin text to Telegram Bot!
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void runFullDispatchToFlowPipeline(
            Activity activity,
            String noSc,
            String initialWonum,
            String serviceArea,
            String namaTim,
            String leaderNik,
            String leaderName,
            String paket,
            String odp,
            String koordinat,
            String bookingDate,
            String assignedBy
    ) {
        runFullDispatchToFlowPipeline(
                activity, noSc, initialWonum, serviceArea, namaTim, leaderNik,
                leaderName, paket, odp, koordinat, bookingDate, assignedBy, ""
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void runFullDispatchToFlowPipeline(
            Activity activity,
            String noSc,
            String initialWonum,
            String serviceArea,
            String namaTim,
            String leaderNik,
            String leaderName,
            String paket,
            String odp,
            String koordinat,
            String bookingDate,
            String assignedBy,
            String copiedText
    ) {
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            restoreCookies(activity);

            SharedPreferences prefs = getPrefs(activity);
            String flowUser = prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER);
            String flowPass = prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS);

            String techDisplay = !leaderName.isEmpty()
                    ? (leaderName + (!leaderNik.isEmpty() ? " (" + leaderNik + ")" : ""))
                    : (!namaTim.isEmpty() ? namaTim : "Teknisi");

            String displayTarget = (initialWonum != null && !initialWonum.trim().isEmpty()) ? initialWonum.trim() : noSc;
            Toast.makeText(activity, "⚡ Memproses di background: FLOW WFM (" + displayTarget + ")", Toast.LENGTH_SHORT).show();
            updateProgressNotification(
                    activity,
                    "⚡ Dispatch Order: " + displayTarget,
                    "Membuka FLOW Telkom Akses → " + techDisplay,
                    true
            );

            ViewGroup root = activity.findViewById(R.id.rootLayout);
            if (root == null) {
                root = (ViewGroup) activity.findViewById(android.R.id.content);
            }
            if (root == null) return;

            final long runId = ++sActiveRunId;
            int bgWidth = 1366;
            int bgHeight = 950;
            FrameLayout bgContainer;
            WebView bgWebView;
            View oldBg = root.findViewWithTag("TAG_WFM_BG_CONTAINER");
            if (oldBg instanceof FrameLayout
                    && ((FrameLayout) oldBg).getChildCount() > 0
                    && ((FrameLayout) oldBg).getChildAt(0) instanceof WebView) {
                // Reuse persistent FLOW WebView so sessionStorage, cookies, and OTP session stay alive!
                bgContainer = (FrameLayout) oldBg;
                bgWebView = (WebView) bgContainer.getChildAt(0);
            } else {
                if (oldBg != null) {
                    root.removeView(oldBg);
                }
                bgContainer = new FrameLayout(activity);
                bgContainer.setTag("TAG_WFM_BG_CONTAINER");
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(bgWidth, bgHeight);
                bgContainer.setLayoutParams(lp);
                bgContainer.setFocusable(false);
                bgContainer.setFocusableInTouchMode(false);
                bgContainer.setClickable(false);

                bgWebView = new WebView(activity);
                bgWebView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                bgWebView.setFocusable(false);
                bgWebView.setFocusableInTouchMode(false);
                bgWebView.setClickable(false);

                WebSettings settings = bgWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setUseWideViewPort(true);
                settings.setLoadWithOverviewMode(true);
                settings.setSupportZoom(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);
                settings.setTextZoom(100);
                settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

                float density = activity.getResources().getDisplayMetrics().density;
                int desktopScale = Math.max(20, Math.round(100f / Math.max(1f, density)));
                bgWebView.setInitialScale(desktopScale);

                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(bgWebView, true);

                bgContainer.addView(bgWebView);
                root.addView(bgContainer, 0); // At index 0 behind everything
            }

            bgContainer.measure(
                    View.MeasureSpec.makeMeasureSpec(bgWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(bgHeight, View.MeasureSpec.EXACTLY)
            );
            bgContainer.layout(0, 0, bgWidth, bgHeight);
            bgWebView.measure(
                    View.MeasureSpec.makeMeasureSpec(bgWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(bgHeight, View.MeasureSpec.EXACTLY)
            );
            bgWebView.layout(0, 0, bgWidth, bgHeight);

            final boolean[] flowSsCaptured = {false};
            final String[] resolvedWonum = {initialWonum != null ? initialWonum : ""};
            final boolean[] isFinished = {false};
            final boolean[] isWaitingForOtp = {false};

            // Keep FLOW WebView alive in background after dispatch so OTP session is never destroyed!
            Runnable cleanup = () -> {
                if (isFinished[0]) return;
                isFinished[0] = true;
                mainHandler.post(() -> persistCookies(activity));
            };

            Runnable onFlowWorkorderReady = () -> {
                if (flowSsCaptured[0]) return;
                flowSsCaptured[0] = true;

                updateProgressNotification(
                        activity,
                        "⚡ Dispatch Order: " + noSc,
                        "📸 Mengambil SS Workorder dari FLOW...",
                        true
                );

                extractFlowCardAndCropBitmap(bgWebView, (croppedBitmap, extractedJson) -> {
                    if (croppedBitmap != null) {
                        saveBitmapToGalleryAndClipboard(activity, croppedBitmap, noSc);
                    }

                    String extCustName = "";
                    String extServiceNo = "";
                    String extContactNo = "";
                    String extAddress = "";
                    String extWo = "";

                    if (extractedJson != null) {
                        extWo = extractedJson.optString("wo", "").trim();
                        if (!extWo.isEmpty()) {
                            resolvedWonum[0] = extWo;
                        }
                        extCustName = extractedJson.optString("customer_name", "").trim();
                        extServiceNo = extractedJson.optString("service_number", "").trim();
                        extContactNo = extractedJson.optString("contact_number", "").trim();
                        extAddress = extractedJson.optString("service_address", "").trim();
                    }

                    persistCookies(activity);

                    String woLabel = !resolvedWonum[0].isEmpty() ? resolvedWonum[0] : "-";
                    String custLabel = !extCustName.isEmpty() ? extCustName : "-";
                    String srvLabel = !extServiceNo.isEmpty() ? extServiceNo : "-";
                    String odpLabel = !odp.isEmpty() ? odp : "-";
                    String paketLabel = !paket.isEmpty() ? paket : "-";

                    // Auto-send cropped Workorder SS + ADING "Salin" text to matched Technician Channel (+ Personal ID) via Telegram Bot
                    String tgToken = prefs.getString(KEY_TG_TOKEN, DEFAULT_TG_TOKEN).trim();
                    String fallbackChatId = prefs.getString(KEY_TG_CHAT_ID, DEFAULT_TG_CHAT_ID).trim();
                    String tgThreadId = prefs.getString(KEY_TG_THREAD_ID, "").trim();
                    List<String> targetChatIds = resolveTechnicianTargetChatIds(activity, leaderName, namaTim, leaderNik, fallbackChatId);
                    if (croppedBitmap != null && !tgToken.isEmpty() && !targetChatIds.isEmpty()) {
                        String finalCaption = (copiedText != null && !copiedText.trim().isEmpty())
                                ? unmaskCaptionWithFlowData(
                                        copiedText.trim(),
                                        extCustName,
                                        extAddress,
                                        extContactNo,
                                        extServiceNo
                                )
                                : buildFallbackTelegramCaption(
                                        noSc,
                                        resolvedWonum[0],
                                        bookingDate,
                                        serviceArea,
                                        paket,
                                        extServiceNo,
                                        extCustName,
                                        extContactNo,
                                        extAddress,
                                        odp,
                                        koordinat
                                );
                        Bitmap photoCopy = croppedBitmap.copy(croppedBitmap.getConfig() != null ? croppedBitmap.getConfig() : Bitmap.Config.ARGB_8888, false);
                        List<String> numericTargets = new ArrayList<>();
                        StringBuilder mentionTags = new StringBuilder();
                        for (String entry : targetChatIds) {
                            if (entry.startsWith("@")) {
                                if (mentionTags.length() > 0) mentionTags.append(" ");
                                mentionTags.append(entry);
                            } else {
                                numericTargets.add(entry);
                            }
                        }
                        if (numericTargets.isEmpty() && !fallbackChatId.isEmpty()) {
                            numericTargets.add(fallbackChatId);
                        }
                        String captionWithMention = mentionTags.length() > 0
                                ? (finalCaption + "\n\n👷 " + mentionTags.toString())
                                : finalCaption;
                        updateProgressNotification(
                                activity,
                                "📤 Mengirim ke Bot Telegram: " + woLabel,
                                "Teknisi: " + techDisplay + "\nMengunggah SS Workorder & Data Salin...",
                                true
                        );
                        Toast.makeText(activity, "📤 Mengirim SS WFM & Data ke Telegram (" + techDisplay + ")...", Toast.LENGTH_SHORT).show();
                        tgExecutor.execute(() -> {
                            // 1. Try Auto-Discovery Railway Bot Bridge first (auto-matches ANY channel where @biarndalupa_bot is Admin by NIK/Name!)
                            String bridgeTarget = sendPhotoViaRailwayAutoDiscovery(photoCopy, finalCaption, leaderName, leaderNik, namaTim, fallbackChatId);
                            int sentCount = 0;
                            boolean fallbackSent = false;
                            if (bridgeTarget != null) {
                                if ("FALLBACK".equals(bridgeTarget)) {
                                    fallbackSent = true;
                                } else {
                                    sentCount = 1;
                                }
                            } else {
                                // 2. Fallback direct Telegram API if Railway bridge is unreachable
                                for (String cid : numericTargets) {
                                    String useThread = cid.equals(fallbackChatId) ? tgThreadId : "";
                                    if (uploadPhotoToTelegram(tgToken, cid, useThread, photoCopy, captionWithMention)) {
                                        sentCount++;
                                    }
                                }
                                if (sentCount == 0 && !fallbackChatId.isEmpty() && !numericTargets.contains(fallbackChatId)) {
                                    String fbCaption = "⚠️ Gagal kirim ke channel " + techDisplay + " (pastikan @biarndalupa_bot jadi Admin channel).\n\n" + captionWithMention;
                                    fallbackSent = uploadPhotoToTelegram(tgToken, fallbackChatId, tgThreadId, photoCopy, fbCaption);
                                }
                            }
                            if (photoCopy != null && !photoCopy.isRecycled()) {
                                photoCopy.recycle();
                            }
                            final int finalSent = sentCount;
                            final int totalTargets = numericTargets.size();
                            final boolean finalFallbackSent = fallbackSent;
                            mainHandler.post(() -> {
                                String summaryLines = "NO SC: " + noSc
                                        + "\nWONUM: " + woLabel
                                        + "\nCustomer: " + custLabel
                                        + "\nNo Internet: " + srvLabel
                                        + "\nODP: " + odpLabel + " | Paket: " + paketLabel;
                                if (finalSent > 0) {
                                    updateProgressNotification(
                                            activity,
                                            "✅ Terkirim ke Telegram — " + techDisplay,
                                            summaryLines + "\nStatus: SS FLOW & Data Salin terkirim (" + finalSent + "/" + totalTargets + ").",
                                            false
                                    );
                                    Toast.makeText(activity, "✅ SS WFM & Data " + woLabel + " terkirim ke Telegram (" + techDisplay + ")!", Toast.LENGTH_LONG).show();
                                } else if (finalFallbackSent) {
                                    updateProgressNotification(
                                            activity,
                                            "⚠️ Dialihkan ke Chat Pribadi — " + techDisplay,
                                            summaryLines + "\nChannel teknisi gagal (jadikan @biarndalupa_bot Admin channel). SS dikirim ke chat pribadi Anda.",
                                            false
                                    );
                                    Toast.makeText(activity, "⚠️ Channel " + techDisplay + " gagal, SS dialihkan ke chat pribadi Anda!", Toast.LENGTH_LONG).show();
                                } else {
                                    updateProgressNotification(
                                            activity,
                                            "❌ Gagal Kirim Telegram — " + techDisplay,
                                            summaryLines + "\nSS tersimpan di Galeri, tapi gagal kirim ke bot Telegram.",
                                            false
                                    );
                                    Toast.makeText(activity, "⚠️ SS tersimpan di Galeri, tapi gagal kirim ke Telegram (" + techDisplay + ").", Toast.LENGTH_LONG).show();
                                }
                            });
                        });
                    } else {
                        updateProgressNotification(
                                activity,
                                "✅ SS FLOW Disimpan — " + techDisplay,
                                "NO SC: " + noSc + "\nWONUM: " + woLabel + "\nSS Workorder disimpan ke Galeri.",
                                false
                        );
                        Toast.makeText(activity, "✅ Order " + noSc + " berhasil diproses & SS WFM disimpan ke Galeri!", Toast.LENGTH_LONG).show();
                    }
                    mainHandler.postDelayed(cleanup, 1200);
                });
            };

            bgWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    if (request == null || !request.isForMainFrame() || isFinished[0]) return;
                    String failingUrl = request.getUrl() != null ? request.getUrl().toString() : "";
                    if (failingUrl.isEmpty() || failingUrl.equals("about:blank")) return;
                    int errorCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && error != null ? error.getErrorCode() : 0;
                    String description = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && error != null && error.getDescription() != null
                            ? error.getDescription().toString() : "Error";
                    // Ignore ERR_ABORTED (-1) which fires normally when JS redirects during page load
                    if (errorCode == WebViewClient.ERROR_UNKNOWN || description.contains("ERR_ABORTED")) return;
                    String targetWeb = failingUrl.contains("flow.telkomakses") ? "FLOW Telkom Akses" : "Web Server";
                    handlePipelineFailure(activity, targetWeb, "Koneksi ke " + targetWeb + " terputus: " + description + " (Kode " + errorCode + "). Periksa jaringan Anda.", cleanup);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    persistCookies(activity);
                    if (url == null || isFinished[0]) return;

                    if (url.contains("flow.telkomakses.co.id")) {
                        injectDesktopViewport(view);
                        if (url.contains("/unauthorize")) {
                            view.loadUrl(FLOW_LOGIN_URL);
                        }
                    }
                }
            });

            // Start master high-speed state loop (direct to /EditWorkorder/<WONUM> if WONUM available)
            String startFlowUrl = !resolvedWonum[0].isEmpty()
                    ? (FLOW_EDIT_WORKORDER_URL + resolvedWonum[0])
                    : FLOW_WORKORDER_URL;
            bgWebView.evaluateJavascript("window.__adingNavigatedToWo=false;window.__adingLoginSubmitted=false;window.__adingOtpSubmitted=false;", null);
            if (startFlowUrl.equals(bgWebView.getUrl())) {
                bgWebView.reload();
            } else {
                bgWebView.loadUrl(startFlowUrl);
            }
            runFlowPipelineFastLoop(
                    bgWebView,
                    runId,
                    isFinished,
                    noSc,
                    resolvedWonum[0],
                    flowUser,
                    flowPass,
                    flowSsCaptured,
                    isWaitingForOtp,
                    activity,
                    onFlowWorkorderReady,
                    cleanup,
                    0
            );
        });
    }


    /**
     * UNIFIED HIGH-SPEED FLOW AUTOMATION LOOP (POLLS EVERY 350MS):
     * Drives all states automatically:
     * 1. Detects Workorder detail card -> triggers screenshot
     * 2. Detects /workorder table -> filters SC Order and clicks row
     * 3. Detects /otp or OTP input -> instantly shows native OTP dialog to user
     * 4. Detects successful login AJAX response -> immediately bypasses 2s Swal and redirects to /otp
     * 5. Detects login form -> fills credentials, adds Remember Me, and submits form
     * 6. Detects /home or /dashboard -> immediately redirects to /workorder
     */
    private static void runFlowPipelineFastLoop(
            WebView view,
            long runId,
            boolean[] isFinished,
            String noSc,
            String targetWonum,
            String flowUser,
            String flowPass,
            boolean[] flowSsCaptured,
            boolean[] isWaitingForOtp,
            Activity activity,
            Runnable onCardReady,
            Runnable cleanup,
            int attempt
    ) {
        if (runId != sActiveRunId || isFinished[0] || flowSsCaptured[0] || attempt > 110) {
            if (runId == sActiveRunId && attempt > 110 && !flowSsCaptured[0] && !isFinished[0]) {
                String curUrl = "";
                try { curUrl = view != null && view.getUrl() != null ? view.getUrl() : ""; } catch (Exception ignored) {}
                String woInfo = (targetWonum != null && !targetWonum.isEmpty()) ? targetWonum : ("(kosong, SC=" + noSc + ")");
                handlePipelineFailure(
                        activity,
                        "FLOW Telkom Akses",
                        "Waktu habis (Timeout) di FLOW Telkom Akses.\nWONUM: " + woInfo + "\nURL terakhir: " + curUrl,
                        cleanup
                );
            }
            return;
        }

        mainHandler.postDelayed(() -> {
            if (runId != sActiveRunId || isFinished[0] || flowSsCaptured[0]) return;

            // Pause polling while waiting for user to type OTP in the dialog - do NOT increment attempt
            if (isWaitingForOtp[0]) {
                runFlowPipelineFastLoop(view, runId, isFinished, noSc, targetWonum, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt);
                return;
            }

            String safeSc = escapeJs(noSc);
            String safeWonum = escapeJs(targetWonum != null ? targetWonum.trim() : "");
            String safeUser = escapeJs(flowUser);
            String safePass = escapeJs(flowPass);

            String js = "(function(){" +
                    "var path = window.location.pathname || '';" +
                    "var targetSc = '" + safeSc + "';" +
                    "var targetLower = targetSc.toLowerCase();" +
                    "var targetWo = '" + safeWonum + "';" +
                    "var targetWoUpper = targetWo.toUpperCase();" +
                    "var targetFlowDest = targetWo ? ('/EditWorkorder/' + targetWo) : '/workorder';" +

                    // 1. DETAIL CARD READY CHECK
                    "function getDetailVal(lbl){" +
                    "  var nodes = document.querySelectorAll('label, div, span, td');" +
                    "  for(var i=0; i<nodes.length; i++){" +
                    "    var n = nodes[i];" +
                    "    if(n.children.length > 2) continue;" +
                    "    var txt = (n.innerText || '').trim();" +
                    "    if(txt.toLowerCase().indexOf(lbl.toLowerCase()) !== -1){" +
                    "      var sib = n.nextElementSibling;" +
                    "      if(sib){" +
                    "        var inp = sib.matches('input,textarea') ? sib : sib.querySelector('input,textarea,.form-control');" +
                    "        if(inp && (inp.readOnly || inp.disabled || inp.tagName === 'DIV')) return (inp.value || inp.innerText || '').trim();" +
                    "      }" +
                    "      var p = n.parentElement;" +
                    "      if(p && p.nextElementSibling){" +
                    "        var ps = p.nextElementSibling;" +
                    "        var inp2 = ps.matches('input,textarea') ? ps : ps.querySelector('input,textarea,.form-control');" +
                    "        if(inp2 && (inp2.readOnly || inp2.disabled || inp2.tagName === 'DIV')) return (inp2.value || inp2.innerText || '').trim();" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  return '';" +
                    "}" +
                    "var scDetailVal = getDetailVal('SC Order No');" +
                    "var woDetailVal = getDetailVal('Workorder:');" +
                    "if(!scDetailVal || !woDetailVal){" +
                    "  var allInputs = document.querySelectorAll('input, textarea');" +
                    "  for(var ai=0; ai<allInputs.length; ai++){" +
                    "    var aiv = (allInputs[ai].value || '').trim();" +
                    "    var aivLower = aiv.toLowerCase();" +
                    "    if(targetLower && aivLower.indexOf(targetLower) !== -1){ scDetailVal = aiv; }" +
                    "    if(targetWoUpper && aiv.toUpperCase() === targetWoUpper){ woDetailVal = aiv; }" +
                    "    else if(!woDetailVal && (aiv.toUpperCase().indexOf('WO0') !== -1 || (aiv.toUpperCase().indexOf('WO') === 0 && aiv.length >= 8))){ woDetailVal = aiv; }" +
                    "  }" +
                    "}" +
                    "var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';" +
                    "var onEditWoPage = path.toLowerCase().indexOf('/editworkorder/') !== -1;" +
                    "var hasWoCardLabels = bodyTxt.indexOf('OSS Order ID') !== -1 || bodyTxt.indexOf('Service Number') !== -1 || bodyTxt.indexOf('Customer Name') !== -1;" +
                    "var isWoCardMatch = (onEditWoPage && hasWoCardLabels) || (targetWoUpper ? (woDetailVal && woDetailVal.toUpperCase().indexOf(targetWoUpper) !== -1) : (scDetailVal && scDetailVal.toLowerCase().indexOf(targetLower) !== -1 && woDetailVal && woDetailVal.toUpperCase().indexOf('WO') !== -1));" +
                    "if(isWoCardMatch){" +
                    "  document.querySelectorAll('.modal.show, .modal-backdrop').forEach(function(m){ m.style.display='none'; });" +
                    "  return 'DETAIL_CARD_READY:' + (woDetailVal || targetWo);" +
                    "}" +

                    // 1B. DIRECT NAVIGATE BY WONUM VIA /EditWorkorder/<WONUM> WHEN LOGGED IN
                    "var searchWonumInp = document.getElementById('searchWonum');" +
                    "if(targetWo && (searchWonumInp || path.indexOf('/home') !== -1 || path.indexOf('/dashboard') !== -1 || path.indexOf('/workorder') !== -1)){" +
                    "  if(path.toLowerCase().indexOf('/editworkorder/' + targetWo.toLowerCase()) === -1 && !window.__adingNavigatedToWo){" +
                    "    window.__adingNavigatedToWo = true;" +
                    "    window.location.href = '/EditWorkorder/' + targetWo;" +
                    "    return 'NAVIGATING_TO_WONUM';" +
                    "  }" +
                    "}" +

                    // 2. ON /workorder TABLE: FILTER & CLICK ROW (Fallback when WONUM empty)
                    "if(!targetWo && path.indexOf('/workorder') !== -1){" +
                    "  document.querySelectorAll('.modal-backdrop').forEach(function(b){ if(!document.querySelector('.modal.show')) b.remove(); });" +
                    "  if(window.__adingRowClickTime && (Date.now() - window.__adingRowClickTime) > 3500){" +
                    "    var oldClickedRows = document.querySelectorAll('[data-ading-clicked]');" +
                    "    for(var ocr=0; ocr<oldClickedRows.length; ocr++){ delete oldClickedRows[ocr].dataset.adingClicked; }" +
                    "    delete window.__adingRowClickTime;" +
                    "  }" +
                    "  if(window.__adingFlowFilterApplied === targetSc){" +
                    "    var rows = document.querySelectorAll('table tbody tr');" +
                    "    for(var r=0; r<rows.length; r++){" +
                    "      var rTxt = (rows[r].innerText || '');" +
                    "      if(rTxt.toLowerCase().indexOf(targetLower) !== -1 || (rows.length === 1 && rTxt.toUpperCase().indexOf('WO0') !== -1)){" +
                    "        if(!rows[r].dataset.adingClicked){" +
                    "          rows[r].dataset.adingClicked = '1';" +
                    "          window.__adingRowClickTime = Date.now();" +
                    "          var clickTarget = rows[r].querySelector('a, button');" +
                    "          if(!clickTarget){" +
                    "            var tds = rows[r].querySelectorAll('td');" +
                    "            for(var c=0; c<tds.length; c++){" +
                    "              if((tds[c].innerText || '').trim().toUpperCase().indexOf('WO') === 0){ clickTarget = tds[c]; break; }" +
                    "            }" +
                    "          }" +
                    "          if(clickTarget) clickTarget.click();" +
                    "          rows[r].click();" +
                    "          rows[r].dispatchEvent(new MouseEvent('dblclick', {bubbles:true}));" +
                    "          return 'ROW_CLICKED';" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  var scInput = document.querySelector('input[placeholder*=\"Track\" i], input[name*=\"track\" i], input[id*=\"track\" i], input[placeholder*=\"SC Order\" i], input[name*=\"sc_order\" i], input[id*=\"sc_order\" i]');" +
                    "  if(!scInput){" +
                    "    var labels = document.querySelectorAll('label, div, span');" +
                    "    for(var k=0; k<labels.length; k++){" +
                    "      var lTxt = (labels[k].innerText || '').trim().toLowerCase();" +
                    "      if(lTxt === 'track id' || lTxt === 'track id:' || lTxt === 'sc order no:' || lTxt === 'sc order no'){" +
                    "        var container = labels[k].parentElement;" +
                    "        scInput = container ? container.querySelector('input:not([readonly]):not([disabled])') : null;" +
                    "        if(scInput) break;" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  var modalEl = scInput ? (scInput.closest('.modal') || document) : null;" +
                    "  if(!scInput || (modalEl && modalEl.classList && modalEl.classList.contains('modal') && !modalEl.classList.contains('show'))){" +
                    "    var filterBtns = document.querySelectorAll('[data-bs-toggle=\"modal\"], [data-toggle=\"modal\"], button, a');" +
                    "    for(var b=0; b<filterBtns.length; b++){" +
                    "      var bTxt = (filterBtns[b].innerText || filterBtns[b].title || '').toLowerCase();" +
                    "      if(bTxt.indexOf('filter') !== -1 || bTxt.indexOf('search') !== -1 || filterBtns[b].querySelector('.bi-funnel, .fa-filter, .bi-search')){" +
                    "        if(!filterBtns[b].dataset.adingFilterOpened){" +
                    "          filterBtns[b].dataset.adingFilterOpened = '1';" +
                    "          filterBtns[b].click();" +
                    "          return 'OPENED_FILTER_MODAL';" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  if(scInput){" +
                    "    var scope = modalEl || document;" +
                    "    var btns = scope.querySelectorAll('button, a, input[type=\"button\"], input[type=\"reset\"]');" +
                    "    for(var m=0; m<btns.length; m++){" +
                    "      if((btns[m].innerText || btns[m].value || '').trim().toLowerCase() === 'clear all' && !btns[m].dataset.adingCleared){" +
                    "        btns[m].dataset.adingCleared = '1';" +
                    "        btns[m].click();" +
                    "      }" +
                    "    }" +
                    "    scInput.focus();" +
                    "    scInput.value = targetSc;" +
                    "    scInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                    "    scInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "    scInput.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', keyCode:13, bubbles:true}));" +
                    "    for(var a=0; a<btns.length; a++){" +
                    "      var aTxt = (btns[a].innerText || btns[a].value || '').trim().toLowerCase();" +
                    "      if(aTxt === 'apply' || aTxt === 'apply filters'){" +
                    "        window.__adingFlowFilterApplied = targetSc;" +
                    "        btns[a].click();" +
                    "        return 'APPLIED_SC_FILTER';" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  return 'ON_WORKORDER_PAGE';" +
                    "}" +

                    // 3. CHECK IF ON /otp PAGE OR #otp INPUT PRESENT
                    "var otpInput = document.getElementById('otp') || document.querySelector('input[name=\"otp\"]');" +
                    "if(path.indexOf('/otp') !== -1 || otpInput){" +
                    "  if(window.__adingOtpSubmitted){" +
                    "    if(localStorage.getItem('menus')){" +
                    "      window.location.href = targetFlowDest;" +
                    "      return 'REDIRECT_TO_WORKORDER';" +
                    "    }" +
                    "    var otpSwal = (document.querySelector('.swal2-html-container, .swal2-content') || {}).innerText || '';" +
                    "    if(otpSwal && (otpSwal.toLowerCase().indexOf('salah') !== -1 || otpSwal.toLowerCase().indexOf('invalid') !== -1 || otpSwal.toLowerCase().indexOf('expired') !== -1 || otpSwal.toLowerCase().indexOf('gagal') !== -1)){" +
                    "      window.__adingOtpSubmitted = false;" +
                    "      return 'READY_FOR_OTP';" +
                    "    }" +
                    "    return 'OTP_VERIFYING';" +
                    "  }" +
                    "  return 'READY_FOR_OTP';" +
                    "}" +

                    // 4. CHECK IF LOGIN WAS JUST SUBMITTED & USERNAME SET -> JUMP TO /otp
                    "if(window.__adingLoginSubmitted && (path === '/' || path === '/login') && localStorage.getItem('username') && !localStorage.getItem('menus')){" +
                    "  window.location.href = '/otp';" +
                    "  return 'NAVIGATING_TO_OTP';" +
                    "}" +

                    // 5. CHECK IF ON LOGIN FORM: AUTO FILL & SUBMIT
                    "var swalText = (document.querySelector('.swal2-html-container, .swal2-content') || {}).innerText || '';" +
                    "if(swalText.toLowerCase().indexOf('password salah') !== -1){" +
                    "  return 'LOGIN_WRONG_PASSWORD';" +
                    "}" +
                    "var userInput = document.getElementById('inputEmail4') || document.querySelector('input[name=\"username\"]');" +
                    "var passInput = document.getElementById('inputPassword4') || document.querySelector('input[name=\"password\"]');" +
                    "if(userInput && passInput){" +
                    "  if(!window.__adingLoginSubmitted){" +
                    "    window.__adingLoginSubmitted = true;" +
                    "    try{ localStorage.removeItem('menus'); localStorage.removeItem('username'); }catch(e){}" +
                    "    userInput.value = '" + safeUser + "';" +
                    "    userInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                    "    userInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "    passInput.value = '" + safePass + "';" +
                    "    passInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                    "    passInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "    var rem = document.getElementById('gridCheck');" +
                    "    if(rem){" +
                    "      rem.setAttribute('name', 'remember');" +
                    "      rem.name = 'remember';" +
                    "      rem.value = '1';" +
                    "      rem.checked = true;" +
                    "    }" +
                    "    if(window.Swal){" +
                    "      var origFire = window.Swal.fire;" +
                    "      window.Swal.fire = function(arg){" +
                    "        var isErr = arg && (arg.icon === 'error' || String(arg.text || arg.title || '').toLowerCase().indexOf('salah') !== -1);" +
                    "        if(!isErr){ setTimeout(function(){ window.location.href = '/otp'; }, 50); }" +
                    "        return origFire.apply(this, arguments);" +
                    "      };" +
                    "    }" +
                    "    var submitBtn = document.querySelector('#loginForm button[type=\"submit\"], button.btn-danger[type=\"submit\"]');" +
                    "    if(submitBtn){ submitBtn.click(); }" +
                    "    else {" +
                    "      var lForm = document.getElementById('loginForm');" +
                    "      if(lForm) lForm.dispatchEvent(new Event('submit', {bubbles:true, cancelable:true}));" +
                    "    }" +
                    "    return 'SUBMITTED_LOGIN';" +
                    "  }" +
                    "  return 'LOGIN_WAITING_RESPONSE';" +
                    "}" +

                    // 6. IF ON /home OR /dashboard, DIRECTLY OPEN TARGET
                    "if(path.indexOf('/home') !== -1 || path.indexOf('/dashboard') !== -1){" +
                    "  window.location.href = targetFlowDest;" +
                    "  return 'NAVIGATING_TO_WORKORDER';" +
                    "}" +

                    // 7. IF ON /unauthorize, CLEAR STALE STORAGE AND RETURN TO /
                    "if(path.indexOf('/unauthorize') !== -1){" +
                    "  try{ localStorage.removeItem('menus'); localStorage.removeItem('username'); }catch(e){}" +
                    "  window.location.href = '/';" +
                    "  return 'NAVIGATING_TO_ROOT';" +
                    "}" +

                    "return 'WAITING';" +
                    "})();";

            final boolean[] callbackFired = {false};
            Runnable evalWatchdog = () -> {
                if (!callbackFired[0] && runId == sActiveRunId && !isFinished[0] && !flowSsCaptured[0]) {
                    callbackFired[0] = true;
                    runFlowPipelineFastLoop(view, runId, isFinished, noSc, targetWonum, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt + 1);
                }
            };
            mainHandler.postDelayed(evalWatchdog, 2500);

            view.evaluateJavascript(js, result -> {
                if (callbackFired[0] || runId != sActiveRunId) return;
                callbackFired[0] = true;
                mainHandler.removeCallbacks(evalWatchdog);
                String res = result != null ? result.replace("\"", "") : "";
                String labelKey = (targetWonum != null && !targetWonum.isEmpty()) ? targetWonum : noSc;
                if (res.startsWith("LOGIN_WRONG_PASSWORD")) {
                    handlePipelineFailure(
                            activity,
                            "FLOW Telkom Akses",
                            "Password FLOW salah untuk akun " + flowUser + ". Perbarui password di menu Pengaturan Akun WFM FLOW agar akun tidak terkunci.",
                            cleanup
                    );
                    return;
                } else if (res.startsWith("NAVIGATING_TO_ROOT")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔐 Sesi FLOW belum aktif, membuka halaman login...", true);
                } else if (res.startsWith("SUBMITTED_LOGIN")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔐 [1/2] Mengirim login FLOW (" + flowUser + ")...", true);
                } else if (res.startsWith("NAVIGATING_TO_OTP")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "📲 Menuju halaman verifikasi OTP FLOW...", true);
                } else if (res.startsWith("READY_FOR_OTP")) {
                    isWaitingForOtp[0] = true;
                    updateProgressNotification(activity, "📲 Masukkan OTP FLOW", "Buka dialog untuk verifikasi OTP", true);
                    showOtpInputDialog(activity, view, noSc, targetWonum, isWaitingForOtp, cleanup);
                } else if (res.startsWith("NAVIGATING_TO_WONUM") || res.startsWith("REDIRECT_TO_WORKORDER")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔍 [1/2] Membuka langsung Workorder " + labelKey + "...", true);
                } else if (res.startsWith("OPENED_FILTER_MODAL")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔍 [1/2] Membuka Filter Search Workorder Fulfillment...", true);
                } else if (res.startsWith("APPLIED_SC_FILTER")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔍 [1/2] Filter SC Order no di-Apply! Menunggu tabel...", true);
                } else if (res.startsWith("ROW_CLICKED")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "📄 [1/2] Baris ditemukan! Membuka detail kartu Workorder...", true);
                } else if (res.startsWith("DETAIL_CARD_READY")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "✅ [1/2] Kartu Workorder tampil! Mengambil Screenshot...", true);
                    persistCookies(activity);
                    mainHandler.postDelayed(onCardReady, 600);
                    return;
                }
                runFlowPipelineFastLoop(view, runId, isFinished, noSc, targetWonum, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt + 1);
            });
        }, 350);
    }

    /**
     * Shows a clean 6-digit OTP input dialog with ADING's visual design.
     * When entered, it submits the OTP directly into the background WebView waiting on /otp.
     */
    private static void showOtpInputDialog(Activity activity, WebView bgWebView, String noSc, String targetWonum, boolean[] isWaitingForOtp, Runnable onCancel) {
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = dp(activity, 20);
                root.setPadding(pad, pad, pad, pad);

                // ADING White Rounded Card (#FFFFFF, 16dp radius, 1dp #E5E7EB border)
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(Color.WHITE);
                cardBg.setCornerRadius(dp(activity, 16));
                cardBg.setStroke(dp(activity, 1), Color.parseColor("#E5E7EB"));
                root.setBackground(cardBg);

                // ADING Pill Badge (Soft Indigo #EEF2FF with Navy #1E3A8A text)
                TextView tvBadge = new TextView(activity);
                tvBadge.setText("ADING • WFM FLOW");
                tvBadge.setTextSize(10.5f);
                tvBadge.setTypeface(null, Typeface.BOLD);
                tvBadge.setTextColor(Color.parseColor("#1E3A8A"));
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(Color.parseColor("#EEF2FF"));
                badgeBg.setCornerRadius(dp(activity, 20));
                tvBadge.setBackground(badgeBg);
                tvBadge.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeLp.setMargins(0, 0, 0, dp(activity, 10));
                tvBadge.setLayoutParams(badgeLp);
                root.addView(tvBadge);

                TextView tvTitle = new TextView(activity);
                tvTitle.setText("Verifikasi OTP FLOW");
                tvTitle.setTextSize(17f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setTextColor(Color.parseColor("#111827"));
                root.addView(tvTitle);

                TextView tvMsg = new TextView(activity);
                tvMsg.setText("Masukkan 6 digit kode OTP yang dikirimkan ke Telegram Anda untuk melanjutkan pengambilan SS Workorder.");
                tvMsg.setTextSize(13f);
                tvMsg.setTextColor(Color.parseColor("#6B7280"));
                tvMsg.setLineSpacing(dp(activity, 2), 1f);
                tvMsg.setPadding(0, dp(activity, 6), 0, dp(activity, 16));
                root.addView(tvMsg);

                // ADING Input Label
                TextView tvOtpLabel = new TextView(activity);
                tvOtpLabel.setText("KODE OTP");
                tvOtpLabel.setTextSize(11f);
                tvOtpLabel.setTypeface(null, Typeface.BOLD);
                tvOtpLabel.setTextColor(Color.parseColor("#6B7280"));
                tvOtpLabel.setPadding(0, 0, 0, dp(activity, 6));
                root.addView(tvOtpLabel);

                // ADING Rounded Input Field (#F9FAFB fill, 12dp radius, 1dp #D1D5DB border)
                final EditText etOtp = new EditText(activity);
                etOtp.setInputType(InputType.TYPE_CLASS_NUMBER);
                etOtp.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
                etOtp.setHint("• • • • • •");
                etOtp.setHintTextColor(Color.parseColor("#9CA3AF"));
                etOtp.setTextSize(24f);
                etOtp.setTypeface(null, Typeface.BOLD);
                etOtp.setTextColor(Color.parseColor("#111827"));
                etOtp.setLetterSpacing(0.25f);
                etOtp.setGravity(Gravity.CENTER);
                GradientDrawable inputBg = new GradientDrawable();
                inputBg.setColor(Color.parseColor("#F9FAFB"));
                inputBg.setCornerRadius(dp(activity, 12));
                inputBg.setStroke(dp(activity, 1), Color.parseColor("#D1D5DB"));
                etOtp.setBackground(inputBg);
                etOtp.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
                root.addView(etOtp);

                // Subtle Divider (#F3F4F6)
                View divider = new View(activity);
                divider.setBackgroundColor(Color.parseColor("#F3F4F6"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
                divLp.setMargins(0, dp(activity, 18), 0, dp(activity, 14));
                divider.setLayoutParams(divLp);
                root.addView(divider);

                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);

                // ADING Secondary Outlined Button (matching Redispatch / View / Salin)
                Button btnCancel = new Button(activity);
                btnCancel.setText("Batal");
                btnCancel.setTextSize(13.5f);
                btnCancel.setTypeface(null, Typeface.BOLD);
                btnCancel.setTextColor(Color.parseColor("#374151"));
                btnCancel.setAllCaps(false);
                GradientDrawable cancelBg = new GradientDrawable();
                cancelBg.setColor(Color.WHITE);
                cancelBg.setCornerRadius(dp(activity, 10));
                cancelBg.setStroke(dp(activity, 1), Color.parseColor("#D1D5DB"));
                btnCancel.setBackground(cancelBg);
                btnCancel.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
                btnCancel.setMinHeight(0);
                btnCancel.setMinimumHeight(dp(activity, 42));
                LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cancelLp.setMargins(0, 0, dp(activity, 10), 0);
                btnCancel.setLayoutParams(cancelLp);
                btnRow.addView(btnCancel);

                // ADING Primary Submit Button (Navy Blue #1E3A8A, 10dp radius, matching ADING Submit)
                Button btnSubmit = new Button(activity);
                btnSubmit.setText("Submit");
                btnSubmit.setTextSize(13.5f);
                btnSubmit.setTextColor(Color.WHITE);
                btnSubmit.setAllCaps(false);
                btnSubmit.setTypeface(null, Typeface.BOLD);
                GradientDrawable submitBg = new GradientDrawable();
                submitBg.setColor(Color.parseColor("#1E3A8A"));
                submitBg.setCornerRadius(dp(activity, 10));
                btnSubmit.setBackground(submitBg);
                btnSubmit.setPadding(dp(activity, 24), dp(activity, 10), dp(activity, 24), dp(activity, 10));
                btnSubmit.setMinHeight(0);
                btnSubmit.setMinimumHeight(dp(activity, 42));
                btnRow.addView(btnSubmit);

                root.addView(btnRow);

                builder.setView(root);
                builder.setCancelable(false);

                AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                btnCancel.setOnClickListener(v -> {
                    dialog.dismiss();
                    isWaitingForOtp[0] = false;
                    if (onCancel != null) onCancel.run();
                    cancelProgressNotification(activity);
                    Toast.makeText(activity, "Otomasi FLOW dibatalkan.", Toast.LENGTH_SHORT).show();
                });

                btnSubmit.setOnClickListener(v -> {
                    String otpCode = etOtp.getText().toString().trim();
                    if (otpCode.length() < 4) {
                        Toast.makeText(activity, "Masukkan 6 digit kode OTP yang valid!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    Toast.makeText(activity, "Memverifikasi OTP FLOW...", Toast.LENGTH_SHORT).show();
                    updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "📲 Memverifikasi OTP FLOW Telegram...", true);

                    String safeOtp = escapeJs(otpCode);
                    String safePostOtpUrl = (targetWonum != null && !targetWonum.trim().isEmpty())
                            ? ("/EditWorkorder/" + escapeJs(targetWonum.trim()))
                            : "/workorder";
                    String submitJs = "(function(){" +
                            "var inp = document.getElementById('otp') || document.querySelector('input[name=\"otp\"]');" +
                            "if(inp){" +
                            "  window.__adingOtpSubmitted = true;" +
                            "  inp.focus();" +
                            "  inp.value = '" + safeOtp + "';" +
                            "  inp.dispatchEvent(new Event('input', {bubbles:true}));" +
                            "  inp.dispatchEvent(new Event('change', {bubbles:true}));" +
                            "  if(window.Swal){" +
                            "    var origSwal = window.Swal.fire;" +
                            "    window.Swal.fire = function(){" +
                            "      var allTxt = '';" +
                            "      try{ allTxt = Array.prototype.slice.call(arguments).map(function(x){ return typeof x === 'object' ? JSON.stringify(x) : String(x); }).join(' ').toLowerCase(); }catch(e){}" +
                            "      var isErr = allTxt.indexOf('error') !== -1 || allTxt.indexOf('salah') !== -1 || allTxt.indexOf('invalid') !== -1 || allTxt.indexOf('expired') !== -1 || allTxt.indexOf('gagal') !== -1;" +
                            "      var isSuccess = !isErr && (allTxt.indexOf('success') !== -1 || allTxt.indexOf('berhasil') !== -1 || allTxt.indexOf('valid') !== -1);" +
                            "      if(isSuccess){ setTimeout(function(){ window.location.href = '" + safePostOtpUrl + "'; }, 400); }" +
                            "      else if(isErr){ window.__adingOtpSubmitted = false; }" +
                            "      return origSwal.apply(this, arguments);" +
                            "    };" +
                            "  }" +
                            "  var btn = document.getElementById('signInButton') || document.querySelector('button[type=\"submit\"]');" +
                            "  if(btn){ btn.click(); return 'CLICKED_SIGN_IN'; }" +
                            "  var form = document.getElementById('otpForm') || inp.closest('form');" +
                            "  if(form){ form.dispatchEvent(new Event('submit', {bubbles:true, cancelable:true})); return 'SUBMITTED_FORM'; }" +
                            "}" +
                            "return 'OTP_INPUT_NOT_FOUND';" +
                            "})();";
                    bgWebView.evaluateJavascript(submitJs, res -> {
                        // Resume loop so it can catch redirect to /workorder immediately
                        isWaitingForOtp[0] = false;
                    });
                });

                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                    etOtp.requestFocus();
                });

                dialog.show();
                Toast.makeText(activity, "📲 Masukkan 6 digit OTP FLOW dari Telegram!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                isWaitingForOtp[0] = false;
                Toast.makeText(activity, "Gagal membuka dialog OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private interface FlowExtractionCallback {
        void onExtracted(Bitmap croppedBitmap, JSONObject extractedData);
    }

    private static void extractFlowCardAndCropBitmap(WebView webView, FlowExtractionCallback callback) {
        String js = "(function(){" +
                "function getValByLabel(lbl){" +
                "  var nodes = document.querySelectorAll('label, div, span, td, th');" +
                "  for(var i=0; i<nodes.length; i++){" +
                "    var n = nodes[i];" +
                "    if(n.children.length > 1) continue;" +
                "    var t = (n.innerText || n.textContent || '').trim();" +
                "    if(t.toLowerCase().indexOf(lbl.toLowerCase()) !== -1){" +
                "      var sib = n.nextElementSibling;" +
                "      if(sib){" +
                "        var inp = sib.matches('input,textarea,select') ? sib : sib.querySelector('input,textarea,select,.form-control');" +
                "        if(inp) return (inp.value || inp.innerText || '').trim();" +
                "      }" +
                "      var p = n.parentElement;" +
                "      if(p){" +
                "        var pInps = p.querySelectorAll('input,textarea,select,.form-control');" +
                "        if(pInps.length === 1 && pInps[0] !== n) return (pInps[0].value || pInps[0].innerText || '').trim();" +
                "        var psib = p.nextElementSibling;" +
                "        if(psib){" +
                "          var inp3 = psib.matches('input,textarea,select') ? psib : psib.querySelector('input,textarea,select,.form-control');" +
                "          if(inp3) return (inp3.value || inp3.innerText || '').trim();" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "  return '';" +
                "}" +
                "var wo = getValByLabel('Workorder:');" +
                "var sc = getValByLabel('SC Order No');" +
                "var srv = getValByLabel('Service Number');" +
                "var contact = getValByLabel('Contact Number');" +
                "var cust = getValByLabel('Customer Name');" +
                "var addr = getValByLabel('Service Address');" +
                "var navs = document.querySelectorAll('nav, header, .navbar');" +
                "for(var ni=0; ni<navs.length; ni++){ navs[ni].style.display = 'none'; }" +
                "var formEl = document.querySelector('main form') || document.querySelector('form');" +
                "var tabBtnEl = null;" +
                "var redBarEl = null;" +
                "var prodLblEl = null;" +
                "var schedEl = null;" +
                "var scanNodes = document.querySelectorAll('a, button, li, div, label, span, h5, h6');" +
                "for(var j=0; j<scanNodes.length; j++){" +
                "  var sn = scanNodes[j];" +
                "  if(sn.children.length > 2) continue;" +
                "  var st = (sn.innerText || '').trim();" +
                "  if(!tabBtnEl && (st === 'Related Record' || st === 'Plans')){" +
                "    tabBtnEl = sn;" +
                "  }" +
                "  if(!redBarEl && st === 'Workorder'){" +
                "    var rr = sn.getBoundingClientRect();" +
                "    if(rr.width > 400){ redBarEl = sn; }" +
                "  }" +
                "  if(!prodLblEl && st.toLowerCase().indexOf('product name') === 0){" +
                "    prodLblEl = sn;" +
                "  }" +
                "  if(!schedEl && st === 'Scheduling Information'){" +
                "    schedEl = sn;" +
                "  }" +
                "}" +
                "var rectObj = null;" +
                "var anchorEl = tabBtnEl || redBarEl || formEl;" +
                "if(anchorEl){" +
                "  var absTop = anchorEl.getBoundingClientRect().top + window.scrollY;" +
                "  window.scrollTo(0, Math.max(0, absTop - 12));" +
                "  var boxRef = redBarEl || formEl || anchorEl;" +
                "  var br = boxRef.getBoundingClientRect();" +
                "  var topY = tabBtnEl ? (tabBtnEl.getBoundingClientRect().top - 6) : (br.top - 6);" +
                "  var botY = br.bottom + 420;" +
                "  if(prodLblEl){" +
                "    var pr = prodLblEl.getBoundingClientRect();" +
                "    var prodBottom = pr.bottom;" +
                "    var pSib = prodLblEl.nextElementSibling;" +
                "    if(pSib){ prodBottom = Math.max(prodBottom, pSib.getBoundingClientRect().bottom); }" +
                "    else if(prodLblEl.parentElement && prodLblEl.parentElement.nextElementSibling){" +
                "      prodBottom = Math.max(prodBottom, prodLblEl.parentElement.nextElementSibling.getBoundingClientRect().bottom);" +
                "    }" +
                "    botY = prodBottom + 18;" +
                "  }" +
                "  if(schedEl){" +
                "    var schedTop = schedEl.getBoundingClientRect().top - 10;" +
                "    if(schedTop > topY + 180){ botY = prodLblEl ? Math.min(botY, schedTop) : schedTop; }" +
                "  }" +
                "  var leftX = Math.max(0, br.left - 16);" +
                "  var rightX = Math.min(window.innerWidth, br.right + 16);" +
                "  var cropW = Math.max(200, rightX - leftX);" +
                "  var cropH = Math.max(200, botY - topY);" +
                "  rectObj = {left:leftX, top:Math.max(0, topY), width:cropW, height:cropH, vw:window.innerWidth, vh:window.innerHeight};" +
                "}" +
                "return JSON.stringify({wo:wo, sc:sc, service_number:srv, contact_number:contact, customer_name:cust, service_address:addr, rect:rectObj});" +
                "})();";

        webView.evaluateJavascript(js, rawJson -> {
            JSONObject extracted = null;
            try {
                if (rawJson != null && !rawJson.equals("null")) {
                    String clean = rawJson;
                    if (clean.startsWith("\"") && clean.endsWith("\"")) {
                        clean = new JSONTokener(rawJson).nextValue().toString();
                    }
                    extracted = new JSONObject(clean);
                }
            } catch (Exception ignored) {
            }

            final JSONObject finalExtracted = extracted;
            mainHandler.postDelayed(() -> {
                try {
                    int width = webView.getWidth();
                    int height = webView.getHeight();
                    if (width <= 0) width = 1366;
                    if (height <= 0) height = 900;

                    int hdScale = 2;
                    Bitmap fullBitmap = Bitmap.createBitmap(width * hdScale, height * hdScale, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(fullBitmap);
                    canvas.scale(hdScale, hdScale);
                    webView.draw(canvas);

                    Bitmap resultBitmap = fullBitmap;
                    if (finalExtracted != null && finalExtracted.has("rect") && !finalExtracted.isNull("rect")) {
                        try {
                            JSONObject r = finalExtracted.getJSONObject("rect");
                            double vw = r.optDouble("vw", 0);
                            double vh = r.optDouble("vh", 0);
                            if (vw > 0 && vh > 0) {
                                double scaleX = (double) fullBitmap.getWidth() / vw;
                                double scaleY = (double) fullBitmap.getHeight() / vh;
                                int padPx = 8;
                                int cropX = Math.max(0, (int) (r.optDouble("left", 0) * scaleX) - padPx);
                                int cropY = Math.max(0, (int) (r.optDouble("top", 0) * scaleY) - padPx);
                                int cropW = Math.min(fullBitmap.getWidth() - cropX, (int) (r.optDouble("width", 0) * scaleX) + padPx * 2);
                                int cropH = Math.min(fullBitmap.getHeight() - cropY, (int) (r.optDouble("height", 0) * scaleY) + padPx * 2);
                                if (cropW > 200 && cropH > 120) {
                                    resultBitmap = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    callback.onExtracted(resultBitmap, finalExtracted);
                } catch (Exception e) {
                    callback.onExtracted(null, finalExtracted);
                }
            }, 300);
        });
    }

    private static Uri saveBitmapToGalleryAndClipboard(Context context, Bitmap bitmap, String noSc) {
        try {
            String safeName = (noSc != null ? noSc : "WO").replaceAll("[^a-zA-Z0-9_-]", "_");
            String fileName = "SS_WFM_" + safeName + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AdingWFM");
            }
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out);
                    }
                }
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newUri(context.getContentResolver(), "SS WFM " + safeName, uri);
                    clipboard.setPrimaryClip(clip);
                }
                return uri;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void injectDesktopViewport(WebView webView) {
        String js = "(function(){" +
                "var meta = document.querySelector('meta[name=\"viewport\"]');" +
                "if(!meta){ meta = document.createElement('meta'); meta.name = 'viewport'; document.head.appendChild(meta); }" +
                "meta.setAttribute('content', 'width=1366');" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public static void showControlPanelDialog(Activity activity, WebView mainWebView) {
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            SharedPreferences prefs = getPrefs(activity);

            ScrollView scrollView = new ScrollView(activity);
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 20);
            container.setPadding(pad, pad, pad, pad);
            scrollView.addView(container);

            TextView tvTitle = new TextView(activity);
            tvTitle.setText("⚡ Pengaturan Akun WFM FLOW");
            tvTitle.setTextSize(17f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.parseColor("#F9010F"));
            container.addView(tvTitle);

            TextView tvSub = new TextView(activity);
            tvSub.setText("Otomasi WFM FLOW berjalan di latar belakang saat Anda klik tombol [Dispatch] di ADING.");
            tvSub.setTextSize(12f);
            tvSub.setTextColor(Color.parseColor("#4B5563"));
            tvSub.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
            container.addView(tvSub);

            LinearLayout navRow = new LinearLayout(activity);
            navRow.setOrientation(LinearLayout.HORIZONTAL);
            Button btnLoginFlow = createSmallButton(activity, "🔑 Buka Sesi FLOW", "#111827");
            Button btnCheckUpdate = createSmallButton(activity, "🔄 Cek Update", "#059669");
            navRow.addView(btnLoginFlow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            navRow.addView(btnCheckUpdate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            container.addView(navRow);

            addSectionHeader(container, activity, "🧪 Test Manual Cari WONUM / SC di FLOW");
            EditText etNoSc = createInput(activity, "WONUM (contoh: WO069272106) atau NO SC", "", false);
            container.addView(etNoSc);

            addSectionHeader(container, activity, "🔐 Akun FLOW (flow.telkomakses.co.id)");
            EditText etFlowUser = createInput(activity, "Username FLOW", prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER), false);
            EditText etFlowPass = createInput(activity, "Password FLOW", prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS), true);
            container.addView(etFlowUser);
            container.addView(etFlowPass);

            addSectionHeader(container, activity, "✈️ Bot Telegram AutoSender");
            EditText etTgToken = createInput(activity, "Bot Token Telegram", prefs.getString(KEY_TG_TOKEN, DEFAULT_TG_TOKEN), false);
            EditText etTgChatId = createInput(activity, "Chat ID Fallback (jika teknisi belum terdaftar)", prefs.getString(KEY_TG_CHAT_ID, DEFAULT_TG_CHAT_ID), false);
            EditText etTgThreadId = createInput(activity, "Topic / Thread ID (opsional)", prefs.getString(KEY_TG_THREAD_ID, ""), false);
            container.addView(etTgToken);
            container.addView(etTgChatId);
            container.addView(etTgThreadId);

            addSectionHeader(container, activity, "👷 Mapping Teknisi → Channel,ID Pribadi");
            EditText etTechMapping = new EditText(activity);
            etTechMapping.setHint("NAMA=-100channel,id_pribadi");
            etTechMapping.setText(prefs.getString(KEY_TECH_MAPPING, DEFAULT_TECH_MAPPING));
            etTechMapping.setTextSize(11.5f);
            etTechMapping.setSingleLine(false);
            etTechMapping.setMinLines(4);
            etTechMapping.setMaxLines(8);
            etTechMapping.setGravity(Gravity.TOP | Gravity.START);
            container.addView(etTechMapping);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(scrollView)
                    .setPositiveButton("🚀 Jalankan Otomasi", null)
                    .setNeutralButton("💾 Simpan Akun", null)
                    .setNegativeButton("Tutup", (d, w) -> d.dismiss())
                    .create();

            btnLoginFlow.setOnClickListener(v -> {
                saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString());
                saveTelegramConfig(activity, etTgToken.getText().toString(), etTgChatId.getText().toString(), etTgThreadId.getText().toString(), etTechMapping.getText().toString());
                dialog.dismiss();
                openSessionHelperDialog(activity, FLOW_WORKORDER_URL, "🔑 Sesi Login FLOW Telkom Akses");
            });

            btnCheckUpdate.setOnClickListener(v -> {
                dialog.dismiss();
                GitHubAutoUpdater.checkForUpdates(activity, true);
            });

            dialog.setOnShowListener(dlg -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString());
                    saveTelegramConfig(activity, etTgToken.getText().toString(), etTgChatId.getText().toString(), etTgThreadId.getText().toString(), etTechMapping.getText().toString());
                    Toast.makeText(activity, "Kredensial FLOW & Mapping Teknisi disimpan!", Toast.LENGTH_SHORT).show();
                });

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString());
                    saveTelegramConfig(activity, etTgToken.getText().toString(), etTgChatId.getText().toString(), etTgThreadId.getText().toString(), etTechMapping.getText().toString());
                    String inputVal = etNoSc.getText().toString().trim();
                    if (inputVal.isEmpty()) {
                        Toast.makeText(activity, "Masukkan WONUM atau NO SC terlebih dahulu!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String manualWonum = inputVal.toUpperCase().startsWith("WO") ? inputVal.toUpperCase() : "";
                    dialog.dismiss();
                    runFullDispatchToFlowPipeline(
                            activity,
                            inputVal,
                            manualWonum,
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            ""
                    );
                });
            });

            dialog.show();
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void openSessionHelperDialog(Activity activity, String targetUrl, String title) {
        if (activity == null || activity.isFinishing()) return;
        restoreCookies(activity);

        Dialog dlg = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#111827"));
        int pad = dp(activity, 10);
        topBar.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(13f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        topBar.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnDone = createSmallButton(activity, "✅ Selesai & Simpan Sesi", "#10B981");
        btnDone.setOnClickListener(v -> {
            persistCookies(activity);
            dlg.dismiss();
        });
        topBar.addView(btnDone);
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        WebView wv = new WebView(activity);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                persistCookies(activity);
                if (url == null) return;
                if (url.contains("flow.telkomakses.co.id")) {
                    if (url.contains("/unauthorize")) {
                        view.loadUrl(FLOW_LOGIN_URL);
                    }
                }
            }
        });

        root.addView(wv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        dlg.setContentView(root);
        dlg.show();
        wv.loadUrl(targetUrl);
    }

    private static void addSectionHeader(LinearLayout container, Context context, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#111827"));
        tv.setPadding(0, dp(context, 14), 0, dp(context, 4));
        container.addView(tv);
    }

    private static EditText createInput(Context context, String hint, String value, boolean isPassword) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setText(value);
        et.setTextSize(13f);
        et.setSingleLine(true);
        if (isPassword) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return et;
    }

    private static Button createSmallButton(Context context, String text, String bgColorHex) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextSize(11f);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(bgColorHex));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(context, 4), 0, dp(context, 4), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    private static List<String> resolveTechnicianTargetChatIds(
            Context context,
            String leaderName,
            String namaTim,
            String leaderNik,
            String fallbackChatId
    ) {
        List<String> targets = new ArrayList<>();
        String mappingRaw = getPrefs(context).getString(KEY_TECH_MAPPING, DEFAULT_TECH_MAPPING);
        String hayLeader = leaderName != null ? leaderName.trim().toUpperCase(Locale.ROOT) : "";
        String hayTim = namaTim != null ? namaTim.trim().toUpperCase(Locale.ROOT) : "";
        String hayNik = leaderNik != null ? leaderNik.trim().toUpperCase(Locale.ROOT) : "";

        if (mappingRaw != null && (!hayLeader.isEmpty() || !hayTim.isEmpty() || !hayNik.isEmpty())) {
            String[] lines = mappingRaw.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.contains("=")) continue;
                int eq = trimmed.indexOf('=');
                String key = trimmed.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                String vals = trimmed.substring(eq + 1).trim();
                if (key.isEmpty() || vals.isEmpty()) continue;

                boolean matched = (!hayLeader.isEmpty() && (hayLeader.contains(key) || key.contains(hayLeader)))
                        || (!hayTim.isEmpty() && hayTim.contains(key))
                        || (!hayNik.isEmpty() && key.equals(hayNik));
                if (matched) {
                    for (String idPart : vals.split(",")) {
                        String cid = idPart.trim();
                        if (!cid.isEmpty() && !targets.contains(cid)) {
                            targets.add(cid);
                        }
                    }
                }
            }
        }
        if (targets.isEmpty() && fallbackChatId != null && !fallbackChatId.trim().isEmpty()) {
            targets.add(fallbackChatId.trim());
        }
        return targets;
    }

    private static String unmaskCaptionWithFlowData(
            String rawCaption,
            String flowCustName,
            String flowAddress,
            String flowContactNo,
            String flowServiceNo
    ) {
        if (rawCaption == null || rawCaption.isEmpty()) return "";
        String[] lines = rawCaption.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String upper = line.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("CUSTOMER NAME:") && flowCustName != null && !flowCustName.isEmpty() && !flowCustName.contains("*")) {
                line = "CUSTOMER NAME: " + flowCustName;
            } else if (upper.startsWith("ALAMAT:") && flowAddress != null && !flowAddress.isEmpty() && !flowAddress.contains("*")) {
                line = "ALAMAT: " + flowAddress;
            } else if (upper.startsWith("CONTACT NUMBER:") && line.contains("*") && flowContactNo != null && !flowContactNo.isEmpty() && !flowContactNo.contains("*")) {
                line = "CONTACT NUMBER: " + flowContactNo;
            } else if (upper.startsWith("NO INTERNET:") && line.contains("*") && flowServiceNo != null && !flowServiceNo.isEmpty() && !flowServiceNo.contains("*")) {
                line = "NO INTERNET: " + flowServiceNo;
            }
            if (i > 0) out.append("\n");
            out.append(line);
        }
        return out.toString();
    }

    private static String buildFallbackTelegramCaption(
            String noSc,
            String wonum,
            String bookingDate,
            String sto,
            String paket,
            String noInternet,
            String customerName,
            String contactNumber,
            String alamat,
            String odp,
            String koordinat
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("NO SC: ").append(nonEmptyOrDash(noSc)).append("\n");
        sb.append("WONUM: ").append(nonEmptyOrDash(wonum)).append("\n");
        sb.append("BOOKING DATE: ").append(nonEmptyOrDash(bookingDate)).append("\n");
        sb.append("STO: ").append(nonEmptyOrDash(sto)).append("\n");
        sb.append("PAKET: ").append(nonEmptyOrDash(paket)).append("\n");
        sb.append("NO INTERNET: ").append(nonEmptyOrDash(noInternet)).append("\n");
        sb.append("CUSTOMER NAME: ").append(nonEmptyOrDash(customerName)).append("\n");
        sb.append("CONTACT NUMBER: ").append(nonEmptyOrDash(contactNumber)).append("\n");
        sb.append("ALAMAT: ").append(nonEmptyOrDash(alamat)).append("\n");
        sb.append("ODP: ").append(nonEmptyOrDash(odp)).append("\n");
        sb.append("KORDINAT: ").append(nonEmptyOrDash(koordinat));
        return sb.toString();
    }

    private static String nonEmptyOrDash(String v) {
        return (v != null && !v.trim().isEmpty()) ? v.trim() : "-";
    }

    private static String sendPhotoViaRailwayAutoDiscovery(
            Bitmap bitmap,
            String caption,
            String leaderName,
            String leaderNik,
            String namaTim,
            String fallbackChatId
    ) {
        HttpURLConnection conn = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            String photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            JSONObject body = new JSONObject();
            body.put("leader_name", leaderName != null ? leaderName : "");
            body.put("leader_nik", leaderNik != null ? leaderNik : "");
            body.put("nama_tim", namaTim != null ? namaTim : "");
            body.put("caption", caption != null ? caption : "");
            body.put("photo_base64", photoB64);
            body.put("fallback_chat_id", fallbackChatId != null ? fallbackChatId : "");

            URL url = new URL(RAILWAY_BRIDGE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(25000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Ading-Key", RAILWAY_API_KEY);

            byte[] payloadBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payloadBytes);
                out.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                java.io.InputStream is = conn.getInputStream();
                ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = is.read(buf)) != -1) respBuf.write(buf, 0, n);
                JSONObject resJson = new JSONObject(respBuf.toString("UTF-8"));
                if (resJson.optBoolean("ok", false)) {
                    return resJson.optBoolean("fallback", false) ? "FALLBACK" : "CHANNEL";
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean uploadPhotoToTelegram(String token, String chatId, String threadId, Bitmap bitmap, String caption) {
        HttpURLConnection conn = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] pngBytes = baos.toByteArray();

            String boundary = "----AdingTelegramBoundary" + System.currentTimeMillis();
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendPhoto");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                writeMultipartField(out, boundary, "chat_id", chatId);
                if (threadId != null && !threadId.trim().isEmpty()) {
                    writeMultipartField(out, boundary, "message_thread_id", threadId.trim());
                }
                if (caption != null && !caption.isEmpty()) {
                    String safePlain = caption.length() > 950 ? caption.substring(0, 950) : caption;
                    String[] formatted = formatTelegramDispatchHtmlAndMarkup(safePlain);
                    writeMultipartField(out, boundary, "caption", formatted[0]);
                    writeMultipartField(out, boundary, "parse_mode", "HTML");
                    if (formatted[1] != null && !formatted[1].isEmpty()) {
                        writeMultipartField(out, boundary, "reply_markup", formatted[1]);
                    }
                }
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"workorder.png\"\r\n");
                out.writeBytes("Content-Type: image/png\r\n\r\n");
                out.write(pngBytes);
                out.writeBytes("\r\n--" + boundary + "--\r\n");
                out.flush();
            }

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void writeMultipartField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static String[] formatTelegramDispatchHtmlAndMarkup(String plainCaption) {
        StringBuilder htmlOut = new StringBuilder();
        StringBuilder summaryOut = new StringBuilder();
        String waDigits = "";
        String mapsCoord = "";
        String[] lines = plainCaption.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) htmlOut.append("\n");
            String rawLine = lines[i];
            int colonIdx = rawLine.indexOf(':');
            if (colonIdx < 0) {
                htmlOut.append(escapeHtml(rawLine));
                continue;
            }
            String label = rawLine.substring(0, colonIdx).trim();
            String labelUpper = label.toUpperCase(Locale.ROOT);
            String val = rawLine.substring(colonIdx + 1).trim();
            if (val.isEmpty() || "-".equals(val)) {
                htmlOut.append(escapeHtml(label)).append(": ").append(escapeHtml(val));
                continue;
            }
            if ("CONTACT NUMBER".equals(labelUpper)) {
                String digits = val.replaceAll("\\D+", "");
                if (digits.startsWith("0")) digits = "62" + digits.substring(1);
                else if (digits.startsWith("8")) digits = "62" + digits;
                if (digits.length() >= 9) {
                    waDigits = digits;
                    if (summaryOut.length() > 0) summaryOut.append("\n");
                    summaryOut.append("HP: +").append(digits);
                    htmlOut.append(escapeHtml(label)).append(": +").append(digits)
                            .append(" (<a href=\"https://wa.me/").append(digits).append("\">💬 WA</a>)");
                } else {
                    htmlOut.append(escapeHtml(label)).append(": <code>").append(escapeHtml(val)).append("</code>");
                }
                continue;
            }
            if ("KORDINAT".equals(labelUpper) || "KOORDINAT".equals(labelUpper)) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d{1,3}\\.\\d+)\\s*,\\s*(-?\\d{1,3}\\.\\d+)").matcher(val);
                if (m.find()) {
                    String lat = m.group(1);
                    String lng = m.group(2);
                    mapsCoord = lat + "," + lng;
                    if (summaryOut.length() > 0) summaryOut.append("\n");
                    summaryOut.append("LOC: ").append(mapsCoord);
                    htmlOut.append(escapeHtml(label)).append(": <code>").append(escapeHtml(val))
                            .append("</code> (<a href=\"https://maps.google.com/?q=").append(mapsCoord).append("\">📍 Maps</a>)");
                } else {
                    htmlOut.append(escapeHtml(label)).append(": <code>").append(escapeHtml(val)).append("</code>");
                }
                continue;
            }
            if ("NO SC".equals(labelUpper) || "WONUM".equals(labelUpper) || "NO INTERNET".equals(labelUpper)
                    || "CUSTOMER NAME".equals(labelUpper) || "ALAMAT".equals(labelUpper) || "ODP".equals(labelUpper)) {
                if (!"ALAMAT".equals(labelUpper)) {
                    if (summaryOut.length() > 0) summaryOut.append("\n");
                    String shortVal = val.length() > 40 ? val.substring(0, 40) : val;
                    summaryOut.append(labelUpper).append(": ").append(shortVal);
                }
                htmlOut.append(escapeHtml(label)).append(": <code>").append(escapeHtml(val)).append("</code>");
                continue;
            }
            htmlOut.append(escapeHtml(label)).append(": ").append(escapeHtml(val));
        }

        String markupJson = "";
        try {
            JSONArray rows = new JSONArray();
            JSONArray actionRow = new JSONArray();
            if (!waDigits.isEmpty()) {
                JSONObject b = new JSONObject();
                b.put("text", "💬 Chat WA");
                b.put("url", "https://wa.me/" + waDigits);
                actionRow.put(b);
            }
            if (!mapsCoord.isEmpty()) {
                JSONObject b = new JSONObject();
                b.put("text", "📍 Buka Maps");
                b.put("url", "https://maps.google.com/?q=" + mapsCoord);
                actionRow.put(b);
            }
            if (actionRow.length() > 0) rows.put(actionRow);
            if (summaryOut.length() > 0) {
                String copyTxt = summaryOut.toString();
                if (copyTxt.length() > 250) copyTxt = copyTxt.substring(0, 250);
                JSONObject copyObj = new JSONObject();
                copyObj.put("text", copyTxt);
                JSONObject copyBtn = new JSONObject();
                copyBtn.put("text", "📋 Copy Ringkas");
                copyBtn.put("copy_text", copyObj);
                JSONArray copyRow = new JSONArray();
                copyRow.put(copyBtn);
                rows.put(copyRow);
            }
            if (rows.length() > 0) {
                JSONObject rm = new JSONObject();
                rm.put("inline_keyboard", rows);
                markupJson = rm.toString();
            }
        } catch (Exception ignored) {
        }
        return new String[]{htmlOut.toString(), markupJson};
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "");
    }
}
