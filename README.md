# ADING Android App

Aplikasi Android resmi pembungkus (WebView wrapper) untuk **ADING** (Aplikasi Dashboard & Monitoring Operasional Telkom Akses Branch Banjarmasin - https://ading.web.id/).

---

## 📱 File APK Siap Pakai

File installer APK yang sudah di-build dengan standar keamanan Google Play Store:
- **Lokasi file APK:**
  `C:\Users\user\.gemini\antigravity\scratch\ading-android-app\ADING.apk`

---

## 🛡️ Standar Keamanan Google Play Store yang Telah Diterapkan

1. **Network Security Config & HTTPS Enforcement:**
   - Menolak semua lalu lintas data tanpa enkripsi HTTP biasa (`cleartextTrafficPermitted="false"`).
   - Mengharuskan sertifikat CA sistem tepercaya (`network_security_config.xml`).
2. **Perlindungan Terhadap SSL Error Bypassing:**
   - Mematuhi kebijakan wajib Google Play: tidak pernah mengabaikan atau memaksa koneksi (`handler.cancel()`) saat sertifikat SSL bermasalah untuk melindungi pengguna dari serangan MITM (Man-in-the-Middle).
3. **Mitigasi Kerentanan Akses File (CWE-200):**
   - Menonaktifkan `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, `setAllowFileAccessFromFileURLs(false)`, dan `setAllowUniversalAccessFromFileURLs(false)` guna mencegah injeksi skema file lokal.
4. **Google Safe Browsing:**
   - Mengaktifkan proteksi Google Safe Browsing untuk mendeteksi dan memblokir phishing atau malware secara otomatis.
5. **Mitigasi Intent Redirection & Hijacking:**
   - Tautan `intent:` disanitasi secara ketat dengan membersihkan komponen private/internal (`component=null`, `selector=null`) dan mewajibkan kategori `CATEGORY_BROWSABLE`.
6. **Perlindungan Data Cadangan (Anti-Extraction):**
   - Menonaktifkan `allowBackup="false"` untuk melindungi cookie login, sesi, dan data otentikasi dashboard dari ekstraksi melalui kabel USB / debugging ADB.
7. **ProGuard / R8 Rules:**
   - Konfigurasi proteksi kode produksi dan penghapusan log debugging di `proguard-rules.pro`.

---

## 📋 Daftar Permission yang Dikonfigurasi

| Permission | Tujuan & Kebutuhan | Penanganan di Android Modern |
| :--- | :--- | :--- |
| `INTERNET` | Mengakses web server ADING secara online | Wajib untuk aplikasi web |
| `ACCESS_NETWORK_STATE` | Mendeteksi status internet offline/online | Menampilkan layar error ramah |
| `ACCESS_FINE_LOCATION` | Mengetahui posisi GPS akurat | Permintaan izin runtime via prompt |
| `ACCESS_COARSE_LOCATION` | Mengetahui perkiraan posisi jaringan | Permintaan izin runtime via prompt |
| `CAMERA` | Mengambil foto langsung bukti perbaikan di lapangan | Bertanda `required="false"` agar kompatibel di semua tipe HP |
| `POST_NOTIFICATIONS` | Menerima notifikasi dashboard / alert penting | Didukung untuk Android 13+ (API 33+) |
| `VIBRATE` | Efek getar untuk alert atau notifikasi | Akses langsung |
| `READ_EXTERNAL_STORAGE` | Kompatibilitas unduhan di Android lama (<= Android 12) | Dibatasi `maxSdkVersion="32"` |
| `WRITE_EXTERNAL_STORAGE` | Kompatibilitas unduhan di Android jadul (<= Android 9) | Dibatasi `maxSdkVersion="28"` |

---

## 🚀 Cara Build Release (AAB / Signed APK) untuk Play Store Console

Jika Anda ingin mengunggah ke Google Play Console:
1. Google Play saat ini mewajibkan format **Android App Bundle (.aab)** untuk rilis baru.
2. Buat file `.aab` menggunakan perintah:
   ```bash
   .\gradlew.bat bundleRelease
   ```
3. File `.aab` akan otomatis dihasilkan di folder `app/build/outputs/bundle/release/`.
