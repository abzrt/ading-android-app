package id.web.ading;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitHubAutoUpdater {

    private static final String VERSION_PROPS_URL =
            "https://raw.githubusercontent.com/abzrt/ading-android-app/main/app/version.properties";
    private static final String APK_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/abzrt/ading-android-app/main/Ading%20Mobile.apk";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static boolean isChecking = false;

    public static void checkForUpdates(Activity activity, boolean manualCheck) {
        if (activity == null || activity.isFinishing() || isChecking) return;
        isChecking = true;

        if (manualCheck) {
            Toast.makeText(activity, "🔍 Memeriksa update terbaru di GitHub...", Toast.LENGTH_SHORT).show();
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                int currentCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? (int) pInfo.getLongVersionCode()
                        : pInfo.versionCode;
                String currentName = pInfo.versionName != null ? pInfo.versionName : ("1.0." + currentCode);

                URL url = new URL(VERSION_PROPS_URL + "?_t=" + System.currentTimeMillis());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);

                if (conn.getResponseCode() != 200) {
                    if (manualCheck) {
                        mainHandler.post(() -> Toast.makeText(activity, "⚠️ Gagal mengecek update GitHub.", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                Properties props = new Properties();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    props.load(br);
                }

                int remoteMajor = Integer.parseInt(props.getProperty("VERSION_MAJOR", "1").trim());
                int remoteMinor = Integer.parseInt(props.getProperty("VERSION_MINOR", "0").trim());
                int remotePatch = Integer.parseInt(props.getProperty("VERSION_PATCH", "0").trim());
                String remoteName = remoteMajor + "." + remoteMinor + "." + remotePatch;

                if (remotePatch > currentCode) {
                    mainHandler.post(() -> showUpdateAvailableDialog(activity, currentName, remoteName));
                } else if (manualCheck) {
                    mainHandler.post(() -> Toast.makeText(activity, "✅ Ading Mobile sudah versi terbaru (v" + currentName + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (manualCheck) {
                    mainHandler.post(() -> Toast.makeText(activity, "⚠️ Tidak dapat terhubung ke GitHub untuk cek update.", Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (conn != null) conn.disconnect();
                isChecking = false;
            }
        });
    }

    private static void showUpdateAvailableDialog(Activity activity, String currentName, String remoteName) {
        if (activity == null || activity.isFinishing()) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 20);
            container.setPadding(pad, pad, pad, pad);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dp(activity, 16));
            cardBg.setStroke(dp(activity, 1), Color.parseColor("#E5E7EB"));
            container.setBackground(cardBg);

            // ADING Pill Badge
            TextView tvBadge = new TextView(activity);
            tvBadge.setText("ADING • AUTO UPDATE");
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
            container.addView(tvBadge);

            TextView tvTitle = new TextView(activity);
            tvTitle.setText("Update Tersedia: v" + remoteName);
            tvTitle.setTextSize(17f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.parseColor("#111827"));
            container.addView(tvTitle);

            TextView tvMsg = new TextView(activity);
            tvMsg.setText("Unduh dan pasang versi terbaru dari GitHub untuk mendapatkan fitur & perbaikan terbaru.");
            tvMsg.setTextSize(13f);
            tvMsg.setTextColor(Color.parseColor("#6B7280"));
            tvMsg.setLineSpacing(dp(activity, 2), 1f);
            tvMsg.setPadding(0, dp(activity, 6), 0, dp(activity, 14));
            container.addView(tvMsg);

            // Version info box (#F9FAFB fill, 12dp radius, 1dp #D1D5DB border)
            TextView tvVerBox = new TextView(activity);
            tvVerBox.setText("Versi terpasang : v" + currentName + "\nVersi terbaru   : v" + remoteName);
            tvVerBox.setTextSize(13f);
            tvVerBox.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvVerBox.setTextColor(Color.parseColor("#111827"));
            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(Color.parseColor("#F9FAFB"));
            boxBg.setCornerRadius(dp(activity, 12));
            boxBg.setStroke(dp(activity, 1), Color.parseColor("#D1D5DB"));
            tvVerBox.setBackground(boxBg);
            tvVerBox.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
            container.addView(tvVerBox);

            // Subtle Divider (#F3F4F6)
            View divider = new View(activity);
            divider.setBackgroundColor(Color.parseColor("#F3F4F6"));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
            divLp.setMargins(0, dp(activity, 18), 0, dp(activity, 14));
            divider.setLayoutParams(divLp);
            container.addView(divider);

            LinearLayout btnRow = new LinearLayout(activity);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.END);

            Button btnLater = new Button(activity);
            btnLater.setText("Nanti");
            btnLater.setTextSize(13.5f);
            btnLater.setTypeface(null, Typeface.BOLD);
            btnLater.setTextColor(Color.parseColor("#374151"));
            btnLater.setAllCaps(false);
            GradientDrawable laterBg = new GradientDrawable();
            laterBg.setColor(Color.WHITE);
            laterBg.setCornerRadius(dp(activity, 10));
            laterBg.setStroke(dp(activity, 1), Color.parseColor("#D1D5DB"));
            btnLater.setBackground(laterBg);
            btnLater.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
            btnLater.setMinHeight(0);
            btnLater.setMinimumHeight(dp(activity, 42));
            LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            laterLp.setMargins(0, 0, dp(activity, 10), 0);
            btnLater.setLayoutParams(laterLp);

            Button btnUpdate = new Button(activity);
            btnUpdate.setText("Update Sekarang");
            btnUpdate.setTextSize(13.5f);
            btnUpdate.setTextColor(Color.WHITE);
            btnUpdate.setAllCaps(false);
            btnUpdate.setTypeface(null, Typeface.BOLD);
            GradientDrawable updateBg = new GradientDrawable();
            updateBg.setColor(Color.parseColor("#1E3A8A"));
            updateBg.setCornerRadius(dp(activity, 10));
            btnUpdate.setBackground(updateBg);
            btnUpdate.setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10));
            btnUpdate.setMinHeight(0);
            btnUpdate.setMinimumHeight(dp(activity, 42));

            btnRow.addView(btnLater);
            btnRow.addView(btnUpdate);
            container.addView(btnRow);

            builder.setView(container);
            builder.setCancelable(true);
            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            btnLater.setOnClickListener(v -> dialog.dismiss());
            btnUpdate.setOnClickListener(v -> {
                dialog.dismiss();
                downloadAndInstallApk(activity, remoteName);
            });

            dialog.show();
        } catch (Exception ignored) {
        }
    }

    private static void downloadAndInstallApk(Activity activity, String remoteName) {
        if (activity == null || activity.isFinishing()) return;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 20);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable progCardBg = new GradientDrawable();
        progCardBg.setColor(Color.WHITE);
        progCardBg.setCornerRadius(dp(activity, 16));
        progCardBg.setStroke(dp(activity, 1), Color.parseColor("#E5E7EB"));
        root.setBackground(progCardBg);

        TextView tvBadge = new TextView(activity);
        tvBadge.setText("ADING • MENGUNDUH");
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
        tvTitle.setText("Mengunduh Ading Mobile v" + remoteName);
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor("#111827"));
        root.addView(tvTitle);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#1E3A8A")));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#1E3A8A")));
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.setMargins(0, dp(activity, 14), 0, dp(activity, 8));
        progressBar.setLayoutParams(pbLp);
        root.addView(progressBar);

        TextView tvProgress = new TextView(activity);
        tvProgress.setText("Menghubungkan ke GitHub...");
        tvProgress.setTextSize(12.5f);
        tvProgress.setTextColor(Color.parseColor("#6B7280"));
        root.addView(tvProgress);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(false)
                .create();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        progressDialog.show();

        executor.execute(() -> {
            HttpURLConnection conn = null;
            File outFile = new File(activity.getCacheDir(), "ading_update.apk");
            try {
                URL url = new URL(APK_DOWNLOAD_URL + "?_t=" + System.currentTimeMillis());
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("HTTP " + code);
                }

                long totalBytes = conn.getContentLengthLong();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    long downloaded = 0;
                    int read;
                    int lastPercent = -1;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                        downloaded += read;
                        if (totalBytes > 0) {
                            int pct = (int) ((downloaded * 100L) / totalBytes);
                            if (pct != lastPercent) {
                                lastPercent = pct;
                                final int fPct = pct;
                                final String mbText = String.format(java.util.Locale.US, "%.2f / %.2f MB (%d%%)",
                                        downloaded / (1024f * 1024f), totalBytes / (1024f * 1024f), fPct);
                                mainHandler.post(() -> {
                                    progressBar.setIndeterminate(false);
                                    progressBar.setProgress(fPct);
                                    tvProgress.setText(mbText);
                                });
                            }
                        }
                    }
                    out.flush();
                }

                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    launchApkInstaller(activity, outFile);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(activity, "❌ Gagal mengunduh update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void launchApkInstaller(Activity activity, File apkFile) {
        if (activity == null || activity.isFinishing() || apkFile == null || !apkFile.exists()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Aktifkan izin 'Install unknown apps' untuk Ading Mobile, lalu klik Update lagi.", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())
                );
                activity.startActivity(settingsIntent);
                return;
            }

            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(activity, "❌ Gagal membuka installer APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static int dp(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }
}
