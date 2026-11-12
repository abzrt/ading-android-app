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
    public static final String HEBITPRO_URL = "https://hebitpro-kaltara.ops.web.id/";
    public static final String HEBITPRO_TASK_ORDER_URL = "https://hebitpro-kaltara.ops.web.id/task-order";
    public static final String HEBITPRO_TEK_PROVI_URL = "https://hebitpro-kaltara.ops.web.id/master/tek-provi";
    public static final String ADING_URL = "https://ading.web.id/";

    public static final String DEFAULT_FLOW_USER = "16830482";
    public static final String DEFAULT_FLOW_PASS = "Narindo@2026";
    public static final String DEFAULT_HEBIT_USER = "irfan_nnk";
    public static final String DEFAULT_HEBIT_PASS = "Hebitpro2026";

    private static final String PREFS_NAME = "ading_wfm_hebit_prefs";
    private static final String KEY_FLOW_USER = "flow_username";
    private static final String KEY_FLOW_PASS = "flow_password";
    private static final String KEY_HEBIT_USER = "hebit_username";
    private static final String KEY_HEBIT_PASS = "hebit_password";

    private static final String KEY_FLOW_COOKIES = "flow_saved_cookies";
    private static final String KEY_HEBIT_COOKIES = "hebit_saved_cookies";

    private static final String BG_CHANNEL_ID = "ading_bg_dispatch_pipeline";
    private static final String BG_CHANNEL_NAME = "Otomasi Background FLOW & HebitPro";
    private static final int NOTIF_PROGRESS_ID = 8801;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SharedPreferences getPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = null;
        if (prefs.getString(KEY_FLOW_USER, "").isEmpty()) {
            editor = prefs.edit();
            editor.putString(KEY_FLOW_USER, DEFAULT_FLOW_USER);
            editor.putString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS);
        }
        if (prefs.getString(KEY_HEBIT_USER, "").isEmpty()) {
            if (editor == null) editor = prefs.edit();
            editor.putString(KEY_HEBIT_USER, DEFAULT_HEBIT_USER);
            editor.putString(KEY_HEBIT_PASS, DEFAULT_HEBIT_PASS);
        }
        if (editor != null) {
            editor.apply();
        }
        return prefs;
    }

    public static void saveCredentials(
            Context context,
            String flowUser,
            String flowPass,
            String hebitUser,
            String hebitPass
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_FLOW_USER, (flowUser != null && !flowUser.trim().isEmpty()) ? flowUser.trim() : DEFAULT_FLOW_USER)
                .putString(KEY_FLOW_PASS, (flowPass != null && !flowPass.isEmpty()) ? flowPass : DEFAULT_FLOW_PASS)
                .putString(KEY_HEBIT_USER, (hebitUser != null && !hebitUser.trim().isEmpty()) ? hebitUser.trim() : DEFAULT_HEBIT_USER)
                .putString(KEY_HEBIT_PASS, (hebitPass != null && !hebitPass.isEmpty()) ? hebitPass : DEFAULT_HEBIT_PASS)
                .apply();
    }

    /**
     * Persists FLOW and HebitPro session cookies into SharedPreferences
     */
    public static void persistCookies(Context context) {
        if (context == null) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.flush();
            String flowCookies = cm.getCookie("https://flow.telkomakses.co.id");
            String hebitCookies = cm.getCookie("https://hebitpro-kaltara.ops.web.id");
            SharedPreferences.Editor editor = getPrefs(context).edit();
            if (flowCookies != null && !flowCookies.isEmpty()) {
                editor.putString(KEY_FLOW_COOKIES, flowCookies);
            }
            if (hebitCookies != null && !hebitCookies.isEmpty()) {
                editor.putString(KEY_HEBIT_COOKIES, hebitCookies);
            }
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Restores FLOW and HebitPro cookies from SharedPreferences into CookieManager
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
            String hebitCookies = prefs.getString(KEY_HEBIT_COOKIES, "");
            if (!hebitCookies.isEmpty()) {
                String[] parts = hebitCookies.split(";");
                for (String p : parts) {
                    String cookie = p.trim();
                    if (!cookie.isEmpty()) {
                        cm.setCookie("https://hebitpro-kaltara.ops.web.id", cookie + "; path=/; secure; samesite=lax");
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
                ch.setDescription("Notifikasi proses latar belakang pencarian WFM FLOW dan assign HebitPro");
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
     * Handles failure gracefully with specific attribution to either FLOW or HebitPro.
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

                if (targetWeb.contains("HebitPro")) {
                    Button btnLogin = new Button(activity);
                    btnLogin.setText("🔑 Buka Sesi HebitPro");
                    btnLogin.setTextColor(Color.WHITE);
                    btnLogin.setBackgroundColor(Color.parseColor("#002E7A"));
                    btnLogin.setAllCaps(false);
                    btnLogin.setOnClickListener(v -> {
                        dialog.dismiss();
                        openSessionHelperDialog(activity, HEBITPRO_TASK_ORDER_URL, "🔑 Sesi Login HebitPro Kaltara");
                    });
                    btnRow.addView(btnLogin);
                } else if (targetWeb.contains("FLOW")) {
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
                "fetch(window.location.origin + '/api/provisioning/team-options?_t=' + Date.now())" +
                "  .then(function(r){ return r.ok ? r.json() : []; })" +
                "  .then(function(arr){ if(Array.isArray(arr) && arr.length > 0) window.__adingTeamsCache = arr; })" +
                "  .catch(function(){});" +
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
                "          if(Array.isArray(arr) && arr.length > 0) window.__adingTeamsCache = arr;" +
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
                "          var payload = {" +
                "            no_sc: noSc," +
                "            wonum: item.wonum || cachedOrder.WONUM || ''," +
                "            sto: item.sto || cachedOrder.STO || ''," +
                "            service_area: sa || (cachedTeam ? cachedTeam.service_area : '')," +
                "            nama_tim: namaTim," +
                "            leader_nik: cachedTeam && cachedTeam.leader_nik ? String(cachedTeam.leader_nik) : ''," +
                "            leader_name: cachedTeam && cachedTeam.leader_name ? String(cachedTeam.leader_name) : ''," +
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

            runFullDispatchToFlowAndHebitPipeline(
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
     *   and auto-assigns in HebitPro in 4-6 seconds total!
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void runFullDispatchToFlowAndHebitPipeline(
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
            String hebitUser = prefs.getString(KEY_HEBIT_USER, DEFAULT_HEBIT_USER);
            String hebitPass = prefs.getString(KEY_HEBIT_PASS, DEFAULT_HEBIT_PASS);

            String techDisplay = !leaderName.isEmpty()
                    ? (leaderName + (!leaderNik.isEmpty() ? " (" + leaderNik + ")" : ""))
                    : (!namaTim.isEmpty() ? namaTim : "Teknisi");

            Toast.makeText(activity, "⚡ Memproses di background: FLOW WFM & HebitPro (" + noSc + ")", Toast.LENGTH_SHORT).show();
            updateProgressNotification(
                    activity,
                    "⚡ Dispatch Order: " + noSc,
                    "[1/2] Membuka FLOW Telkom Akses → " + techDisplay,
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

            final int[] currentStage = {1}; // 1 = FLOW, 2 = HEBITPRO
            final boolean[] flowSsCaptured = {false};
            final String[] capturedBase64Png = {""};
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

            Runnable captureFlowAndProceedToHebit = () -> {
                if (flowSsCaptured[0] && currentStage[0] == 2) return;
                flowSsCaptured[0] = true;

                updateProgressNotification(
                        activity,
                        "⚡ Dispatch Order: " + noSc,
                        "📸 Mengambil SS Workorder dari FLOW...",
                        true
                );

                extractFlowCardAndCropBitmap(bgWebView, (croppedBitmap, extractedJson) -> {
                    if (croppedBitmap != null) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 92, bos);
                        capturedBase64Png[0] = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
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

                    currentStage[0] = 2;
                    updateProgressNotification(
                            activity,
                            "⚡ Dispatch Order: " + noSc,
                            "[2/2] Membuka HebitPro (/task-order) untuk Assign ke " + techDisplay + "...",
                            true
                    );
                    bgWebView.loadUrl(HEBITPRO_TASK_ORDER_URL);
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
                    String targetWeb = failingUrl.contains("flow.telkomakses") ? "FLOW Telkom Akses" : (failingUrl.contains("hebitpro") ? "HebitPro Kaltara" : "Web Server");
                    handlePipelineFailure(activity, targetWeb, "Koneksi ke " + targetWeb + " terputus: " + description + " (Kode " + errorCode + "). Periksa jaringan Anda.", cleanup);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    persistCookies(activity);
                    if (url == null || isFinished[0]) return;

                    if (url.contains("flow.telkomakses.co.id")) {
                        injectDesktopViewport(view);
                    } else if (url.contains("hebitpro-kaltara.ops.web.id")) {
                        injectDesktopViewport(view);
                        if (url.contains("/login")) {
                            handlePipelineFailure(
                                    activity,
                                    "HebitPro Kaltara",
                                    "Sesi login HebitPro belum aktif atau sudah kadaluarsa (Perlu verifikasi captcha). Silakan klik [Buka Sesi HebitPro] untuk login sekali.",
                                    cleanup
                            );
                        } else if (!url.contains("/task-order")) {
                            view.loadUrl(HEBITPRO_TASK_ORDER_URL);
                        } else {
                            runHebitProTaskOrderLoop(
                                    view,
                                    isFinished,
                                    activity,
                                    noSc,
                                    resolvedWonum[0],
                                    leaderNik,
                                    leaderName,
                                    namaTim,
                                    odp,
                                    paket,
                                    koordinat,
                                    capturedBase64Png[0],
                                    cleanup,
                                    0
                            );
                        }
                    }
                }
            });

            // Start master high-speed state loop
            bgWebView.loadUrl(FLOW_WORKORDER_URL);
            runFlowPipelineFastLoop(
                    bgWebView,
                    isFinished,
                    noSc,
                    flowUser,
                    flowPass,
                    flowSsCaptured,
                    isWaitingForOtp,
                    activity,
                    captureFlowAndProceedToHebit,
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
            String flowUser,
            String flowPass,
            boolean[] flowSsCaptured,
            boolean[] isWaitingForOtp,
            Activity activity,
            Runnable onCardReady,
            Runnable cleanup,
            int attempt
    ) {
        if (isFinished[0] || flowSsCaptured[0] || attempt > 85) {
            if (attempt > 85 && !flowSsCaptured[0] && !isFinished[0]) {
                handlePipelineFailure(
                        activity,
                        "FLOW Telkom Akses",
                        "Waktu habis (Timeout 30 detik) saat memproses data di FLOW Telkom Akses. Tidak dapat menemukan Workorder atau sesi login kadaluarsa.",
                        cleanup
                );
            }
            return;
        }

        mainHandler.postDelayed(() -> {
            if (isFinished[0] || flowSsCaptured[0]) return;

            // Pause polling while waiting for user to type OTP in the dialog
            if (isWaitingForOtp[0]) {
                runFlowPipelineFastLoop(view, isFinished, noSc, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt + 1);
                return;
            }

            String safeSc = escapeJs(noSc);
            String safeUser = escapeJs(flowUser);
            String safePass = escapeJs(flowPass);

            String js = "(function(){" +
                    "var path = window.location.pathname || '';" +
                    "var targetSc = '" + safeSc + "';" +
                    "var targetLower = targetSc.toLowerCase();" +

                    // 1. DETAIL CARD READY CHECK (Foto Ketiga)
                    "function getDetailVal(lbl){" +
                    "  var nodes = document.querySelectorAll('label, div, span, td');" +
                    "  for(var i=0; i<nodes.length; i++){" +
                    "    var n = nodes[i];" +
                    "    if(n.children.length > 1) continue;" +
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
                    "if(scDetailVal && scDetailVal.toLowerCase().indexOf(targetLower) !== -1 && woDetailVal.toUpperCase().indexOf('WO') !== -1){" +
                    "  document.querySelectorAll('.modal.show, .modal-backdrop').forEach(function(m){ m.style.display='none'; });" +
                    "  return 'DETAIL_CARD_READY:' + woDetailVal;" +
                    "}" +

                    // 2. ON /workorder TABLE: FILTER & CLICK ROW
                    "if(path.indexOf('/workorder') !== -1){" +
                    "  document.querySelectorAll('.modal-backdrop').forEach(function(b){ if(!document.querySelector('.modal.show')) b.remove(); });" +
                    "  if(window.__adingFlowFilterApplied === targetSc){" +
                    "    var rows = document.querySelectorAll('table tbody tr');" +
                    "    for(var r=0; r<rows.length; r++){" +
                    "      var rTxt = (rows[r].innerText || '');" +
                    "      if(rTxt.toLowerCase().indexOf(targetLower) !== -1 || (rows.length === 1 && rTxt.toUpperCase().indexOf('WO0') !== -1)){" +
                    "        if(!rows[r].dataset.adingClicked){" +
                    "          rows[r].dataset.adingClicked = '1';" +
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
                    "    window.location.href = '/workorder';" +
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
                    "      window.Swal.fire = function(){" +
                    "        setTimeout(function(){ window.location.href = '/otp'; }, 50);" +
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

                    // 6. IF ON /home OR /dashboard, DIRECTLY OPEN /workorder
                    "if(path.indexOf('/home') !== -1 || path.indexOf('/dashboard') !== -1){" +
                    "  window.location.href = '/workorder';" +
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
                if (res.startsWith("SUBMITTED_LOGIN")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "🔐 [1/2] Mengirim login FLOW (" + flowUser + ")...", true);
                } else if (res.startsWith("READY_FOR_OTP")) {
                    isWaitingForOtp[0] = true;
                    updateProgressNotification(activity, "📲 Masukkan OTP FLOW", "Buka dialog untuk verifikasi OTP", true);
                    showOtpInputDialog(activity, view, noSc, isWaitingForOtp, cleanup);
                } else if (res.startsWith("OPENED_FILTER_MODAL")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "🔍 [1/2] Membuka Filter Search Workorder Fulfillment...", true);
                } else if (res.startsWith("APPLIED_SC_FILTER")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "🔍 [1/2] Filter SC Order no di-Apply! Menunggu tabel...", true);
                } else if (res.startsWith("ROW_CLICKED")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "📄 [1/2] Baris ditemukan! Membuka detail kartu Workorder...", true);
                } else if (res.startsWith("DETAIL_CARD_READY")) {
                    updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "✅ [1/2] Kartu Workorder tampil! Mengambil Screenshot...", true);
                    persistCookies(activity);
                    mainHandler.postDelayed(onCardReady, 600);
                    return;
                }
                runFlowPipelineFastLoop(view, isFinished, noSc, flowUser, flowPass, flowSsCaptured, isWaitingForOtp, activity, onCardReady, cleanup, attempt + 1);
            });
        }, 350);
    }

    /**
     * Shows a clean 6-digit OTP input dialog with ADING's visual design.
     * When entered, it submits the OTP directly into the background WebView waiting on /otp.
     */
    private static void showOtpInputDialog(Activity activity, WebView bgWebView, String noSc, boolean[] isWaitingForOtp, Runnable onCancel) {
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
                            "      setTimeout(function(){ window.location.href = '/workorder'; }, 50);" +
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

    /**
     * STAGE 3 LOOP (FAST BACKGROUND POLLING EVERY 450MS):
     */
    private static void runHebitProTaskOrderLoop(
            WebView view,
            boolean[] isFinished,
            Activity activity,
            String noSc,
            String wonum,
            String leaderNik,
            String leaderName,
            String namaTim,
            String odp,
            String paket,
            String koordinat,
            String base64Png,
            Runnable cleanup,
            int attempt
    ) {
        if (isFinished[0] || attempt > 80) {
            if (attempt > 80 && !isFinished[0]) {
                handlePipelineFailure(
                        activity,
                        "HebitPro Kaltara",
                        "Waktu habis (Timeout 35 detik) saat mencari order " + noSc + " atau membuka form assign di HebitPro Kaltara.",
                        cleanup
                );
            }
            return;
        }
        mainHandler.postDelayed(() -> {
            if (isFinished[0]) return;
            injectHebitProTaskOrderAutomation(
                    view,
                    noSc,
                    wonum,
                    leaderNik,
                    leaderName,
                    namaTim,
                    odp,
                    paket,
                    koordinat,
                    base64Png,
                    statusResult -> {
                        if (statusResult != null && statusResult.contains("DONE_SUBMITTED")) {
                            persistCookies(activity);
                            cancelProgressNotification(activity);
                            Toast.makeText(activity, "✅ Order " + noSc + " berhasil di-assign & dikirim ke Telegram Teknisi!", Toast.LENGTH_LONG).show();
                            mainHandler.postDelayed(cleanup, 1200);
                        } else {
                            if (statusResult != null && statusResult.contains("SEARCHED_TABLE")) {
                                updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "🔍 [2/2] Mencari order di tabel HebitPro...", true);
                            } else if (statusResult != null && statusResult.contains("CLICKED_TO_TEKNISI")) {
                                updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "✈️ [2/2] Klik [To Teknisi] → Membuka form Assign...", true);
                            } else if (statusResult != null && statusResult.contains("SWAL_CONFIRMED")) {
                                updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "📋 [2/2] Konfirmasi Data Baru → Membuka Form Assign...", true);
                            } else if (statusResult != null && (statusResult.contains("CLICKED_SIMPAN_ASSIGN") || statusResult.contains("MODAL_OPEN"))) {
                                updateProgressNotification(activity, "⚡ Dispatch: " + noSc, "💾 [2/2] Menempelkan SS WFM & Simpan Assign ke Teknisi...", true);
                            }
                            runHebitProTaskOrderLoop(
                                    view,
                                    isFinished,
                                    activity,
                                    noSc,
                                    wonum,
                                    leaderNik,
                                    leaderName,
                                    namaTim,
                                    odp,
                                    paket,
                                    koordinat,
                                    base64Png,
                                    cleanup,
                                    attempt + 1
                            );
                        }
                    }
            );
        }, 450);
    }

    private interface StringCallback {
        void onResult(String val);
    }

    private static void injectHebitProTaskOrderAutomation(
            WebView view,
            String noSc,
            String wonum,
            String leaderNik,
            String leaderName,
            String namaTim,
            String odp,
            String paket,
            String koordinat,
            String base64Png,
            StringCallback callback
    ) {
        String lat = "";
        String lng = "";
        if (koordinat != null && koordinat.contains(",")) {
            String[] parts = koordinat.split(",", 2);
            lat = parts[0].trim();
            lng = parts[1].trim();
        }

        String safeSc = escapeJs(noSc);
        String safeWo = escapeJs(wonum);
        String safeNik = escapeJs(leaderNik);
        String safeName = escapeJs(leaderName);
        String safeTim = escapeJs(namaTim);
        String safeOdp = escapeJs("-".equals(odp) ? "" : odp);
        String safePaket = escapeJs("-".equals(paket) ? "" : paket);
        String safeLat = escapeJs(lat);
        String safeLng = escapeJs(lng);
        String safeB64 = base64Png != null ? base64Png : "";

        String js = "(function(){" +
                "var noSc = '" + safeSc + "';" +
                "var wonum = '" + safeWo + "';" +
                "var nik = '" + safeNik + "';" +
                "var techName = '" + safeName + "';" +
                "var timName = '" + safeTim + "';" +
                "var odpVal = '" + safeOdp + "';" +
                "var paketVal = '" + safePaket + "';" +
                "var latVal = '" + safeLat + "';" +
                "var lngVal = '" + safeLng + "';" +
                "var b64Png = '" + safeB64 + "';" +

                "var swalConfirm = document.querySelector('.swal2-popup button.swal2-confirm, .swal2-container button.swal2-confirm, button.swal2-confirm, .swal-button--confirm');" +
                "if(!swalConfirm){" +
                "  var allSwalBtns = document.querySelectorAll('.swal2-popup button, .swal2-container button, .sweet-alert button');" +
                "  for(var sb=0; sb<allSwalBtns.length; sb++){" +
                "    var st = (allSwalBtns[sb].innerText || allSwalBtns[sb].textContent || '').trim().toLowerCase();" +
                "    if(st === 'assign' || st === 'ya' || st.indexOf('lanjut') !== -1){" +
                "      swalConfirm = allSwalBtns[sb]; break;" +
                "    }" +
                "  }" +
                "}" +
                "if(swalConfirm){" +
                "  swalConfirm.click();" +
                "  return 'SWAL_CONFIRMED';" +
                "}" +

                "var assignModal = null;" +
                "var modals = document.querySelectorAll('.modal.show, .modal[style*=\"display: block\"]');" +
                "for(var m=0; m<modals.length; m++){" +
                "  if((modals[m].innerText || '').indexOf('Assign Order') !== -1 || (modals[m].innerText || '').indexOf('Pilih Teknisi') !== -1){" +
                "    assignModal = modals[m]; break;" +
                "  }" +
                "}" +
                "if(assignModal){" +
                "  function fillInputByPlaceholderOrLabel(scope, phSubstr, lblSubstr, val){" +
                "    if(!val) return;" +
                "    var inp = scope.querySelector('input[placeholder*=\"' + phSubstr + '\" i], textarea[placeholder*=\"' + phSubstr + '\" i]');" +
                "    if(!inp && lblSubstr){" +
                "      var lbls = scope.querySelectorAll('label');" +
                "      for(var i=0; i<lbls.length; i++){" +
                "        if((lbls[i].innerText || '').toLowerCase().indexOf(lblSubstr.toLowerCase()) !== -1){" +
                "          inp = lbls[i].parentElement.querySelector('input, textarea');" +
                "          if(inp) break;" +
                "        }" +
                "      }" +
                "    }" +
                "    if(inp && !inp.value){" +
                "      inp.value = val;" +
                "      inp.dispatchEvent(new Event('input', {bubbles:true}));" +
                "      inp.dispatchEvent(new Event('change', {bubbles:true}));" +
                "    }" +
                "  }" +
                "  fillInputByPlaceholderOrLabel(assignModal, 'ODP', 'Nama ODP', odpVal);" +
                "  fillInputByPlaceholderOrLabel(assignModal, 'IndiHome', 'Paket', paketVal);" +
                "  fillInputByPlaceholderOrLabel(assignModal, '-6.', 'Latitude', latVal);" +
                "  fillInputByPlaceholderOrLabel(assignModal, '106.', 'Longitude', lngVal);" +

                "  var techSelected = false;" +
                "  var sel = assignModal.querySelector('select');" +
                "  if(sel){" +
                "    var opts = sel.querySelectorAll('option');" +
                "    var matchedVal = '';" +
                "    for(var o=0; o<opts.length; o++){" +
                "      var oTxt = (opts[o].innerText || opts[o].textContent || '').toLowerCase();" +
                "      var oVal = (opts[o].value || '').toLowerCase();" +
                "      if((nik && (oTxt.indexOf(nik.toLowerCase()) !== -1 || oVal.indexOf(nik.toLowerCase()) !== -1)) ||" +
                "         (techName && oTxt.indexOf(techName.toLowerCase()) !== -1) ||" +
                "         (timName && oTxt.indexOf(timName.toLowerCase()) !== -1)){" +
                "        matchedVal = opts[o].value;" +
                "        opts[o].selected = true;" +
                "        break;" +
                "      }" +
                "    }" +
                "    if(matchedVal){" +
                "      sel.value = matchedVal;" +
                "      sel.dispatchEvent(new Event('change', {bubbles:true}));" +
                "      if(window.jQuery){ try{ window.jQuery(sel).val(matchedVal).trigger('change'); }catch(e){} }" +
                "      techSelected = true;" +
                "    } else if(window.jQuery && (nik || techName)){" +
                "      try {" +
                "        if(!sel.dataset.adingSelect2Opened){" +
                "          sel.dataset.adingSelect2Opened = '1';" +
                "          window.jQuery(sel).select2('open');" +
                "          var sField = document.querySelector('.select2-container--open .select2-search__field');" +
                "          if(sField){" +
                "            sField.value = nik || techName;" +
                "            sField.dispatchEvent(new Event('input', {bubbles:true}));" +
                "            sField.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));" +
                "          }" +
                "        } else {" +
                "          var sRes = document.querySelectorAll('.select2-results__option');" +
                "          for(var sr=0; sr<sRes.length; sr++){" +
                "            var srTxt = (sRes[sr].innerText || '').toLowerCase();" +
                "            if((nik && srTxt.indexOf(nik.toLowerCase()) !== -1) || (techName && srTxt.indexOf(techName.toLowerCase()) !== -1)){" +
                "              sRes[sr].click();" +
                "              techSelected = true;" +
                "              break;" +
                "            }" +
                "          }" +
                "        }" +
                "      } catch(e){}" +
                "    }" +
                "  }" +
                "  var techInput = assignModal.querySelector('input[placeholder*=\"Ketik NIK\" i], input[placeholder*=\"Nama Teknisi\" i]');" +
                "  if(techInput && !techInput.value && (nik || techName)){" +
                "    techInput.focus();" +
                "    techInput.value = nik || techName;" +
                "    techInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    techInput.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));" +
                "  }" +

                "  if(b64Png && !assignModal.dataset.adingSsPasted){" +
                "    var ssBox = assignModal.querySelector('textarea[placeholder*=\"Paste screenshot\" i], input[placeholder*=\"Paste screenshot\" i], [placeholder*=\"Paste screenshot\" i]');" +
                "    if(ssBox){" +
                "      try {" +
                "        assignModal.dataset.adingSsPasted = '1';" +
                "        var bStr = atob(b64Png);" +
                "        var ab = new ArrayBuffer(bStr.length);" +
                "        var ia = new Uint8Array(ab);" +
                "        for(var i=0; i<bStr.length; i++){ ia[i] = bStr.charCodeAt(i); }" +
                "        var blob = new Blob([ab], {type:'image/png'});" +
                "        var file = new File([blob], 'ss_wfm_' + noSc + '.png', {type:'image/png', lastModified:Date.now()});" +
                "        var dt = new DataTransfer();" +
                "        dt.items.add(file);" +
                "        ssBox.focus();" +
                "        ssBox.click();" +
                "        var pasteEvt;" +
                "        try {" +
                "          pasteEvt = new ClipboardEvent('paste', {bubbles:true, cancelable:true, clipboardData:dt});" +
                "        } catch(e){" +
                "          pasteEvt = new Event('paste', {bubbles:true, cancelable:true});" +
                "        }" +
                "        if(!pasteEvt.clipboardData || !pasteEvt.clipboardData.items || pasteEvt.clipboardData.items.length === 0){" +
                "          try { Object.defineProperty(pasteEvt, 'clipboardData', {value:dt}); } catch(e){}" +
                "        }" +
                "        ssBox.dispatchEvent(pasteEvt);" +
                "        var fileInputs = assignModal.querySelectorAll('input[type=\"file\"]');" +
                "        if(fileInputs.length > 0){" +
                "          try { fileInputs[0].files = dt.files; fileInputs[0].dispatchEvent(new Event('change', {bubbles:true})); } catch(e){}" +
                "        }" +
                "      } catch(e){}" +
                "    }" +
                "  }" +

                "  if(!assignModal.dataset.adingSaveClicked){" +
                "    var modalBtns = assignModal.querySelectorAll('button, input[type=\"submit\"]');" +
                "    for(var mb=0; mb<modalBtns.length; mb++){" +
                "      var mbTxt = (modalBtns[mb].innerText || modalBtns[mb].value || '').toLowerCase();" +
                "      if(mbTxt.indexOf('simpan & assign') !== -1 || mbTxt.indexOf('simpan') !== -1){" +
                "        assignModal.dataset.adingSaveClicked = '1';" +
                "        window.__adingHebitFormSubmitted = true;" +
                "        window.__adingHebitSaveTime = Date.now();" +
                "        setTimeout(function(){ modalBtns[mb].click(); }, 350);" +
                "        return 'CLICKED_SIMPAN_ASSIGN';" +
                "      }" +
                "    }" +
                "  }" +
                "  return 'MODAL_OPEN';" +
                "}" +

                "if(window.__adingHebitFormSubmitted){" +
                "  if(window.__adingHebitSaveTime && (Date.now() - window.__adingHebitSaveTime) < 2500){" +
                "    return 'WAITING_POST_SAVE';" +
                "  }" +
                "  return 'DONE_SUBMITTED';" +
                "}" +

                "var searchTarget = (noSc || wonum || '').trim();" +
                "if(searchTarget && window.__adingHebitSearched !== searchTarget){" +
                "  window.__adingHebitSearched = searchTarget;" +
                "  if(window.jQuery && window.jQuery.fn && window.jQuery.fn.dataTable){" +
                "    try {" +
                "      var dtApi = window.jQuery.fn.dataTable.tables({api:true});" +
                "      if(dtApi && dtApi.search){ dtApi.search(searchTarget).draw(); }" +
                "    } catch(e){}" +
                "  }" +
                "  var sInp = document.querySelector('input[type=\"search\"], .dataTables_filter input');" +
                "  if(sInp){" +
                "    sInp.value = searchTarget;" +
                "    sInp.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    sInp.dispatchEvent(new Event('change', {bubbles:true}));" +
                "    sInp.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', keyCode:13, bubbles:true}));" +
                "  }" +
                "  return 'SEARCHED_TABLE';" +
                "}" +

                "if(window.__adingToTeknisiClickTime && (Date.now() - window.__adingToTeknisiClickTime) > 4000){" +
                "  var oldClicked = document.querySelectorAll('[data-ading-to-teknisi-clicked]');" +
                "  for(var oc=0; oc<oldClicked.length; oc++){ delete oldClicked[oc].dataset.adingToTeknisiClicked; }" +
                "  delete window.__adingToTeknisiClickTime;" +
                "}" +

                "var rows = document.querySelectorAll('table tbody tr');" +
                "for(var r=0; r<rows.length; r++){" +
                "  var rTxt = (rows[r].innerText || '').toLowerCase();" +
                "  var matchSc = noSc && rTxt.indexOf(noSc.toLowerCase()) !== -1;" +
                "  var matchWo = wonum && rTxt.indexOf(wonum.toLowerCase()) !== -1;" +
                "  var singleMatch = (rows.length === 1 && rTxt.indexOf('no data') === -1 && rTxt.indexOf('tidak') === -1 && rTxt.indexOf('loading') === -1 && rTxt.trim().length > 10);" +
                "  if(matchSc || matchWo || singleMatch){" +
                "    var rowBtns = rows[r].querySelectorAll('button, a');" +
                "    for(var b=0; b<rowBtns.length; b++){" +
                "      var bTxt = (rowBtns[b].innerText || rowBtns[b].title || '').toLowerCase().replace(/\\s+/g, ' ');" +
                "      var hasTelegramIcon = rowBtns[b].querySelector('.fa-telegram, .fa-telegram-plane, .fa-paper-plane, .bi-send, .bi-telegram') !== null;" +
                "      if(bTxt.indexOf('teknisi') !== -1 || bTxt.indexOf('assign') !== -1 || (bTxt.indexOf('to') !== -1 && bTxt.indexOf('teknisi') !== -1) || hasTelegramIcon){" +
                "        if(!rowBtns[b].dataset.adingToTeknisiClicked){" +
                "          rowBtns[b].dataset.adingToTeknisiClicked = '1';" +
                "          window.__adingToTeknisiClickTime = Date.now();" +
                "          rowBtns[b].click();" +
                "          return 'CLICKED_TO_TEKNISI';" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "}" +
                "return 'WAITING_TABLE';" +
                "})();";

        view.evaluateJavascript(js, rawResult -> {
            String res = rawResult != null ? rawResult.replace("\"", "") : "";
            if (callback != null) {
                callback.onResult(res);
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
                "var cardEl = null;" +
                "var headers = document.querySelectorAll('.card-header, .bg-danger, h5, h6, div');" +
                "for(var j=0; j<headers.length; j++){" +
                "  var ht = (headers[j].innerText || '').trim();" +
                "  if(ht === 'Workorder'){" +
                "    cardEl = headers[j].closest('.tab-pane') || headers[j].closest('.card') || headers[j].parentElement;" +
                "    if(cardEl) break;" +
                "  }" +
                "}" +
                "var rectObj = null;" +
                "if(cardEl){" +
                "  cardEl.scrollIntoView({block:'start', inline:'nearest'});" +
                "  var r = cardEl.getBoundingClientRect();" +
                "  rectObj = {left:r.left, top:r.top, width:r.width, height:r.height, vw:window.innerWidth, vh:window.innerHeight};" +
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

    private static void injectHebitAutoLoginScript(WebView webView, String username, String password) {
        String safeUser = escapeJs(username);
        String safePass = escapeJs(password);
        String js = "(function(){" +
                "var userInput = document.getElementById('username') || document.querySelector('input[name=\"username\"]');" +
                "var passInput = document.getElementById('password') || document.querySelector('input[name=\"password\"]');" +
                "if(!userInput || !passInput || passInput.dataset.adingFilled) return;" +
                "passInput.dataset.adingFilled = '1';" +
                "userInput.value = '" + safeUser + "';" +
                "userInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "userInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "passInput.value = '" + safePass + "';" +
                "passInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "passInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "var rem = document.getElementById('remember');" +
                "if(rem && !rem.checked){ rem.checked = true; }" +
                "})();";
        webView.evaluateJavascript(js, null);
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
            tvTitle.setText("⚡ Pengaturan Akun FLOW & HebitPro");
            tvTitle.setTextSize(17f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.parseColor("#F9010F"));
            container.addView(tvTitle);

            TextView tvSub = new TextView(activity);
            tvSub.setText("Otomasi berjalan sepenuhnya di latar belakang saat Anda klik tombol [Dispatch] di ADING.");
            tvSub.setTextSize(12f);
            tvSub.setTextColor(Color.parseColor("#4B5563"));
            tvSub.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
            container.addView(tvSub);

            LinearLayout navRow = new LinearLayout(activity);
            navRow.setOrientation(LinearLayout.HORIZONTAL);
            Button btnLoginFlow = createSmallButton(activity, "🔑 Buka Sesi FLOW", "#111827");
            Button btnLoginHebit = createSmallButton(activity, "🔑 Buka Sesi HebitPro", "#002E7A");
            navRow.addView(btnLoginFlow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            navRow.addView(btnLoginHebit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            container.addView(navRow);

            addSectionHeader(container, activity, "🧪 Test Manual Cari SC di FLOW & Assign HebitPro");
            EditText etNoSc = createInput(activity, "NO SC (contoh: AOi4261112044318992845c20)", "", false);
            EditText etTeknisiNik = createInput(activity, "NIK / Nama Teknisi (contoh: 16983839 / ARIFIN)", "", false);
            container.addView(etNoSc);
            container.addView(etTeknisiNik);

            addSectionHeader(container, activity, "🔐 Akun FLOW (flow.telkomakses.co.id)");
            EditText etFlowUser = createInput(activity, "Username FLOW", prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER), false);
            EditText etFlowPass = createInput(activity, "Password FLOW", prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS), true);
            container.addView(etFlowUser);
            container.addView(etFlowPass);

            addSectionHeader(container, activity, "🔐 Akun HebitPro (hebitpro-kaltara.ops.web.id)");
            EditText etHebitUser = createInput(activity, "Username HebitPro", prefs.getString(KEY_HEBIT_USER, DEFAULT_HEBIT_USER), false);
            EditText etHebitPass = createInput(activity, "Password HebitPro", prefs.getString(KEY_HEBIT_PASS, DEFAULT_HEBIT_PASS), true);
            container.addView(etHebitUser);
            container.addView(etHebitPass);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(scrollView)
                    .setPositiveButton("🚀 Jalankan Otomasi", null)
                    .setNeutralButton("💾 Simpan Akun", null)
                    .setNegativeButton("Tutup", (d, w) -> d.dismiss())
                    .create();

            btnLoginFlow.setOnClickListener(v -> {
                saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString(), etHebitUser.getText().toString(), etHebitPass.getText().toString());
                dialog.dismiss();
                openSessionHelperDialog(activity, FLOW_WORKORDER_URL, "🔑 Sesi Login FLOW Telkom Akses");
            });

            btnLoginHebit.setOnClickListener(v -> {
                saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString(), etHebitUser.getText().toString(), etHebitPass.getText().toString());
                dialog.dismiss();
                openSessionHelperDialog(activity, HEBITPRO_TASK_ORDER_URL, "🔑 Sesi Login HebitPro Kaltara");
            });

            dialog.setOnShowListener(dlg -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString(), etHebitUser.getText().toString(), etHebitPass.getText().toString());
                    Toast.makeText(activity, "Kredensial FLOW & HebitPro disimpan!", Toast.LENGTH_SHORT).show();
                });

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    saveCredentials(activity, etFlowUser.getText().toString(), etFlowPass.getText().toString(), etHebitUser.getText().toString(), etHebitPass.getText().toString());
                    String sc = etNoSc.getText().toString().trim();
                    if (sc.isEmpty()) {
                        Toast.makeText(activity, "Masukkan NO SC terlebih dahulu!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String tech = etTeknisiNik.getText().toString().trim();
                    dialog.dismiss();
                    runFullDispatchToFlowAndHebitPipeline(
                            activity,
                            sc,
                            "",
                            "",
                            tech,
                            tech,
                            tech,
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

        SharedPreferences prefs = getPrefs(activity);
        String flowUser = prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER);
        String flowPass = prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS);
        String hebitUser = prefs.getString(KEY_HEBIT_USER, DEFAULT_HEBIT_USER);
        String hebitPass = prefs.getString(KEY_HEBIT_PASS, DEFAULT_HEBIT_PASS);

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
                } else if (url.contains("hebitpro-kaltara.ops.web.id")) {
                    injectHebitAutoLoginScript(view, hebitUser, hebitPass);
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
