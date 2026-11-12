/**
 * Helper JavaScript untuk Integrasi Notifikasi Native Ading Mobile (Android WebView Bridge)
 * Pasang script ini di halaman dashboard https://ading.web.id/
 */
window.AdingMobileBridge = {
    isAndroidApp: function () {
        return typeof window.AdingAndroid !== "undefined";
    },

    /**
     * Daftarkan Channel Notifikasi khusus teknisi yang sedang login
     * @param {string} nik - Contoh: "16983839"
     * @param {string} nama - Contoh: "ARIFIN"
     */
    registerTechnician: function (nik, nama) {
        if (this.isAndroidApp() && window.AdingAndroid.registerTechnicianChannel) {
            window.AdingAndroid.registerTechnicianChannel(String(nik || ""), String(nama || ""));
        }
    },

    /**
     * Tampilkan notifikasi Task Assigned Baru lengkap dengan tombol aksi WhatsApp & Google Maps di HP teknisi
     * @param {Object} task
     */
    notifyTaskAssigned: function (task) {
        if (!task) return;
        if (this.isAndroidApp() && window.AdingAndroid.notifyTaskJson) {
            window.AdingAndroid.notifyTaskJson(JSON.stringify({
                ao_wo: task.ao_wo || ((task.ao_sc || "-") + " | " + (task.wo || "-")),
                teknisi: task.teknisi || ((task.nama_teknisi || "-") + (task.nik_teknisi ? " (" + task.nik_teknisi + ")" : "")),
                nama_pelanggan: task.nama_pelanggan || "-",
                id_pelanggan: task.id_pelanggan || "-",
                odp: task.odp || "-",
                package: task.package || "-",
                alamat: task.alamat || "-",
                no_hp: task.no_hp || "",
                booking_date: task.booking_date || "-",
                status_booking: task.status_booking || "Manja On Track",
                koordinat: task.koordinat || "",
                catatan: task.catatan || "-",
                assigned_by: task.assigned_by || "-"
            }));
        }
    }
};

