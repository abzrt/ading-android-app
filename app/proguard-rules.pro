# =================================================================
# Proguard / R8 Rules for ADING (Google Play Store Release)
# =================================================================

# Keep AndroidX and Material Components
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep WebView and JavaScript interfaces safe from obfuscation
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebChromeClient and WebViewClient subclasses
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, android.webkit.WebResourceRequest);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, int);
    public boolean *(android.webkit.WebView, android.webkit.ValueCallback, android.webkit.WebChromeClient$FileChooserParams);
}

# Preserve Line Numbers for Crash Reporting in Google Play Console
-keepattributes SourceFile,LineNumberTable

# Preserve Annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip debug log outputs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
