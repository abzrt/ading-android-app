<?php
/**
 * Modul Pengirim Notifikasi Task Assigned ke Channel / Topic Telegram Masing-Masing Teknisi
 * untuk Dashboard ADING (https://ading.web.id/)
 */

class TelegramTaskNotifier
{
    private string $botToken;

    /**
     * Daftar mapping NIK Teknisi ke Chat ID / Topic ID (message_thread_id) Telegram
     * Contoh:
     * - Jika menggunakan Supergroup dengan fitur Topic per Teknisi: isi 'chat_id' grup utama dan 'thread_id' masing-masing teknisi.
     * - Jika menggunakan Channel/Chat pribadi per Teknisi: isi 'chat_id' masing-masing teknisi dan 'thread_id' = null.
     */
    private array $technicianChannels = [
        '16983839' => [
            'name'      => 'ARIFIN',
            'chat_id'   => '-100xxxxxxxxxx', // Ganti dengan Chat ID Grup/Channel Telegram
            'thread_id' => 123               // ID Topic khusus teknisi (opsional, isi null jika tanpa topic)
        ],
    ];

    public function __construct(string $botToken, array $customChannels = [])
    {
        $this->botToken = $botToken;
        if (!empty($customChannels)) {
            $this->technicianChannels = $customChannels;
        }
    }

    /**
     * Kirim Gambar Screenshot WFM + Pesan Detail Task Assigned Baru ke Channel Teknisi
     */
    public function sendTaskAssigned(array $task, ?string $wfmScreenshotPathOrUrl = null): array
    {
        $nik = trim((string)($task['nik_teknisi'] ?? ''));
        $channel = $this->technicianChannels[$nik] ?? [
            'name'      => $task['nama_teknisi'] ?? 'TEKNISI',
            'chat_id'   => $task['chat_id'] ?? '',
            'thread_id' => $task['thread_id'] ?? null,
        ];

        if (empty($channel['chat_id'])) {
            return ['ok' => false, 'error' => 'Chat ID teknisi tidak ditemukan untuk NIK: ' . $nik];
        }

        $results = [];

        // 1. Kirim Screenshot WFM terlebih dahulu jika tersedia
        if (!empty($wfmScreenshotPathOrUrl)) {
            $aoCode = $task['ao_sc'] ?? ($task['ao_wo'] ?? '-');
            $caption = "SS WFM - Simple: " . $aoCode;
            $results['photo'] = $this->sendPhoto($channel['chat_id'], $wfmScreenshotPathOrUrl, $caption, $channel['thread_id']);
        }

        // 2. Susun format pesan persis seperti template operasional ADING
        $namaTeknisi   = htmlspecialchars($channel['name'] ?? ($task['nama_teknisi'] ?? '-'));
        $aoWo          = htmlspecialchars(($task['ao_sc'] ?? '-') . ' | ' . ($task['wo'] ?? '-'));
        $teknisiLabel  = htmlspecialchars(($task['nama_teknisi'] ?? $namaTeknisi) . (!empty($nik) ? " ({$nik})" : ''));
        $namaPelanggan = htmlspecialchars($task['nama_pelanggan'] ?? '-');
        $idPelanggan   = htmlspecialchars($task['id_pelanggan'] ?? '-');
        $odp           = htmlspecialchars($task['odp'] ?? '-');
        $package       = htmlspecialchars($task['package'] ?? '-');
        $alamat        = htmlspecialchars($task['alamat'] ?? '-');
        $noHpRaw       = preg_replace('/[^0-9]/', '', (string)($task['no_hp'] ?? ''));
        if (str_starts_with($noHpRaw, '0')) {
            $noHpRaw = '62' . substr($noHpRaw, 1);
        }
        $noHpDisplay   = htmlspecialchars($noHpRaw ?: '-');
        $bookingDate   = htmlspecialchars($task['booking_date'] ?? '-');
        $statusBooking = htmlspecialchars($task['status_booking'] ?? 'Manja On Track');
        $koordinat     = trim((string)($task['koordinat'] ?? ''));
        $koordinatDisp = htmlspecialchars($koordinat ?: '-');
        $catatan       = htmlspecialchars($task['catatan'] ?? '-');
        $assignedBy    = htmlspecialchars($task['assigned_by'] ?? '-');

        $waMessage = rawurlencode("Halo Bapak/Ibu {$task['nama_pelanggan']}, saya {$namaTeknisi} teknisi Telkom Akses untuk tindak lanjut order {$idPelanggan}.");
        $waUrl     = !empty($noHpRaw) ? "https://wa.me/{$noHpRaw}?text={$waMessage}" : "https://wa.me/";
        $mapsQuery = rawurlencode(str_replace(' ', '', $koordinat));
        $mapsUrl   = !empty($koordinat) ? "https://www.google.com/maps?q={$mapsQuery}" : "https://www.google.com/maps";

        $messageHtml = "<b>{$namaTeknisi}</b>\n"
            . "📬 <b>TASK ASSIGNED BARU</b> 📬\n\n"
            . "▪️ <b>AO/SC | WO:</b> {$aoWo}\n"
            . "▪️ <b>Teknisi:</b> {$teknisiLabel}\n"
            . "▪️ <b>Nama Pelanggan:</b> {$namaPelanggan}\n"
            . "▪️ <b>Id Pelanggan :</b> <a href=\"tel:{$idPelanggan}\">{$idPelanggan}</a>\n"
            . "▪️ <b>ODP:</b> {$odp}\n"
            . "▪️ <b>Package:</b> {$package}\n"
            . "▪️ <b>Alamat:</b> {$alamat}\n"
            . "▪️ <b>No HP:</b> {$noHpDisplay}\n"
            . "👉 <a href=\"{$waUrl}\">Kirim WhatsApp (Chat Pelanggan)</a>\n"
            . "🗓️ <b>Booking Date:</b> {$bookingDate}\n"
            . "📅 <b>Status Booking:</b> <u>{$statusBooking}</u>\n"
            . "<b>Koordinat:</b> {$koordinatDisp}\n"
            . "📍 <a href=\"{$mapsUrl}\">Buka di Google Maps</a>\n"
            . "📝 <b>Catatan:</b> {$catatan}\n"
            . "👷 <b>Assigned By:</b> {$assignedBy}";

        $results['message'] = $this->sendMessage($channel['chat_id'], $messageHtml, $channel['thread_id']);
        return $results;
    }

    private function sendMessage(string $chatId, string $htmlText, ?int $threadId = null): array
    {
        $payload = [
            'chat_id'                  => $chatId,
            'text'                     => $htmlText,
            'parse_mode'               => 'HTML',
            'disable_web_page_preview' => true,
        ];
        if ($threadId !== null) {
            $payload['message_thread_id'] = $threadId;
        }
        return $this->requestTelegram('sendMessage', $payload);
    }

    private function sendPhoto(string $chatId, string $photoPathOrUrl, string $caption, ?int $threadId = null): array
    {
        $payload = [
            'chat_id' => $chatId,
            'caption' => $caption,
        ];
        if ($threadId !== null) {
            $payload['message_thread_id'] = $threadId;
        }
        if (file_exists($photoPathOrUrl)) {
            $payload['photo'] = new CURLFile(realpath($photoPathOrUrl));
        } else {
            $payload['photo'] = $photoPathOrUrl;
        }
        return $this->requestTelegram('sendPhoto', $payload);
    }

    private function requestTelegram(string $method, array $payload): array
    {
        $url = "https://api.telegram.org/bot{$this->botToken}/{$method}";
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_POST           => true,
            CURLOPT_POSTFIELDS     => $payload,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => 20,
        ]);
        $response = curl_exec($ch);
        $error = curl_error($ch);
        curl_close($ch);

        if ($response === false) {
            return ['ok' => false, 'error' => $error];
        }
        return json_decode($response, true) ?: ['ok' => false, 'raw' => $response];
    }
}
