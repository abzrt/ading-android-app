package id.web.ading;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
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

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public class WfmFlowAutomationManager {

    public static final String FLOW_LOGIN_URL = "https://flow.telkomakses.co.id/";
    public static final String FLOW_WORKORDER_URL = "https://flow.telkomakses.co.id/workorder";
    public static final String FLOW_EDIT_WORKORDER_URL = "https://flow.telkomakses.co.id/EditWorkorder/";
    public static final String ADING_URL = "https://ading.web.id/";

    public static final String DEFAULT_FLOW_USER = "16830482";
    public static final String DEFAULT_FLOW_PASS = "narindo1";

    private static final String PREFS_NAME = "ading_wfm_prefs";
    private static final String KEY_FLOW_USER = "flow_username";
    private static final String KEY_FLOW_PASS = "flow_password";

    private static final String KEY_FLOW_COOKIES = "flow_saved_cookies";

    private static final String BG_CHANNEL_ID = "ading_bg_dispatch_pipeline";
    private static final String BG_CHANNEL_NAME = "Otomasi Background WFM FLOW";
    private static final int NOTIF_PROGRESS_ID = 8801;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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


    /**
     * Persists FLOW session cookies into SharedPreferences
     */
    public static void persistCookies(Context context) {
        if (context == null) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.flush();
            String flowCookies = cm.getCookie("https://flow.telkomakses.co.id");
            SharedPreferences.Editor editor = getPrefs(context).edit();
            if (flowCookies != null && !flowCookies.isEmpty()) {
                editor.putString(KEY_FLOW_COOKIES, flowCookies);
            }
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Restores FLOW cookies from SharedPreferences into CookieManager
     */
    public static void restoreCookies(Context context) {
        if (context == null) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            SharedPreferences prefs = getPrefs(context);
            String flowCookies = prefs.getString(KEY_FLOW_COOKIES, "");
            if (!flowCookies.isEmpty()) {
                String[] parts = flowCookies.split(";");
                for (String p : parts) {
                    String cookie = p.trim();
                    if (!cookie.isEmpty()) {
                        cm.setCookie("https://flow.telkomakses.co.id", cookie + "; path=/; secure; samesite=lax");
                    }
                }
            }
            cm.flush();
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
                "            assigned_by: assignedBy" +
                "          };" +
                "          if(noSc && window.AdingAndroid && window.AdingAndroid.onDispatchOrderTriggered){" +
                "            window.AdingAndroid.onDispatchOrderTriggered(JSON.stringify(payload));" +
                "          }" +
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
                    assignedBy
            );
        } catch (Exception ignored) {
        }
    }

    /**
     * HIGH-SPEED BACKGROUND PIPELINE:
     * - Restores persistent cookies so session is active immediately.
     * - Fast-loop polls every 350ms, auto-submits login, fast-overrides SweetAlert timers.
     * - Instantly shows native OTP dialog on screen when OTP is ready.
     * - Once OTP is entered, immediately continues to /workorder, filters, crops screenshot,
     *   saves to Gallery/Clipboard, and triggers task assigned notification!
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
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            restoreCookies(activity);

            SharedPreferences prefs = getPrefs(activity);
            String flowUser = prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER);
            String flowPass = prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS);

            String techDisplay = !leaderName.isEmpty()
                    ? (leaderName + (!leaderNik.isEmpty() ? " (" + leaderNik + ")" : ""))
                    : (!namaTim.isEmpty() ? namaTim : "Teknisi");

            Toast.makeText(activity, "⚡ Memproses di background: FLOW WFM (" + noSc + ")", Toast.LENGTH_SHORT).show();
            updateProgressNotification(
                    activity,
                    "⚡ Dispatch Order: " + noSc,
                    "Membuka FLOW Telkom Akses → " + techDisplay,
                    true
            );

            ViewGroup root = activity.findViewById(R.id.rootLayout);
            if (root == null) {
                root = (ViewGroup) activity.findViewById(android.R.id.content);
            }
            if (root == null) return;

            View oldBg = root.findViewWithTag("TAG_WFM_BG_CONTAINER");
            if (oldBg != null) {
                root.removeView(oldBg);
            }

            FrameLayout bgContainer = new FrameLayout(activity);
            bgContainer.setTag("TAG_WFM_BG_CONTAINER");
            int bgWidth = 1366;
            int bgHeight = 900;
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(bgWidth, bgHeight);
            bgContainer.setLayoutParams(lp);

            bgContainer.setFocusable(false);
            bgContainer.setFocusableInTouchMode(false);
            bgContainer.setClickable(false);

            WebView bgWebView = new WebView(activity);
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
            settings.setSupportZoom(false);
            settings.setDisplayZoomControls(false);
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(bgWebView, true);

            bgContainer.addView(bgWebView);
            root.addView(bgContainer, 0); // At index 0 behind everything

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

            final ViewGroup finalRoot = root;
            Runnable cleanup = () -> {
                if (isFinished[0]) return;
                isFinished[0] = true;
                mainHandler.post(() -> {
                    try {
                        persistCookies(activity);
                        bgWebView.stopLoading();
                        bgWebView.loadUrl("about:blank");
                        bgContainer.removeAllViews();
                        bgWebView.destroy();
                        finalRoot.removeView(bgContainer);
                    } catch (Exception ignored) {
                    }
                });
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

                    String aoWoLabel = noSc + (!resolvedWonum[0].isEmpty() ? " | " + resolvedWonum[0] : "");
                    new AdingNotificationBridge(activity).notifyTaskAssigned(
                            aoWoLabel,
                            techDisplay,
                            !extCustName.isEmpty() ? extCustName : "-",
                            !extServiceNo.isEmpty() ? extServiceNo : "-",
                            !odp.isEmpty() ? odp : "-",
                            !paket.isEmpty() ? paket : "-",
                            !extAddress.isEmpty() ? extAddress : "-",
                            !extContactNo.isEmpty() ? extContactNo : "-",
                            !bookingDate.isEmpty() ? bookingDate : "-",
                            "Manja On Track",
                            !koordinat.isEmpty() ? koordinat : "-",
                            "-",
                            !assignedBy.isEmpty() ? assignedBy : "AHMAD PAIDI"
                    );

                    persistCookies(activity);
                    cancelProgressNotification(activity);
                    Toast.makeText(activity, "✅ Order " + noSc + " berhasil diproses & SS WFM disimpan ke Galeri!", Toast.LENGTH_LONG).show();
                    mainHandler.postDelayed(cleanup, 1200);
                });
            };

            bgWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    if (failingUrl == null || failingUrl.equals("about:blank") || isFinished[0]) return;
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
                    }
                }
            });

            // Start master high-speed state loop (direct to /EditWorkorder/<WONUM> if WONUM available)
            String startFlowUrl = !resolvedWonum[0].isEmpty()
                    ? (FLOW_EDIT_WORKORDER_URL + resolvedWonum[0])
                    : FLOW_WORKORDER_URL;
            bgWebView.loadUrl(startFlowUrl);
            runFlowPipelineFastLoop(
                    bgWebView,
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
        if (isFinished[0] || flowSsCaptured[0] || attempt > 110) {
            if (attempt > 110 && !flowSsCaptured[0] && !isFinished[0]) {
                handlePipelineFailure(
                        activity,
                        "FLOW Telkom Akses",
                        "Waktu habis (Timeout) saat memproses data di FLOW Telkom Akses. Tidak dapat menemukan Workorder atau sesi login kadaluarsa.",
                        cleanup
                );
            }
            return;
        }

        mainHandler.postDelayed(() -> {
            if (isFinished[0] || flowSsCaptured[0]) return;

            // Pause polling while waiting for user to type OTP in the dialog - do NOT increment attempt
            if (isWaitingForOtp[0]) {
                runFlowPipelineFastLoop(view, isFinished, noSc, targetWonum, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt);
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
                    "var isWoCardMatch = targetWoUpper ? (woDetailVal && woDetailVal.toUpperCase().indexOf(targetWoUpper) !== -1) : (scDetailVal && scDetailVal.toLowerCase().indexOf(targetLower) !== -1 && woDetailVal && woDetailVal.toUpperCase().indexOf('WO') !== -1);" +
                    "if(isWoCardMatch){" +
                    "  document.querySelectorAll('.modal.show, .modal-backdrop').forEach(function(m){ m.style.display='none'; });" +
                    "  return 'DETAIL_CARD_READY:' + woDetailVal;" +
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
                    "  var scInput = document.querySelector('input[placeholder*=\"SC Order\" i], input[name*=\"sc_order\" i], input[id*=\"sc_order\" i]');" +
                    "  if(!scInput){" +
                    "    var labels = document.querySelectorAll('label, div, span');" +
                    "    for(var k=0; k<labels.length; k++){" +
                    "      var lTxt = (labels[k].innerText || '').trim().toLowerCase();" +
                    "      if(lTxt === 'sc order no:' || lTxt === 'sc order no'){" +
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
                    "      if(aTxt === 'apply'){" +
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
                    "  if(localStorage.getItem('menus')){" +
                    "    window.location.href = targetFlowDest;" +
                    "    return 'REDIRECT_TO_WORKORDER';" +
                    "  }" +
                    "  return 'READY_FOR_OTP';" +
                    "}" +

                    // 4. CHECK IF LOGIN WAS SUBMITTED & USERNAME SET -> IMMEDIATELY JUMP TO /otp
                    "if(localStorage.getItem('username') && path.indexOf('/otp') === -1){" +
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

                    // 7. IF ON /unauthorize, RETURN TO /
                    "if(path.indexOf('/unauthorize') !== -1){" +
                    "  window.location.href = '/';" +
                    "  return 'NAVIGATING_TO_ROOT';" +
                    "}" +

                    "return 'WAITING';" +
                    "})();";

            view.evaluateJavascript(js, result -> {
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
                } else if (res.startsWith("SUBMITTED_LOGIN")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔐 [1/2] Mengirim login FLOW (" + flowUser + ")...", true);
                } else if (res.startsWith("READY_FOR_OTP")) {
                    isWaitingForOtp[0] = true;
                    updateProgressNotification(activity, "📲 Masukkan OTP FLOW", "Buka dialog untuk verifikasi OTP", true);
                    showOtpInputDialog(activity, view, noSc, targetWonum, isWaitingForOtp, cleanup);
                } else if (res.startsWith("NAVIGATING_TO_WONUM")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + labelKey, "🔍 [1/2] Membuka langsung Workorder " + targetWonum + "...", true);
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
                runFlowPipelineFastLoop(view, isFinished, noSc, targetWonum, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt + 1);
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
                root.setBackgroundColor(Color.WHITE);

                // ADING Header with red accent pill
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
                tvTitle.setText("Verifikasi OTP FLOW Telkom");
                tvTitle.setTextSize(16f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setTextColor(Color.parseColor("#111827"));
                headerRow.addView(tvTitle);
                root.addView(headerRow);

                TextView tvMsg = new TextView(activity);
                tvMsg.setText("Kode OTP telah dikirimkan oleh sistem FLOW ke Telegram Anda.\nMasukkan 6 digit kode OTP:");
                tvMsg.setTextSize(13f);
                tvMsg.setTextColor(Color.parseColor("#4B5563"));
                tvMsg.setPadding(0, dp(activity, 12), 0, dp(activity, 14));
                root.addView(tvMsg);

                final EditText etOtp = new EditText(activity);
                etOtp.setInputType(InputType.TYPE_CLASS_NUMBER);
                etOtp.setHint("123456");
                etOtp.setTextSize(26f);
                etOtp.setTypeface(null, Typeface.BOLD);
                etOtp.setTextColor(Color.parseColor("#F9010F"));
                etOtp.setGravity(Gravity.CENTER);
                etOtp.setBackgroundColor(Color.parseColor("#F3F4F6"));
                etOtp.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
                root.addView(etOtp);

                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);
                btnRow.setPadding(0, dp(activity, 18), 0, 0);

                Button btnCancel = new Button(activity);
                btnCancel.setText("Batal");
                btnCancel.setTextColor(Color.parseColor("#4B5563"));
                btnCancel.setBackgroundColor(Color.parseColor("#F3F4F6"));
                btnCancel.setAllCaps(false);
                LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cancelLp.setMargins(0, 0, dp(activity, 8), 0);
                btnCancel.setLayoutParams(cancelLp);
                btnRow.addView(btnCancel);

                Button btnSubmit = new Button(activity);
                btnSubmit.setText("✅ Verifikasi & Lanjut");
                btnSubmit.setTextColor(Color.WHITE);
                btnSubmit.setBackgroundColor(Color.parseColor("#F9010F"));
                btnSubmit.setAllCaps(false);
                btnSubmit.setTypeface(null, Typeface.BOLD);
                btnRow.addView(btnSubmit);

                root.addView(btnRow);

                builder.setView(root);
                builder.setCancelable(false);

                AlertDialog dialog = builder.create();

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
                            "  inp.focus();" +
                            "  inp.value = '" + safeOtp + "';" +
                            "  inp.dispatchEvent(new Event('input', {bubbles:true}));" +
                            "  inp.dispatchEvent(new Event('change', {bubbles:true}));" +
                            "  if(window.Swal){" +
                            "    var origSwal = window.Swal.fire;" +
                            "    window.Swal.fire = function(){" +
                            "      setTimeout(function(){ window.location.href = '" + safePostOtpUrl + "'; }, 50);" +
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
            } catch (Exception ignored) {
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
                "        var inp2 = p.querySelector('input,textarea,select,.form-control');" +
                "        if(inp2 && inp2 !== n) return (inp2.value || inp2.innerText || '').trim();" +
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
                "var formEl = document.querySelector('main form') || document.querySelector('form');" +
                "var tabEl = null;" +
                "var prodEl = null;" +
                "var schedEl = null;" +
                "var scanNodes = document.querySelectorAll('a, button, li, div, label, span, h5, h6');" +
                "for(var j=0; j<scanNodes.length; j++){" +
                "  var sn = scanNodes[j];" +
                "  if(sn.children.length > 2) continue;" +
                "  var st = (sn.innerText || '').trim();" +
                "  if(!tabEl && st === 'Related Record'){" +
                "    tabEl = sn.closest('ul, .nav, .nav-tabs') || sn.parentElement;" +
                "  }" +
                "  if(!prodEl && st.toLowerCase().indexOf('product name') !== -1){" +
                "    prodEl = sn.closest('.row, .form-group, .mb-2, .mb-3') || sn.parentElement;" +
                "  }" +
                "  if(!schedEl && st === 'Scheduling Information'){" +
                "    schedEl = sn;" +
                "  }" +
                "}" +
                "var rectObj = null;" +
                "var anchorEl = tabEl || formEl;" +
                "if(anchorEl){" +
                "  anchorEl.scrollIntoView({block:'start', inline:'nearest'});" +
                "  var fr = (formEl || anchorEl).getBoundingClientRect();" +
                "  var topY = tabEl ? tabEl.getBoundingClientRect().top : fr.top;" +
                "  var botY = prodEl ? (prodEl.getBoundingClientRect().bottom + 14) : (schedEl ? (schedEl.getBoundingClientRect().top - 8) : fr.bottom);" +
                "  var cropH = Math.max(200, botY - topY);" +
                "  rectObj = {left:fr.left, top:topY, width:fr.width, height:cropH, vw:window.innerWidth, vh:window.innerHeight};" +
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

                    Bitmap fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(fullBitmap);
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
                "if(meta){ meta.setAttribute('content', 'width=1280, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes'); }" +
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
            navRow.addView(btnLoginFlow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            container.addView(navRow);

            addSectionHeader(container, activity, "🧪 Test Manual Cari WONUM / SC di FLOW");
            EditText etNoSc = createInput(activity, "WONUM (contoh: WO069272106) atau NO SC", "", false);
            container.addView(etNoSc);

            addSectionHeader(container, activity, "🔐 Akun FLOW (flow.telkomakses.co.id)");
            EditText etFlowUser = createInput(activity, "Username FLOW", prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER), false);
            EditText etFlowPass = createInput(activity, "Password FLOW", prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS), true);
            container.addView(etFlowUser);
            container.addView(etFlowPass);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(scrollView)
                    .setPositiveButton("🚀 Jalankan Otomasi", null)
                    .setNeutralButton("💾 Simpan Akun", null)
                    .setNegativeButton("Tutup", (d, w) -> d.dismiss())
                    .create();

            btnLoginFlow.setOnClickListener(v -> {
                saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString());
                dialog.dismiss();
                openSessionHelperDialog(activity, FLOW_WORKORDER_URL, "🔑 Sesi Login FLOW Telkom Akses");
            });

            dialog.setOnShowListener(dlg -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString());
                    Toast.makeText(activity, "Kredensial FLOW disimpan!", Toast.LENGTH_SHORT).show();
                });

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString());
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

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "");
    }
}
