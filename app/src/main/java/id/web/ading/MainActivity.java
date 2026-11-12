package id.web.ading;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String TARGET_URL = "https://ading.web.id/";
    private static final String ALLOWED_DOMAIN = "ading.web.id";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private LinearLayout layoutError;
    private Button btnRetry;

    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private File tempPhotoFile;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;

    private long backPressedTime = 0;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private PermissionRequest pendingWebPermissionRequest;
    private boolean pendingFileChooserAfterCamera = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupLaunchers();
        setupBackNavigation();
        setupWebView();
        checkNotificationPermission();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            loadApp();
        }
    }

    private void initViews() {
        View rootLayout = findViewById(R.id.rootLayout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                int bottomPadding = Math.max(bars.bottom, ime.bottom);
                v.setPadding(bars.left, bars.top, bars.right, bottomPadding);
                return windowInsets;
            });
        }

        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        layoutError = findViewById(R.id.layoutError);
        btnRetry = findViewById(R.id.btnRetry);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                webView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
                showErrorLayout();
            }
        });

        btnRetry.setOnClickListener(v -> loadApp());
    }

    private void setupLaunchers() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK) {
                        if (result.getData() == null || (result.getData().getData() == null && result.getData().getClipData() == null)) {
                            if (cameraImageUri != null && tempPhotoFile != null && tempPhotoFile.length() > 0) {
                                results = new Uri[]{cameraImageUri};
                            }
                        } else if (result.getData().getClipData() != null) {
                            ClipData clipData = result.getData().getClipData();
                            int count = clipData.getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = clipData.getItemAt(i).getUri();
                            }
                        } else {
                            String dataString = result.getData().getDataString();
                            if (dataString != null) {
                                results = new Uri[]{Uri.parse(dataString)};
                            }
                        }
                    } else {
                        // User cancelled, clean up unused temp file
                        cleanupTempPhoto();
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
        );

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = false;
                    for (Boolean val : permissions.values()) {
                        if (Boolean.TRUE.equals(val)) {
                            granted = true;
                            break;
                        }
                    }
                    if (geolocationCallback != null && geolocationOrigin != null) {
                        geolocationCallback.invoke(geolocationOrigin, granted, false);
                    }
                }
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    // Handled notification permission result
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (pendingWebPermissionRequest != null) {
                        if (isGranted) {
                            pendingWebPermissionRequest.grant(pendingWebPermissionRequest.getResources());
                        } else {
                            pendingWebPermissionRequest.deny();
                        }
                        pendingWebPermissionRequest = null;
                    }
                    if (pendingFileChooserAfterCamera) {
                        pendingFileChooserAfterCamera = false;
                        launchFileChooserIntent();
                    }
                }
        );
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutError.getVisibility() == View.VISIBLE) {
                    finish();
                    return;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finish();
                    } else {
                        Toast.makeText(MainActivity.this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show();
                        backPressedTime = System.currentTimeMillis();
                    }
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Standard Web Capabilities
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Security Hardening for Google Play Store Policy Compliance:
        // 1. Disable local file access to mitigate CWE-200 / file scheme injection vulnerabilities
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        // 2. Enable Google Safe Browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        // 3. Strict Mixed Content Policy: Prohibit loading insecure HTTP assets inside HTTPS
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Secure Cookie Handling
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Register Native Technician Notification Channel Bridge
        webView.addJavascriptInterface(new AdingNotificationBridge(this), "AdingAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return handleUrlLoading(view, uri);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
                hideErrorLayout();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                AdingNotificationBridge.injectWebNotificationHook(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    if (!isNetworkAvailable()) {
                        showErrorLayout();
                    }
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // GOOGLE PLAY STORE SECURITY COMPLIANCE:
                // NEVER call handler.proceed()! Doing so violates Google Play Developer Policy and triggers automatic rejection.
                handler.cancel();
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Koneksi tidak aman (SSL Error). Akses ditolak demi keamanan.", Toast.LENGTH_LONG).show();
                showErrorLayout();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else if (progressBar.getVisibility() == View.GONE) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingFileChooserAfterCamera = true;
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    return true;
                }

                return launchFileChooserIntent();
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                String[] resources = request.getResources();
                boolean needsCamera = false;
                for (String resource : resources) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                        needsCamera = true;
                        break;
                    }
                }
                if (needsCamera) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(resources);
                    } else {
                        pendingWebPermissionRequest = request;
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    }
                } else {
                    request.deny();
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geolocationOrigin = origin;
                geolocationCallback = callback;

                boolean hasFine = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean hasCoarse = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

                if (!hasFine && !hasCoarse) {
                    locationPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                } else {
                    callback.invoke(origin, true, false);
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) {
                        request.addRequestHeader("cookie", cookies);
                    }
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Mengunduh berkas laporan...");
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    request.setTitle(fileName);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(MainActivity.this, "Mengunduh: " + fileName, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Gagal memulai unduhan", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean handleUrlLoading(WebView view, Uri uri) {
        String url = uri.toString();
        String host = uri.getHost();

        // 1. Keep internal dashboard navigation inside WebView
        if (host != null && (host.equalsIgnoreCase(ALLOWED_DOMAIN) || host.toLowerCase(Locale.ROOT).endsWith("." + ALLOWED_DOMAIN))) {
            return false;
        }

        // 2. Google Play Security: Intent Redirection & Hijacking Protection
        if (url.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                // Sanitize intent to prevent launch of private components or code execution
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);

                PackageManager pm = getPackageManager();
                if (intent.resolveActivity(pm) != null) {
                    startActivity(intent);
                } else {
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null && (fallbackUrl.startsWith("https://") || fallbackUrl.startsWith("http://"))) {
                        view.loadUrl(fallbackUrl);
                    }
                }
                return true;
            } catch (Exception e) {
                return true;
            }
        }

        // 3. Known Safe Communication Protocols
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("whatsapp:") || url.startsWith("geo:")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Aplikasi untuk membuka tautan ini tidak ditemukan", Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        // 4. External Web Links (Open in user's default browser)
        if (url.startsWith("https://") || url.startsWith("http://")) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(browserIntent);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    private boolean launchFileChooserIntent() {
        Intent takePictureIntent = null;
        boolean canUseCamera = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (canUseCamera) {
            takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try {
                tempPhotoFile = createImageFile();
                cameraImageUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        tempPhotoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            } catch (IOException ex) {
                tempPhotoFile = null;
                cameraImageUri = null;
                takePictureIntent = null;
            }
        }

        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentSelectionIntent.setType("*/*");
        contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        Intent[] intentArray;
        if (takePictureIntent != null && cameraImageUri != null) {
            intentArray = new Intent[]{takePictureIntent};
        } else {
            intentArray = new Intent[0];
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Pilih Berkas atau Foto");
        if (intentArray.length > 0) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
        }

        try {
            fileChooserLauncher.launch(chooserIntent);
        } catch (ActivityNotFoundException e) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
                MainActivity.this.filePathCallback = null;
            }
            Toast.makeText(MainActivity.this, "Tidak ada aplikasi pemilih berkas yang tersedia", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "ADING_" + timeStamp + "_";
        File storageDir = getCacheDir();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void cleanupTempPhoto() {
        if (tempPhotoFile != null && tempPhotoFile.exists() && tempPhotoFile.length() == 0) {
            tempPhotoFile.delete();
        }
    }

    private void loadApp() {
        if (isNetworkAvailable()) {
            hideErrorLayout();
            webView.loadUrl(TARGET_URL);
        } else {
            showErrorLayout();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private void showErrorLayout() {
        layoutError.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void hideErrorLayout() {
        layoutError.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }
}
