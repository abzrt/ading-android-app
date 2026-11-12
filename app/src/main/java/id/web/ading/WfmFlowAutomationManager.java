package id.web.ading;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WfmFlowAutomationManager {

    public static final String FLOW_WORKORDER_URL = "https://flow.telkomakses.co.id/workorder";
    public static final String HEBITPRO_URL = "https://hebitpro-kaltara.ops.web.id/";
    public static final String ADING_URL = "https://ading.web.id/";

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
                .putString(KEY_FLOW_USER, flowUser != null ? flowUser.trim() : "")
                .putString(KEY_FLOW_PASS, flowPass != null ? flowPass : "")
                .putString(KEY_HEBIT_USER, hebitUser != null ? hebitUser.trim() : "")
                .putString(KEY_HEBIT_PASS, hebitPass != null ? hebitPass : "")
                .putString(KEY_TG_TOKEN, tgBotToken != null ? tgBotToken.trim() : "")
                .putString(KEY_TG_CHAT_ID, tgChatId != null ? tgChatId.trim() : "")
                .putString(KEY_TG_THREAD_ID, tgThreadId != null ? tgThreadId.trim() : "")
                .apply();
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Auto-fill login credentials when on flow.telkomakses.co.id or hebitpro-kaltara.ops.web.id
     * and inject floating quick-access bridge button
     */
    public static void onPageLoaded(WebView webView, String url) {
        if (webView == null || url == null) return;
        Context context = webView.getContext();
        SharedPreferences prefs = getPrefs(context);

        if (url.contains("flow.telkomakses.co.id")) {
            String user = prefs.getString(KEY_FLOW_USER, "");
            String pass = prefs.getString(KEY_FLOW_PASS, "");
            if (!user.isEmpty() && !pass.isEmpty()) {
                injectAutoLoginScript(webView, user, pass);
            }
        } else if (url.contains("hebitpro-kaltara.ops.web.id")) {
            String user = prefs.getString(KEY_HEBIT_USER, "");
            String pass = prefs.getString(KEY_HEBIT_PASS, "");
            if (!user.isEmpty() && !pass.isEmpty()) {
                injectAutoLoginScript(webView, user, pass);
            }
            injectHebitProAutoHook(webView);
        }

        injectFloatingControlWidget(webView);
    }

    private static void injectAutoLoginScript(WebView webView, String username, String password) {
        String safeUser = escapeJs(username);
        String safePass = escapeJs(password);
        String js = "(function(){" +
                "var passInput = document.querySelector('input[type=\"password\"]');" +
                "if(!passInput || passInput.dataset.adingFilled) return;" +
                "var form = passInput.closest('form') || document;" +
                "var userInput = form.querySelector('input[name*=\"user\" i], input[name*=\"nik\" i], input[name*=\"email\" i], input[id*=\"user\" i], input[type=\"text\"], input[type=\"email\"]');" +
                "if(userInput && !userInput.value){" +
                "  userInput.value = '" + safeUser + "';" +
                "  userInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "  userInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "}" +
                "if(!passInput.value){" +
                "  passInput.dataset.adingFilled = '1';" +
                "  passInput.value = '" + safePass + "';" +
                "  passInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "  passInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "}" +
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
            tvSub.setText("Hubungkan akun FLOW Telkom Akses & HebitPro Kaltara untuk otomatis cari Workorder, Screenshot WFM, dan kirim ke Channel Telegram Teknisi.");
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
            addSectionHeader(container, activity, "📸 Auto Cari WO di FLOW & Kirim SS ke Telegram");
            EditText etWoNumber = createInput(activity, "Nomor Workorder / AO (contoh: WO068288713 / AOi426...)", "", false);
            container.addView(etWoNumber);

            EditText etTeknisi = createInput(activity, "Nama & NIK Teknisi (contoh: ARIFIN (16983839))", "", false);
            container.addView(etTeknisi);

            EditText etPelanggan = createInput(activity, "Nama & ID Pelanggan (contoh: SYAMSIDAR | 161502308272)", "", false);
            container.addView(etPelanggan);

            EditText etHpKoordinat = createInput(activity, "No HP | Koordinat (contoh: 6281254208532 | 4.144094, 117.653714)", "", false);
            container.addView(etHpKoordinat);

            EditText etAlamat = createInput(activity, "Alamat Pelanggan & Assigned By", "", false);
            container.addView(etAlamat);

            // Section 2: Credentials for FLOW & HebitPro
            addSectionHeader(container, activity, "🔐 Akun FLOW (flow.telkomakses.co.id)");
            EditText etFlowUser = createInput(activity, "Username / NIK FLOW", prefs.getString(KEY_FLOW_USER, ""), false);
            EditText etFlowPass = createInput(activity, "Password FLOW", prefs.getString(KEY_FLOW_PASS, ""), true);
            container.addView(etFlowUser);
            container.addView(etFlowPass);

            addSectionHeader(container, activity, "🔐 Akun HebitPro (hebitpro-kaltara.ops.web.id)");
            EditText etHebitUser = createInput(activity, "Username HebitPro", prefs.getString(KEY_HEBIT_USER, ""), false);
            EditText etHebitPass = createInput(activity, "Password HebitPro", prefs.getString(KEY_HEBIT_PASS, ""), true);
            container.addView(etHebitUser);
            container.addView(etHebitPass);

            addSectionHeader(container, activity, "✈️ Konfigurasi Bot & Channel Telegram Teknisi");
            EditText etTgToken = createInput(activity, "Telegram Bot Token (dari @BotFather / HebitPro)", prefs.getString(KEY_TG_TOKEN, ""), false);
            EditText etTgChatId = createInput(activity, "Chat ID Channel / Grup Teknisi (contoh: -100123456789)", prefs.getString(KEY_TG_CHAT_ID, ""), false);
            EditText etTgThreadId = createInput(activity, "Topic / Thread ID Teknisi (opsional, kosongkan jika tanpa topic)", prefs.getString(KEY_TG_THREAD_ID, ""), false);
            container.addView(etTgToken);
            container.addView(etTgChatId);
            container.addView(etTgThreadId);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(scrollView)
                    .setPositiveButton("🚀 Cari WO, SS & Kirim", null)
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
     * takes a crisp screenshot of the WFM Workorder page, and sends it + formatted Task Assigned notification to Telegram!
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
            String flowUser = prefs.getString(KEY_FLOW_USER, "");
            String flowPass = prefs.getString(KEY_FLOW_PASS, "");

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
            int pad = dp(activity, 12);
            topBar.setPadding(pad, pad, pad, pad);

            TextView tvStatus = new TextView(activity);
            tvStatus.setText("🔍 Mencari WO: " + woOrAoNumber + " di FLOW...");
            tvStatus.setTextColor(Color.WHITE);
            tvStatus.setTextSize(13f);
            tvStatus.setTypeface(null, Typeface.BOLD);
            topBar.addView(tvStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button btnCaptureNow = createSmallButton(activity, "📸 SS & Kirim Telegram", "#F9010F");
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
            // Request Desktop Site so WFM table renders full-width like PC screenshot
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(flowWebView, true);

            root.addView(flowWebView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            captureDialog.setContentView(root);

            final boolean[] autoCaptured = {false};

            Runnable performScreenshotAndSend = () -> {
                tvStatus.setText("📸 Mengambil Screenshot WFM & Mengirim ke Telegram...");
                captureWebViewBitmap(flowWebView, bitmap -> {
                    if (bitmap == null) {
                        Toast.makeText(activity, "Gagal mengambil screenshot WFM", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendWfmScreenshotAndTaskToTelegram(
                            activity,
                            bitmap,
                            woOrAoNumber,
                            teknisiInfo,
                            pelangganInfo,
                            hpAndKoordinat,
                            alamatAndAssignedBy,
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
                    if (!flowUser.isEmpty() && !flowPass.isEmpty()) {
                        injectAutoLoginScript(view, flowUser, flowPass);
                    }
                    // Auto-search the WO number on flow.telkomakses.co.id/workorder
                    injectFlowWorkorderSearch(view, woOrAoNumber);

                    // Check if workorder detail or result is visible, or allow 4.5s for AJAX table to finish loading
                    mainHandler.postDelayed(() -> {
                        if (!captureDialog.isShowing() || autoCaptured[0]) return;
                        view.evaluateJavascript(
                                "(function(){ return document.body.innerText.indexOf('" + escapeJs(woOrAoNumber) + "') !== -1 ? 'FOUND' : 'WAITING'; })();",
                                result -> {
                                    if (result != null && result.contains("FOUND") && !autoCaptured[0]) {
                                        autoCaptured[0] = true;
                                        tvStatus.setText("✅ Workorder ditemukan! Mengambil SS otomatis...");
                                        mainHandler.postDelayed(performScreenshotAndSend, 1200);
                                    } else {
                                        tvStatus.setText("ℹ️ Silakan klik detail WO lalu tekan [📸 SS & Kirim Telegram]");
                                    }
                                }
                        );
                    }, 3800);
                }
            });

            captureDialog.show();
            flowWebView.loadUrl(FLOW_WORKORDER_URL);
        });
    }

    private static void injectFlowWorkorderSearch(WebView view, String woNumber) {
        String safeWo = escapeJs(woNumber);
        String js = "(function(){" +
                "var inputs = document.querySelectorAll('input[type=\"search\"], input[name*=\"workorder\" i], input[name*=\"search\" i], input[placeholder*=\"search\" i], input[placeholder*=\"cari\" i], input[placeholder*=\"wo\" i], input.form-control');" +
                "for(var i=0; i<inputs.length; i++){" +
                "  var el = inputs[i];" +
                "  if(el.type === 'hidden' || el.type === 'password') continue;" +
                "  if(!el.dataset.adingSearched){" +
                "    el.dataset.adingSearched = '1';" +
                "    el.focus();" +
                "    el.value = '" + safeWo + "';" +
                "    el.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    el.dispatchEvent(new Event('change', {bubbles:true}));" +
                "    el.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', keyCode:13, which:13, bubbles:true}));" +
                "    el.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', keyCode:13, which:13, bubbles:true}));" +
                "    var form = el.closest('form');" +
                "    if(form){" +
                "      var submitBtn = form.querySelector('button[type=\"submit\"], input[type=\"submit\"], button.btn-primary, button.btn-danger');" +
                "      if(submitBtn) submitBtn.click();" +
                "    }" +
                "    break;" +
                "  }" +
                "}" +
                "})();";
        view.evaluateJavascript(js, null);
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

    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    public static void sendWfmScreenshotAndTaskToTelegram(
            Context context,
            Bitmap screenshot,
            String woOrAo,
            String teknisiInfo,
            String pelangganInfo,
            String hpAndKoordinat,
            String alamatAndAssignedBy,
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
        String assignedBy = "ADMIN / HELPDESK";
        if (alamatAndAssignedBy != null && alamatAndAssignedBy.contains("|")) {
            String[] parts = alamatAndAssignedBy.split("\\|", 2);
            alamat = parts[0].trim();
            assignedBy = parts[1].trim();
        }

        final String fTeknisi = (teknisiInfo != null && !teknisiInfo.isEmpty()) ? teknisiInfo : "TEKNISI";
        final String fNamaPel = (namaPelanggan != null && !namaPelanggan.isEmpty()) ? namaPelanggan : "-";
        final String fIdPel = idPelanggan;
        final String fNoHp = noHp;
        final String fKoord = koordinat;
        final String fAlamat = (alamat != null && !alamat.isEmpty()) ? alamat : "-";
        final String fAssignedBy = assignedBy;

        // Also trigger Native Android Notification on the device immediately
        new AdingNotificationBridge(context).notifyTaskAssigned(
                woOrAo,
                fTeknisi,
                fNamaPel,
                fIdPel,
                "-",
                "-",
                fAlamat,
                fNoHp,
                "-",
                "Manja On Track",
                fKoord,
                "-",
                fAssignedBy
        );

        if (botToken.isEmpty() || chatId.isEmpty()) {
            mainHandler.post(() -> callback.onResult(true, "✅ Notifikasi lokal tampil! (Isi Bot Token & Chat ID Telegram di pengaturan untuk kirim ke Telegram)"));
            return;
        }

        executor.execute(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                screenshot.compress(Bitmap.CompressFormat.PNG, 92, bos);
                byte[] pngBytes = bos.toByteArray();

                // 1. Send WFM Screenshot Photo via Telegram Bot API sendPhoto
                boolean photoOk = uploadPhotoToTelegram(botToken, chatId, threadId, pngBytes, "SS WFM - Simple: " + woOrAo);

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
                        + "▪️ <b>AO/SC | WO:</b> " + escapeHtml(woOrAo) + "\n"
                        + "▪️ <b>Teknisi:</b> " + escapeHtml(fTeknisi) + "\n"
                        + "▪️ <b>Nama Pelanggan:</b> " + escapeHtml(fNamaPel) + "\n"
                        + "▪️ <b>Id Pelanggan :</b> " + escapeHtml(fIdPel) + "\n"
                        + "▪️ <b>ODP:</b> -\n"
                        + "▪️ <b>Package:</b> -\n"
                        + "▪️ <b>Alamat:</b> " + escapeHtml(fAlamat) + "\n"
                        + "▪️ <b>No HP:</b> " + escapeHtml(cleanPhone.isEmpty() ? "-" : cleanPhone) + "\n"
                        + "👉 <a href=\"" + waLink + "\">Kirim WhatsApp (Chat Pelanggan)</a>\n"
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
