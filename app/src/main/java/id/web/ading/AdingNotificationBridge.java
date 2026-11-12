package id.web.ading;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class AdingNotificationBridge {

    private static final String PREFS_NAME = "ading_tech_prefs";
    private static final String KEY_TECH_NIK = "tech_nik";
    private static final String KEY_TECH_NAME = "tech_name";
    private static final String DEFAULT_CHANNEL_ID = "ading_task_assigned_channel";
    private static final String DEFAULT_CHANNEL_NAME = "Task Assigned Teknisi";

    private final Context context;

    public AdingNotificationBridge(Context context) {
        this.context = context.getApplicationContext();
        ensureNotificationChannel(DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_NAME);
    }

    private void ensureNotificationChannel(String channelId, String channelName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(channelId) == null) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Pemberitahuan Task Assigned Baru untuk Teknisi Ading Mobile");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 350, 200, 350});
                manager.createNotificationChannel(channel);
            }
        }
    }

    @JavascriptInterface
    public void registerTechnicianChannel(String nik, String namaTeknisi) {
        if (nik == null || nik.trim().isEmpty()) return;
        String cleanNik = nik.trim();
        String cleanName = (namaTeknisi != null && !namaTeknisi.trim().isEmpty()) ? namaTeknisi.trim() : cleanNik;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_TECH_NIK, cleanNik)
                .putString(KEY_TECH_NAME, cleanName)
                .apply();

        String channelId = "ading_tech_" + cleanNik;
        String channelName = "Channel Teknisi: " + cleanName + " (" + cleanNik + ")";
        ensureNotificationChannel(channelId, channelName);
    }

    @JavascriptInterface
    public void notifyTaskJson(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(jsonPayload);
            notifyTaskAssigned(
                    obj.optString("ao_wo", "-"),
                    obj.optString("teknisi", "-"),
                    obj.optString("nama_pelanggan", "-"),
                    obj.optString("id_pelanggan", "-"),
                    obj.optString("odp", "-"),
                    obj.optString("package", "-"),
                    obj.optString("alamat", "-"),
                    obj.optString("no_hp", ""),
                    obj.optString("booking_date", "-"),
                    obj.optString("status_booking", "-"),
                    obj.optString("koordinat", ""),
                    obj.optString("catatan", "-"),
                    obj.optString("assigned_by", "-")
            );
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void notifyTaskAssigned(
            String aoScWo,
            String teknisi,
            String namaPelanggan,
            String idPelanggan,
            String odp,
            String paket,
            String alamat,
            String noHp,
            String bookingDate,
            String statusBooking,
            String koordinat,
            String catatan,
            String assignedBy
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedNik = prefs.getString(KEY_TECH_NIK, "");
        String channelId = DEFAULT_CHANNEL_ID;
        if (!savedNik.isEmpty()) {
            String savedName = prefs.getString(KEY_TECH_NAME, savedNik);
            channelId = "ading_tech_" + savedNik;
            ensureNotificationChannel(channelId, "Channel Teknisi: " + savedName + " (" + savedNik + ")");
        } else {
            ensureNotificationChannel(DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_NAME);
        }

        String title = "📬 TASK ASSIGNED BARU 📬";
        if (teknisi != null && !teknisi.trim().isEmpty() && !"-".equals(teknisi.trim())) {
            title = "📬 TASK ASSIGNED BARU — " + teknisi.trim();
        }

        String summaryLine = (aoScWo != null ? aoScWo : "-") + " • " + (namaPelanggan != null ? namaPelanggan : "-");

        StringBuilder bigText = new StringBuilder();
        bigText.append("▪️ AO/SC | WO: ").append(nonEmpty(aoScWo)).append("\n");
        bigText.append("▪️ Teknisi: ").append(nonEmpty(teknisi)).append("\n");
        bigText.append("▪️ Nama Pelanggan: ").append(nonEmpty(namaPelanggan)).append("\n");
        bigText.append("▪️ Id Pelanggan: ").append(nonEmpty(idPelanggan)).append("\n");
        bigText.append("▪️ ODP: ").append(nonEmpty(odp)).append(" | Package: ").append(nonEmpty(paket)).append("\n");
        bigText.append("▪️ Alamat: ").append(nonEmpty(alamat)).append("\n");
        bigText.append("▪️ No HP: ").append(nonEmpty(noHp)).append("\n");
        bigText.append("🗓️ Booking Date: ").append(nonEmpty(bookingDate)).append("\n");
        bigText.append("📅 Status Booking: ").append(nonEmpty(statusBooking)).append("\n");
        bigText.append("📍 Koordinat: ").append(nonEmpty(koordinat)).append("\n");
        bigText.append("📝 Catatan: ").append(nonEmpty(catatan)).append("\n");
        bigText.append("👷 Assigned By: ").append(nonEmpty(assignedBy));

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        int notificationId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(context, notificationId, openAppIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(summaryLine)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent);

        if (noHp != null && !noHp.trim().isEmpty() && !"-".equals(noHp.trim())) {
            String cleanPhone = noHp.replaceAll("[^0-9]", "");
            if (cleanPhone.startsWith("0")) {
                cleanPhone = "62" + cleanPhone.substring(1);
            }
            if (!cleanPhone.isEmpty()) {
                Intent waIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + cleanPhone));
                waIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent waPendingIntent = PendingIntent.getActivity(context, notificationId + 1, waIntent, flags);
                builder.addAction(0, "👉 Chat WhatsApp", waPendingIntent);
            }
        }

        if (koordinat != null && !koordinat.trim().isEmpty() && !"-".equals(koordinat.trim())) {
            String cleanCoords = koordinat.trim().replace(" ", "");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=" + Uri.encode(cleanCoords)));
            mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent mapPendingIntent = PendingIntent.getActivity(context, notificationId + 2, mapIntent, flags);
            builder.addAction(0, "📍 Buka di Google Maps", mapPendingIntent);
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    @JavascriptInterface
    public void showNotification(String title, String body) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        ensureNotificationChannel(DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_NAME);

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        int notificationId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(context, notificationId, openAppIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(nonEmpty(title))
                .setContentText(nonEmpty(body))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(nonEmpty(body)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    public static void injectWebNotificationHook(WebView view) {
        if (view == null) return;
        String js = "(function(){" +
                "if(window.__adingNotifHooked)return;window.__adingNotifHooked=true;" +
                "if(window.AdingAndroid){" +
                "  window.Notification=function(title,options){" +
                "    var body=(options&&options.body)?options.body:'';" +
                "    window.AdingAndroid.showNotification(title||'" + "Ading Mobile" + "',body);" +
                "  };" +
                "  window.Notification.permission='granted';" +
                "  window.Notification.requestPermission=function(cb){if(cb)cb('granted');return Promise.resolve('granted');};" +
                "}" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private String nonEmpty(String val) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : "-";
    }
}
