package id.web.ading;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2200;
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private ObjectAnimator progressAnimator;

    private final Runnable splashRunnable = () -> {
        if (!isFinishing() && !isDestroyed()) {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View rootLayout = findViewById(R.id.splashRootLayout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return windowInsets;
            });
        }

        setupVersionInfo();
        startSplashAnimations();

        splashHandler.postDelayed(splashRunnable, SPLASH_DELAY_MS);
    }

    private void setupVersionInfo() {
        TextView tvSplashVersion = findViewById(R.id.tvSplashVersion);
        if (tvSplashVersion != null) {
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String versionName = pInfo.versionName;
                long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
                tvSplashVersion.setText(String.format(Locale.US, "Release v%s • Build #%d", versionName, versionCode));
            } catch (PackageManager.NameNotFoundException e) {
                tvSplashVersion.setText("Release v1.0.2 • Build #2");
            }
        }
    }

    private void startSplashAnimations() {
        ImageView ivMascot = findViewById(R.id.ivSplashMascot);
        View textContainer = findViewById(R.id.splashTextContainer);
        View footerContainer = findViewById(R.id.splashFooterContainer);
        ProgressBar progressBar = findViewById(R.id.splashProgressBar);

        if (ivMascot != null) {
            ivMascot.setAlpha(0f);
            ivMascot.setScaleX(0.75f);
            ivMascot.setScaleY(0.75f);
            ivMascot.setTranslationY(24f);
            ivMascot.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(750)
                    .setInterpolator(new OvershootInterpolator(1.15f))
                    .start();
        }

        if (textContainer != null) {
            textContainer.setAlpha(0f);
            textContainer.setTranslationY(32f);
            textContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(180)
                    .setDuration(650)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (footerContainer != null) {
            footerContainer.setAlpha(0f);
            footerContainer.animate()
                    .alpha(1f)
                    .setStartDelay(300)
                    .setDuration(600)
                    .start();
        }

        if (progressBar != null) {
            progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
            progressAnimator.setDuration(SPLASH_DELAY_MS - 150);
            progressAnimator.setInterpolator(new DecelerateInterpolator());
            progressAnimator.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        splashHandler.removeCallbacks(splashRunnable);
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
    }
}
