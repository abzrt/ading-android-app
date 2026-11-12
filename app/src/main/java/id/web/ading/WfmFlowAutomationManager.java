package id.web.ading;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WfmFlowAutomationManager {

    public static final String FLOW_LOGIN_URL = "https://flow.telkomakses.co.id/";
    public static final String FLOW_WORKORDER_URL = "https://flow.telkomakses.co.id/workorder";
    public static final String HEBITPRO_URL = "https://hebitpro-kaltara.ops.web.id/";
    public static final String ADING_URL = "https://ading.web.id/";

    // Pre-configured default credentials provided by user
    public static final String DEFAULT_FLOW_USER = "16830482";
    public static final String DEFAULT_FLOW_PASS = "Narindo@2026";
    public static final String DEFAULT_HEBIT_USER = "irfan_nnk";
    public static final String DEFAULT_HEBIT_PASS = "Hebitpro2026";

    private static final String PREFS_NAME = "ading_wfm_hebit_prefs";
    private static final String KEY_FLOW_USER = "flow_username";
    private static final String KEY_FLOW_PASS = "flow_password";
    private static final String KEY_HEBIT_USER = "hebit_username";
    private static final String KEY_HEBIT_PASS = "hebit_password";
    private static final String KEY_TG_TOKEN = "tg_bot_token";
    private static final String KEY_TG_CHAT_ID = "tg_chat_id";
    private static final String KEY_TG_THREAD_ID = "tg_thread_id";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
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
            String hebitPass,
            String tgBotToken,
            String tgChatId,
            String tgThreadId
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_FLOW_USER, (flowUser != null && !flowUser.trim().isEmpty()) ? flowUser.trim() : DEFAULT_FLOW_USER)
                .putString(KEY_FLOW_PASS, (flowPass != null && !flowPass.isEmpty()) ? flowPass : DEFAULT_FLOW_PASS)
                .putString(KEY_HEBIT_USER, (hebitUser != null && !hebitUser.trim().isEmpty()) ? hebitUser.trim() : DEFAULT_HEBIT_USER)
                .putString(KEY_HEBIT_PASS, (hebitPass != null && !hebitPass.isEmpty()) ? hebitPass : DEFAULT_HEBIT_PASS)
                .putString(KEY_TG_TOKEN, tgBotToken != null ? tgBotToken.trim() : "")
                .putString(KEY_TG_CHAT_ID, tgChatId != null ? tgChatId.trim() : "")
                .putString(KEY_TG_THREAD_ID, tgThreadId != null ? tgThreadId.trim() : "")
                .apply();
    }

    /**
     * Auto-fill login credentials when on flow.telkomakses.co.id or hebitpro-kaltara.ops.web.id
     * and inject floating quick-access bridge button
     */
    public static void onPageLoaded(WebView webView, String url) {
        if (webView == null || url == null) return;
        Context context = webView.getContext();
        SharedPreferences prefs = getPrefs(context);

        CookieManager.getInstance().flush();

        if (url.contains("flow.telkomakses.co.id")) {
            if (url.contains("/unauthorize")) {
                webView.loadUrl(FLOW_LOGIN_URL);
                return;
            }
            String user = prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER);
            String pass = prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS);
            injectFlowAutoLoginScript(webView, user, pass, true);
            if (url.contains("/workorder")) {
                injectDesktopViewportForWfm(webView);
            }
        } else if (url.contains("hebitpro-kaltara.ops.web.id")) {
            String user = prefs.getString(KEY_HEBIT_USER, DEFAULT_HEBIT_USER);
            String pass = prefs.getString(KEY_HEBIT_PASS, DEFAULT_HEBIT_PASS);
            injectHebitAutoLoginScript(webView, user, pass);
            injectHebitProAutoHook(webView);
        }

        injectFloatingControlWidget(webView);
    }

    /**
     * Auto-fills FLOW login form (#loginForm, #inputEmail4, #inputPassword4) and submits automatically
     */
    private static void injectFlowAutoLoginScript(WebView webView, String username, String password, boolean autoSubmit) {
        String safeUser = escapeJs(username);
        String safePass = escapeJs(password);
        String js = "(function(){" +
                "var otpInput = document.getElementById('otp');" +
                "if(otpInput){ otpInput.focus(); return; }" +
                "var userInput = document.getElementById('inputEmail4') || document.querySelector('input[name=\"username\"]');" +
                "var passInput = document.getElementById('inputPassword4') || document.querySelector('input[name=\"password\"]');" +
                "if(!userInput || !passInput || passInput.dataset.adingFilled) return;" +
                "passInput.dataset.adingFilled = '1';" +
                "userInput.value = '" + safeUser + "';" +
                "userInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "userInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "passInput.value = '" + safePass + "';" +
                "passInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "passInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "var rem = document.getElementById('gridCheck');" +
                "if(rem && !rem.checked){ rem.checked = true; }" +
                (autoSubmit ? (
                "setTimeout(function(){" +
                "  var btn = document.querySelector('#loginForm button[type=\"submit\"], button.btn-danger[type=\"submit\"]');" +
                "  if(btn && !btn.dataset.adingClicked){ btn.dataset.adingClicked = '1'; btn.click(); }" +
                "}, 350);"
                ) : "") +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Auto-fills HebitPro login form (#username, #password, #remember).
     * Does not auto-bypass reCAPTCHA; lets the user tap the checkbox if present.
     */
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

    private static void injectDesktopViewportForWfm(WebView webView) {
        String js = "(function(){" +
                "var meta = document.querySelector('meta[name=\"viewport\"]');" +
                "if(meta){ meta.setAttribute('content', 'width=1280, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes'); }" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Hook into hebitpro-kaltara.ops.web.id so that any WO/AO task assignment can automatically
     * trigger FLOW WFM Screenshot + Telegram Notification
     */
    private static void injectHebitProAutoHook(WebView webView) {
        String js = "(function(){" +
                "if(window.__adingHebitHooked) return;" +
                "window.__adingHebitHooked = true;" +
                "window.triggerFlowWfmScreenshot = function(woNumber, aoScNumber, taskJsonStr){" +
                "  if(window.AdingAndroid && window.AdingAndroid.autoCaptureWfmAndNotify){" +
                "    window.AdingAndroid.autoCaptureWfmAndNotify(woNumber||'', aoScNumber||'', taskJsonStr||'{}');" +
                "  }" +
                "};" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Inject a compact floating button on the bottom-right so the user can open the
     * FLOW WFM & HebitPro Connection / Auto-Screenshot Panel at any time
     */
    private static void injectFloatingControlWidget(WebView webView) {
        String js = "(function(){" +
                "if(document.getElementById('__ading_wfm_fab')) return;" +
                "var btn = document.createElement('div');" +
                "btn.id = '__ading_wfm_fab';" +
                "btn.innerHTML = '⚡ WFM & HebitPro';" +
                "btn.style.cssText = 'position:fixed;bottom:18px;right:16px;z-index:999999;background:linear-gradient(135deg,#F9010F,#002E7A);color:#fff;padding:10px 16px;border-radius:50px;font-family:sans-serif;font-size:12px;font-weight:bold;box-shadow:0 4px 14px rgba(0,0,0,0.28);cursor:pointer;user-select:none;display:flex;align-items:center;gap:6px;';" +
                "btn.onclick = function(){ if(window.AdingAndroid && window.AdingAndroid.openWfmHebitPanel){ window.AdingAndroid.openWfmHebitPanel(); } };" +
                "document.body.appendChild(btn);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Show Native Control Panel Dialog to:
     * 1. Save Username & Password for flow.telkomakses.co.id & hebitpro-kaltara.ops.web.id
     * 2. Switch quickly between ADING, HebitPro Kaltara, and FLOW WFM Workorder
     * 3. Run Automatic Workorder Search + Screenshot on FLOW + Send to Technician Telegram Channel
     */
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
            tvTitle.setText("⚡ Integrasi FLOW WFM & HebitPro");
            tvTitle.setTextSize(18f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.parseColor("#F9010F"));
            container.addView(tvTitle);

            TextView tvSub = new TextView(activity);
            tvSub.setText("Otomatis login & cari Workorder di FLOW Telkom Akses (16830482), ambil Screenshot kartu Workorder, dan kirim notifikasi ke Telegram Teknisi / HebitPro Kaltara (irfan_nnk).");
            tvSub.setTextSize(12f);
            tvSub.setTextColor(Color.parseColor("#6B7280"));
            tvSub.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
            container.addView(tvSub);

            // Quick Navigation Buttons
            LinearLayout navRow = new LinearLayout(activity);
            navRow.setOrientation(LinearLayout.HORIZONTAL);
            Button btnGoAding = createSmallButton(activity, "ADING", "#F9010F");
            Button btnGoHebit = createSmallButton(activity, "HebitPro", "#002E7A");
            Button btnGoFlow = createSmallButton(activity, "FLOW WFM", "#111827");
            navRow.addView(btnGoAding, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            navRow.addView(btnGoHebit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            navRow.addView(btnGoFlow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            container.addView(navRow);

            // Section 1: Auto Search WO & Screenshot -> Telegram
            addSectionHeader(container, activity, "📸 Auto Cari WO di FLOW & Screenshot WFM");
            EditText etWoNumber = createInput(activity, "Nomor Workorder / AO (wajib, misal: WO068288713)", "", false);
            container.addView(etWoNumber);

            EditText etTeknisi = createInput(activity, "Nama & NIK Teknisi (misal: ARIFIN (16983839))", "", false);
            container.addView(etTeknisi);

            EditText etPelanggan = createInput(activity, "Nama | ID Pelanggan (otomatis dari FLOW jika dikosongkan)", "", false);
            container.addView(etPelanggan);

            EditText etHpKoordinat = createInput(activity, "No HP | Koordinat (No HP otomatis dari FLOW jika kosong)", "", false);
            container.addView(etHpKoordinat);

            EditText etAlamat = createInput(activity, "Alamat | Assigned By (otomatis dari FLOW jika kosong)", "", false);
            container.addView(etAlamat);

            // Pre-detect WO number if user is currently viewing a page with a WO number
            if (mainWebView != null) {
                mainWebView.evaluateJavascript(
                        "(function(){ var m = document.body.innerText.match(/\\b(WO\\d{6,14}|AO[a-zA-Z0-9]{10,30})\\b/); return m ? m[1] : ''; })();",
                        detectedWo -> {
                            if (detectedWo != null) {
                                String clean = detectedWo.replace("\"", "").trim();
                                if (!clean.isEmpty() && etWoNumber.getText().toString().isEmpty()) {
                                    etWoNumber.setText(clean);
                                }
                            }
                        }
                );
            }

            // Section 2: Credentials for FLOW & HebitPro
            addSectionHeader(container, activity, "🔐 Akun FLOW (flow.telkomakses.co.id)");
            EditText etFlowUser = createInput(activity, "Username / NIK FLOW", prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER), false);
            EditText etFlowPass = createInput(activity, "Password FLOW", prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS), true);
            container.addView(etFlowUser);
            container.addView(etFlowPass);

            addSectionHeader(container, activity, "🔐 Akun HebitPro (hebitpro-kaltara.ops.web.id)");
            EditText etHebitUser = createInput(activity, "Username HebitPro", prefs.getString(KEY_HEBIT_USER, DEFAULT_HEBIT_USER), false);
            EditText etHebitPass = createInput(activity, "Password HebitPro", prefs.getString(KEY_HEBIT_PASS, DEFAULT_HEBIT_PASS), true);
            container.addView(etHebitUser);
            container.addView(etHebitPass);

            addSectionHeader(container, activity, "✈️ Konfigurasi Bot & Channel Telegram Teknisi");
            EditText etTgToken = createInput(activity, "Telegram Bot Token (opsional untuk kirim langsung)", prefs.getString(KEY_TG_TOKEN, ""), false);
            EditText etTgChatId = createInput(activity, "Chat ID Channel / Grup Teknisi (contoh: -100123456789)", prefs.getString(KEY_TG_CHAT_ID, ""), false);
            EditText etTgThreadId = createInput(activity, "Topic / Thread ID Teknisi (opsional)", prefs.getString(KEY_TG_THREAD_ID, ""), false);
            container.addView(etTgToken);
            container.addView(etTgChatId);
            container.addView(etTgThreadId);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(scrollView)
                    .setPositiveButton("🚀 Cari WO & Auto SS", null)
                    .setNeutralButton("💾 Simpan Akun", null)
                    .setNegativeButton("Tutup", (d, w) -> d.dismiss())
                    .create();

            btnGoAding.setOnClickListener(v -> {
                dialog.dismiss();
                if (mainWebView != null) mainWebView.loadUrl(ADING_URL);
            });
            btnGoHebit.setOnClickListener(v -> {
                saveInputs(activity, etFlowUser, etFlowPass, etHebitUser, etHebitPass, etTgToken, etTgChatId, etTgThreadId);
                dialog.dismiss();
                if (mainWebView != null) mainWebView.loadUrl(HEBITPRO_URL);
            });
            btnGoFlow.setOnClickListener(v -> {
                saveInputs(activity, etFlowUser, etFlowPass, etHebitUser, etHebitPass, etTgToken, etTgChatId, etTgThreadId);
                dialog.dismiss();
                if (mainWebView != null) mainWebView.loadUrl(FLOW_WORKORDER_URL);
            });

            dialog.setOnShowListener(dlg -> {
                Button btnSave = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                btnSave.setOnClickListener(v -> {
                    saveInputs(activity, etFlowUser, etFlowPass, etHebitUser, etHebitPass, etTgToken, etTgChatId, etTgThreadId);
                    Toast.makeText(activity, "Akun FLOW, HebitPro & Telegram berhasil disimpan!", Toast.LENGTH_SHORT).show();
                });

                Button btnRun = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                btnRun.setOnClickListener(v -> {
                    saveInputs(activity, etFlowUser, etFlowPass, etHebitUser, etHebitPass, etTgToken, etTgChatId, etTgThreadId);
                    String wo = etWoNumber.getText().toString().trim();
                    if (wo.isEmpty()) {
                        Toast.makeText(activity, "Masukkan nomor Workorder / AO terlebih dahulu!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String teknisi = etTeknisi.getText().toString().trim();
                    String pelanggan = etPelanggan.getText().toString().trim();
                    String hpKoord = etHpKoordinat.getText().toString().trim();
                    String alamat = etAlamat.getText().toString().trim();
                    dialog.dismiss();

                    runInteractiveWfmCapture(activity, wo, teknisi, pelanggan, hpKoord, alamat);
                });
            });

            dialog.show();
        });
    }

    private static void saveInputs(
            Context context,
            EditText etFlowUser,
            EditText etFlowPass,
            EditText etHebitUser,
            EditText etHebitPass,
            EditText etTgToken,
            EditText etTgChatId,
            EditText etTgThreadId
    ) {
        saveCredentials(
                context,
                etFlowUser.getText().toString(),
                etFlowPass.getText().toString(),
                etHebitUser.getText().toString(),
                etHebitPass.getText().toString(),
                etTgToken.getText().toString(),
                etTgChatId.getText().toString(),
                etTgThreadId.getText().toString()
        );
    }

    /**
     * Opens FLOW Workorder in an automation modal WebView, automatically logs in & searches for the Workorder,
     * extracts customer & order details directly from the FLOW Workorder card, crops a crisp screenshot of the
     * Workorder card, saves it to Gallery (Pictures/AdingWFM), and sends it + formatted Task Assigned notification to Telegram!
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void runInteractiveWfmCapture(
            Activity activity,
            String woOrAoNumber,
            String teknisiInfo,
            String pelangganInfo,
            String hpAndKoordinat,
            String alamatAndAssignedBy
    ) {
        if (activity == null || activity.isFinishing()) return;
        mainHandler.post(() -> {
            SharedPreferences prefs = getPrefs(activity);
            String flowUser = prefs.getString(KEY_FLOW_USER, DEFAULT_FLOW_USER);
            String flowPass = prefs.getString(KEY_FLOW_PASS, DEFAULT_FLOW_PASS);

            Dialog captureDialog = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            captureDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.WHITE);

            // Top Action Bar
            LinearLayout topBar = new LinearLayout(activity);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setBackgroundColor(Color.parseColor("#111827"));
            int pad = dp(activity, 10);
            topBar.setPadding(pad, pad, pad, pad);

            TextView tvStatus = new TextView(activity);
            tvStatus.setText("🔍 Membuka FLOW & mencari WO: " + woOrAoNumber + "...");
            tvStatus.setTextColor(Color.WHITE);
            tvStatus.setTextSize(12f);
            tvStatus.setTypeface(null, Typeface.BOLD);
            topBar.addView(tvStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button btnCaptureNow = createSmallButton(activity, "📸 SS & Kirim", "#F9010F");
            Button btnClose = createSmallButton(activity, "✖", "#374151");
            topBar.addView(btnCaptureNow);
            topBar.addView(btnClose);
            root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            WebView flowWebView = new WebView(activity);
            WebSettings settings = flowWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            // Desktop User-Agent so WFM Workorder card renders full 3-column layout like PC screenshot
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(flowWebView, true);

            root.addView(flowWebView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            captureDialog.setContentView(root);

            final boolean[] autoCaptured = {false};

            Runnable performScreenshotAndSend = () -> {
                tvStatus.setText("📸 Mengambil Screenshot Kartu Workorder & mengekstrak data...");
                extractWfmCardDataAndCrop(flowWebView, woOrAoNumber, (croppedBitmap, extractedJson) -> {
                    if (croppedBitmap == null) {
                        Toast.makeText(activity, "Gagal mengambil screenshot WFM", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Merge extracted FLOW Workorder fields with any user-supplied fields
                    String finalWoOrAo = woOrAoNumber;
                    String captionTarget = woOrAoNumber;
                    String finalPelanggan = pelangganInfo;
                    String finalHpKoord = hpAndKoordinat;
                    String finalAlamatAssigned = alamatAndAssignedBy;

                    if (extractedJson != null) {
                        String extWo = extractedJson.optString("wo", "").trim();
                        String extSc = extractedJson.optString("sc", "").trim();
                        String extAo = extractedJson.optString("ao", "").trim();
                        String extCustName = extractedJson.optString("customer_name", "").trim();
                        String extServiceNo = extractedJson.optString("service_number", "").trim();
                        String extContact = extractedJson.optString("contact_number", "").trim();
                        String extAddress = extractedJson.optString("service_address", "").trim();
                        String extAssignedBy = extractedJson.optString("assigned_by", "").trim();

                        if (!extAo.isEmpty() && !extWo.isEmpty()) {
                            finalWoOrAo = extAo + " | " + extWo;
                            captionTarget = extAo;
                        } else if (!extSc.isEmpty() && !extWo.isEmpty()) {
                            finalWoOrAo = extSc + " | " + extWo;
                            captionTarget = extSc;
                        } else if (!extWo.isEmpty()) {
                            finalWoOrAo = extWo;
                        }

                        if ((finalPelanggan == null || finalPelanggan.trim().isEmpty()) && (!extCustName.isEmpty() || !extServiceNo.isEmpty())) {
                            finalPelanggan = (extCustName.isEmpty() ? "-" : extCustName) + " | " + (extServiceNo.isEmpty() ? "-" : extServiceNo);
                        }

                        if (finalHpKoord == null || finalHpKoord.trim().isEmpty()) {
                            finalHpKoord = extContact;
                        } else if (!finalHpKoord.contains("|") && !extContact.isEmpty() && finalHpKoord.contains(",")) {
                            // User only typed coordinates, prepend contact number from FLOW
                            finalHpKoord = extContact + " | " + finalHpKoord;
                        }

                        if (finalAlamatAssigned == null || finalAlamatAssigned.trim().isEmpty()) {
                            finalAlamatAssigned = (extAddress.isEmpty() ? "-" : extAddress) + " | " + (extAssignedBy.isEmpty() ? "AHMAD PAIDI" : extAssignedBy);
                        } else if (!finalAlamatAssigned.contains("|") && !extAssignedBy.isEmpty()) {
                            finalAlamatAssigned = finalAlamatAssigned + " | " + extAssignedBy;
                        }
                    }

                    // Save screenshot to Gallery (Pictures/AdingWFM) so it's also available for HebitPro upload
                    String savedPath = saveBitmapToGallery(activity, croppedBitmap, captionTarget);

                    sendWfmScreenshotAndTaskToTelegram(
                            activity,
                            croppedBitmap,
                            captionTarget,
                            finalWoOrAo,
                            teknisiInfo,
                            finalPelanggan,
                            finalHpKoord,
                            finalAlamatAssigned,
                            savedPath,
                            (success, msg) -> {
                                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                                if (success && captureDialog.isShowing()) {
                                    captureDialog.dismiss();
                                } else {
                                    tvStatus.setText(msg);
                                }
                            }
                    );
                });
            };

            btnCaptureNow.setOnClickListener(v -> performScreenshotAndSend.run());
            btnClose.setOnClickListener(v -> captureDialog.dismiss());

            flowWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    CookieManager.getInstance().flush();
                    if (url == null) return;

                    if (url.contains("/unauthorize")) {
                        tvStatus.setText("🔐 Mengalihkan ke halaman Login FLOW...");
                        view.loadUrl(FLOW_LOGIN_URL);
                        return;
                    }

                    if (url.contains("/otp")) {
                        tvStatus.setText("📲 Masukkan kode OTP FLOW (hanya perlu 1x, sesi akan tersimpan)");
                        injectFlowAutoLoginScript(view, flowUser, flowPass, false);
                        return;
                    }

                    if (url.endsWith("flow.telkomakses.co.id/") || url.endsWith("flow.telkomakses.co.id") || url.contains("/login")) {
                        tvStatus.setText("🔐 Login otomatis ke FLOW (" + flowUser + ")...");
                        injectFlowAutoLoginScript(view, flowUser, flowPass, true);
                        return;
                    }

                    if (url.contains("/home") || url.contains("/dashboard")) {
                        tvStatus.setText("✅ Login FLOW berhasil! Membuka halaman Workorder...");
                        view.loadUrl(FLOW_WORKORDER_URL);
                        return;
                    }

                    if (url.contains("/workorder")) {
                        tvStatus.setText("🔍 Mencari Workorder " + woOrAoNumber + " di FLOW...");
                        injectDesktopViewportForWfm(view);
                        injectFlowWorkorderSearch(view, woOrAoNumber);
                        pollForWorkorderResult(view, captureDialog, woOrAoNumber, autoCaptured, tvStatus, performScreenshotAndSend, 0);
                    }
                }
            });

            captureDialog.show();
            flowWebView.loadUrl(FLOW_WORKORDER_URL);
        });
    }

    private static void pollForWorkorderResult(
            WebView view,
            Dialog dialog,
            String woOrAoNumber,
            boolean[] autoCaptured,
            TextView tvStatus,
            Runnable performScreenshotAndSend,
            int attempt
    ) {
        if (!dialog.isShowing() || autoCaptured[0] || attempt > 10) {
            if (attempt > 10 && !autoCaptured[0] && dialog.isShowing()) {
                tvStatus.setText("ℹ️ Klik detail WO lalu tekan [📸 SS & Kirim]");
            }
            return;
        }
        mainHandler.postDelayed(() -> {
            if (!dialog.isShowing() || autoCaptured[0]) return;
            String safeTarget = escapeJs(woOrAoNumber);
            String checkJs = "(function(){" +
                    "var target = '" + safeTarget + "'.toLowerCase();" +
                    // If there is a search result table row with a detail/view button, click it first
                    "var rows = document.querySelectorAll('table tbody tr');" +
                    "for(var r=0; r<rows.length; r++){" +
                    "  var rText = (rows[r].innerText || '').toLowerCase();" +
                    "  if(rText.indexOf(target) !== -1){" +
                    "    var btn = rows[r].querySelector('a, button');" +
                    "    if(btn && !btn.dataset.adingRowClicked){" +
                    "      btn.dataset.adingRowClicked = '1';" +
                    "      btn.click();" +
                    "      return 'CLICKED_ROW';" +
                    "    }" +
                    "  }" +
                    "}" +
                    // Check if the Workorder detail card has populated values
                    "var inputs = document.querySelectorAll('input.form-control, textarea.form-control, .card input');" +
                    "for(var i=0; i<inputs.length; i++){" +
                    "  var val = (inputs[i].value || '').toLowerCase();" +
                    "  if(val && val.indexOf(target) !== -1 && (inputs[i].readOnly || inputs[i].disabled)){" +
                    "    return 'CARD_READY';" +
                    "  }" +
                    "}" +
                    // Also check if body text shows OSS Order ID + target WO
                    "var bodyTxt = (document.body.innerText || '');" +
                    "if(bodyTxt.toLowerCase().indexOf(target) !== -1 && bodyTxt.indexOf('OSS Order ID') !== -1){" +
                    "  var allReadonly = document.querySelectorAll('input[readonly], input[disabled]');" +
                    "  for(var j=0; j<allReadonly.length; j++){" +
                    "    if((allReadonly[j].value || '').trim().length > 3) return 'CARD_READY';" +
                    "  }" +
                    "}" +
                    "return 'WAITING';" +
                    "})();";
            view.evaluateJavascript(checkJs, result -> {
                if (result != null && result.contains("CARD_READY") && !autoCaptured[0]) {
                    autoCaptured[0] = true;
                    tvStatus.setText("✅ Kartu Workorder ditemukan! Mengambil SS otomatis...");
                    mainHandler.postDelayed(performScreenshotAndSend, 900);
                } else {
                    pollForWorkorderResult(view, dialog, woOrAoNumber, autoCaptured, tvStatus, performScreenshotAndSend, attempt + 1);
                }
            });
        }, 1300);
    }

    private static void injectFlowWorkorderSearch(WebView view, String woNumber) {
        String safeWo = escapeJs(woNumber);
        String js = "(function(){" +
                "var inputs = document.querySelectorAll('input[type=\"search\"], input[name*=\"workorder\" i], input[id*=\"workorder\" i], input[name*=\"search\" i], input[id*=\"search\" i], input[placeholder*=\"search\" i], input[placeholder*=\"cari\" i], input[placeholder*=\"workorder\" i], input[placeholder*=\"wo\" i], input.form-control');" +
                "for(var i=0; i<inputs.length; i++){" +
                "  var el = inputs[i];" +
                "  if(el.type === 'hidden' || el.type === 'password' || el.readOnly || el.disabled) continue;" +
                "  if(!el.dataset.adingSearched){" +
                "    el.dataset.adingSearched = '1';" +
                "    el.focus();" +
                "    el.value = '" + safeWo + "';" +
                "    el.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    el.dispatchEvent(new Event('change', {bubbles:true}));" +
                "    el.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));" +
                "    el.dispatchEvent(new KeyboardEvent('keypress', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));" +
                "    el.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));" +
                "    var container = el.closest('form') || el.parentElement.parentElement || document;" +
                "    var submitBtn = container.querySelector('button[type=\"submit\"], input[type=\"submit\"], button.btn-danger, button.btn-primary, button[id*=\"search\" i], button[class*=\"search\" i]');" +
                "    if(submitBtn) submitBtn.click();" +
                "    break;" +
                "  }" +
                "}" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private interface WfmExtractionCallback {
        void onExtracted(Bitmap croppedBitmap, JSONObject extractedData);
    }

    /**
     * Extracts Workorder fields (Workorder, SC Order No / AO, Service Number, Contact Number,
     * Customer Name, Service Address, Assigned By) and the bounding rect of the Workorder card,
     * then captures and crops the WebView bitmap to the Workorder card.
     */
    private static void extractWfmCardDataAndCrop(WebView webView, String fallbackWo, WfmExtractionCallback callback) {
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
                "var ao = '';" +
                "if(sc){" +
                "  var m = sc.match(/(AO[a-zA-Z0-9_-]+)/);" +
                "  ao = m ? m[1] : sc;" +
                "}" +
                "var srv = getValByLabel('Service Number');" +
                "var contact = getValByLabel('Contact Number');" +
                "var cust = getValByLabel('Customer Name');" +
                "var addr = getValByLabel('Service Address');" +
                "var assigned = localStorage.getItem('name') || 'AHMAD PAIDI';" +
                // Find the Workorder card container to crop cleanly
                "var cardEl = null;" +
                "var headers = document.querySelectorAll('.card-header, .bg-danger, h5, h6, div');" +
                "for(var j=0; j<headers.length; j++){" +
                "  var ht = (headers[j].innerText || '').trim();" +
                "  if(ht === 'Workorder'){" +
                "    cardEl = headers[j].closest('.card') || headers[j].parentElement;" +
                "    if(cardEl) break;" +
                "  }" +
                "}" +
                "var rectObj = null;" +
                "if(cardEl){" +
                "  cardEl.scrollIntoView({block:'start', inline:'nearest'});" +
                "  var r = cardEl.getBoundingClientRect();" +
                "  rectObj = {left:r.left, top:r.top, width:r.width, height:r.height, vw:window.innerWidth, vh:window.innerHeight};" +
                "}" +
                "return JSON.stringify({wo:wo, sc:sc, ao:ao, service_number:srv, contact_number:contact, customer_name:cust, service_address:addr, assigned_by:assigned, rect:rectObj});" +
                "})();";

        webView.evaluateJavascript(js, rawJson -> {
            JSONObject extracted = null;
            try {
                if (rawJson != null && !rawJson.equals("null")) {
                    // Unescape string returned by evaluateJavascript
                    String clean = rawJson;
                    if (clean.startsWith("\"") && clean.endsWith("\"")) {
                        clean = new org.json.JSONTokener(rawJson).nextValue().toString();
                    }
                    extracted = new JSONObject(clean);
                }
            } catch (Exception ignored) {
            }

            final JSONObject finalExtracted = extracted;
            mainHandler.postDelayed(() -> {
                captureWebViewBitmap(webView, fullBitmap -> {
                    if (fullBitmap == null) {
                        callback.onExtracted(null, finalExtracted);
                        return;
                    }
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
                });
            }, 350);
        });
    }

    private interface BitmapCallback {
        void onBitmapReady(Bitmap bitmap);
    }

    private static void captureWebViewBitmap(WebView webView, BitmapCallback callback) {
        try {
            int width = webView.getWidth();
            int height = webView.getHeight();
            if (width <= 0 || height <= 0) {
                callback.onBitmapReady(null);
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            callback.onBitmapReady(bitmap);
        } catch (Exception e) {
            callback.onBitmapReady(null);
        }
    }

    /**
     * Saves the captured WFM Workorder screenshot into Pictures/AdingWFM so it's saved on device
     * and ready to upload in HebitPro or share anytime.
     */
    private static String saveBitmapToGallery(Context context, Bitmap bitmap, String woOrAo) {
        try {
            String safeName = (woOrAo != null ? woOrAo : "WO").replaceAll("[^a-zA-Z0-9_-]", "_");
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
                return "Pictures/AdingWFM/" + fileName;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    public static void sendWfmScreenshotAndTaskToTelegram(
            Context context,
            Bitmap screenshot,
            String captionWoOrAo,
            String fullAoWoLine,
            String teknisiInfo,
            String pelangganInfo,
            String hpAndKoordinat,
            String alamatAndAssignedBy,
            String savedGalleryPath,
            ResultCallback callback
    ) {
        SharedPreferences prefs = getPrefs(context);
        String botToken = prefs.getString(KEY_TG_TOKEN, "");
        String chatId = prefs.getString(KEY_TG_CHAT_ID, "");
        String threadId = prefs.getString(KEY_TG_THREAD_ID, "");

        // Parse composite fields
        String namaPelanggan = pelangganInfo;
        String idPelanggan = "-";
        if (pelangganInfo != null && pelangganInfo.contains("|")) {
            String[] parts = pelangganInfo.split("\\|", 2);
            namaPelanggan = parts[0].trim();
            idPelanggan = parts[1].trim();
        }

        String noHp = "";
        String koordinat = "";
        if (hpAndKoordinat != null && hpAndKoordinat.contains("|")) {
            String[] parts = hpAndKoordinat.split("\\|", 2);
            noHp = parts[0].trim();
            koordinat = parts[1].trim();
        } else if (hpAndKoordinat != null) {
            noHp = hpAndKoordinat.trim();
        }

        String alamat = alamatAndAssignedBy;
        String assignedBy = "AHMAD PAIDI";
        if (alamatAndAssignedBy != null && alamatAndAssignedBy.contains("|")) {
            String[] parts = alamatAndAssignedBy.split("\\|", 2);
            alamat = parts[0].trim();
            assignedBy = parts[1].trim();
        }

        String defaultBookingDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        final String fTeknisi = (teknisiInfo != null && !teknisiInfo.isEmpty()) ? teknisiInfo : "TEKNISI";
        final String fNamaPel = (namaPelanggan != null && !namaPelanggan.isEmpty()) ? namaPelanggan : "-";
        final String fIdPel = idPelanggan;
        final String fNoHp = noHp;
        final String fKoord = koordinat;
        final String fAlamat = (alamat != null && !alamat.isEmpty()) ? alamat : "-";
        final String fAssignedBy = assignedBy;
        final String fAoWo = (fullAoWoLine != null && !fullAoWoLine.isEmpty()) ? fullAoWoLine : captionWoOrAo;

        // Trigger Native Android Notification on the device immediately
        new AdingNotificationBridge(context).notifyTaskAssigned(
                fAoWo,
                fTeknisi,
                fNamaPel,
                fIdPel,
                "-",
                "-",
                fAlamat,
                fNoHp,
                defaultBookingDate,
                "Manja On Track",
                fKoord,
                "-",
                fAssignedBy
        );

        if (botToken.isEmpty() || chatId.isEmpty()) {
            String extra = (savedGalleryPath != null && !savedGalleryPath.isEmpty())
                    ? " Screenshot disimpan di " + savedGalleryPath
                    : "";
            mainHandler.post(() -> callback.onResult(true, "✅ SS WFM berhasil diambil & notifikasi tampil!" + extra));
            return;
        }

        executor.execute(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                screenshot.compress(Bitmap.CompressFormat.PNG, 92, bos);
                byte[] pngBytes = bos.toByteArray();

                // 1. Send WFM Screenshot Photo via Telegram Bot API sendPhoto
                boolean photoOk = uploadPhotoToTelegram(botToken, chatId, threadId, pngBytes, "SS WFM - Simple: " + captionWoOrAo);

                // 2. Send Formatted Task Assigned Message via Telegram Bot API sendMessage
                String cleanPhone = fNoHp.replaceAll("[^0-9]", "");
                if (cleanPhone.startsWith("0")) {
                    cleanPhone = "62" + cleanPhone.substring(1);
                }
                String waLink = !cleanPhone.isEmpty() ? "https://wa.me/" + cleanPhone : "https://wa.me/";
                String mapsLink = !fKoord.isEmpty()
                        ? "https://www.google.com/maps?q=" + URLEncoder.encode(fKoord.replace(" ", ""), "UTF-8")
                        : "https://www.google.com/maps";

                String techHeader = fTeknisi.contains("(") ? fTeknisi.substring(0, fTeknisi.indexOf("(")).trim() : fTeknisi;
                String htmlMsg = "<b>" + escapeHtml(techHeader) + "</b>\n"
                        + "📬 <b>TASK ASSIGNED BARU</b> 📬\n\n"
                        + "▪️ <b>AO/SC | WO:</b> " + escapeHtml(fAoWo) + "\n"
                        + "▪️ <b>Teknisi:</b> " + escapeHtml(fTeknisi) + "\n"
                        + "▪️ <b>Nama Pelanggan:</b> " + escapeHtml(fNamaPel) + "\n"
                        + "▪️ <b>Id Pelanggan :</b> " + escapeHtml(fIdPel) + "\n"
                        + "▪️ <b>ODP:</b> -\n"
                        + "▪️ <b>Package:</b> -\n"
                        + "▪️ <b>Alamat:</b> " + escapeHtml(fAlamat) + "\n"
                        + "▪️ <b>No HP:</b> " + escapeHtml(cleanPhone.isEmpty() ? "-" : cleanPhone) + "\n"
                        + "👉 <a href=\"" + waLink + "\">Kirim WhatsApp (Chat Pelanggan)</a>\n"
                        + "🗓️ <b>Booking Date:</b> " + escapeHtml(defaultBookingDate) + "\n"
                        + "📅 <b>Status Booking:</b> <u>Manja On Track</u>\n"
                        + "<b>Koordinat:</b> " + escapeHtml(fKoord.isEmpty() ? "-" : fKoord) + "\n"
                        + "📍 <a href=\"" + mapsLink + "\">Buka di Google Maps</a>\n"
                        + "📝 <b>Catatan:</b> -\n"
                        + "👷 <b>Assigned By:</b> " + escapeHtml(fAssignedBy);

                boolean msgOk = sendHtmlMessageToTelegram(botToken, chatId, threadId, htmlMsg);

                mainHandler.post(() -> {
                    if (photoOk && msgOk) {
                        callback.onResult(true, "✅ SS WFM & Notifikasi Task berhasil dikirim ke Channel Telegram Teknisi!");
                    } else {
                        callback.onResult(false, "⚠️ Gagal kirim ke Telegram. Periksa Bot Token & Chat ID Channel.");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(false, "Gagal mengirim ke Telegram: " + e.getMessage()));
            }
        });
    }

    private static boolean uploadPhotoToTelegram(String token, String chatId, String threadId, byte[] pngBytes, String caption) {
        String boundary = "----AdingBoundary" + System.currentTimeMillis();
        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendPhoto");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            writeFormField(out, boundary, "chat_id", chatId);
            if (threadId != null && !threadId.isEmpty()) {
                writeFormField(out, boundary, "message_thread_id", threadId);
            }
            writeFormField(out, boundary, "caption", caption);

            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"wfm_workorder.png\"\r\n");
            out.writeBytes("Content-Type: image/png\r\n\r\n");
            out.write(pngBytes);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeFormField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static boolean sendHtmlMessageToTelegram(String token, String chatId, String threadId, String htmlText) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            StringBuilder body = new StringBuilder();
            body.append("chat_id=").append(URLEncoder.encode(chatId, "UTF-8"));
            if (threadId != null && !threadId.isEmpty()) {
                body.append("&message_thread_id=").append(URLEncoder.encode(threadId, "UTF-8"));
            }
            body.append("&parse_mode=HTML&disable_web_page_preview=true");
            body.append("&text=").append(URLEncoder.encode(htmlText, "UTF-8"));

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(bytes);
            conn.getOutputStream().close();

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
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

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
