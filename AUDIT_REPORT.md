# GUZNOTARA — LAPORAN AUDIT BASIS KODE MENYELURUH (COMPREHENSIVE CODEBASE AUDIT)

**Tanggal Audit**: 26 Oktober 2026  
**Status Audit**: Selesai (Read-Only Codebase Inspection & Verification)  
**Tujuan Audit**: Menilai kesiapan produksi, integritas penanganan audio, jalur Speech-to-Text nyata, rencana integrasi fallback Groq, dan ketahanan sesi rapat 60+ menit sebelum pembaruan implementasi berikutnya.

---

## DAFTAR ISI
1. [A. Executive Summary](#a-executive-summary)
2. [B. Implementation Inventory](#b-implementation-inventory)
3. [C. Temuan Audit Berbasis Bukti (Detailed Findings)](#c-temuan-audit-berbasis-bukti-detailed-findings)
4. [D. Audio Lifecycle & Retention Policy](#d-audio-lifecycle--retention-policy)
5. [E. Integrasi AI: Gemini & Rencana Fallback Groq](#e-integrasi-ai-gemini--rencana-fallback-groq)
6. [F. Keandalan, Keamanan, dan Hasil Pengujian Aktual](#f-keandalan-keamanan-dan-hasil-pengujian-aktual)
7. [G. Rekomendasi Rencana Pembaruan (Next-Update Plan)](#g-rekomendasi-rencana-pembaruan-next-update-plan)
8. [H. Keputusan yang Perlu Ditentukan Pengguna](#h-keputusan-yang-perlu-ditentukan-pengguna)

---

## A. Executive Summary

### Status Kelayakan: **BELUM LAYAK UNTUK RAPAT KRUSIAL / PRODUKSI (UNFIT FOR HIGH-STAKES MEETINGS)**

Aplikasi **GuzNotara** (`com.aistudio.guznotara.app`) telah memiliki fondasi antarmuka Jetpack Compose yang rapi, arsitektur Room SQLite yang modular, foreground service untuk perekaman latar belakang, serta generator ekspor dokumen PDF/Markdown/TXT yang berfungsi.

Namun, audit mendalam terhadap kode sumber mengungkap **4 kesenjangan kritis antara tujuan produk dan implementasi aktual**:

1. **Audio Tidak Pernah Diproses Mesin Speech-to-Text (P0)**:
   File audio rekaman `.m4a` tersimpan di memori perangkat, tetapi **sama sekali tidak dikirimkan ke model AI**. Pemanggilan transkripsi hanya mengirimkan judul pertemuan dan durasi sebagai teks biasa, sehingga hasil transkrip merupakan teks karangan (*hallucinated*) atau segmen dialog contoh *hardcoded*. Suara asli pengguna belum pernah dikonversi menjadi teks.
2. **Ketiadaan Integrasi Provider Groq (P0)**:
   Pencarian di seluruh basis kode menunjukkan **nol kode** untuk Groq (`api.groq.com`). Fallback penataan dokumen dari Gemini ke Groq belum diimplementasikan.
3. **Penyimpanan Audio Belum Mengadopsi Konsep Media Sementara (P1)**:
   File rekaman audio `.m4a` disimpan permanen di direktori internal aplikasi tanpa pembersihan otomatis (*auto-cleanup*) setelah transkripsi selesai. Hal ini berpotensi menghabiskan memori perangkat (*storage leak*).
4. **Kerentanan Kontainer MP4/AAC pada Rapat Panjang 60+ Menit (P1)**:
   Perekaman menggunakan `MediaRecorder.OutputFormat.MPEG_4` menyimpan tabel indeks (`moov` atom) pada saat proses rekaman selesai secara normal. Jika terjadi *crash*, kehabisan daya baterai, atau proses dimatikan sistem di menit ke-55, seluruh file menjadi korup dan tidak dapat diputar.

---

## B. Implementation Inventory

Pemetaan komponen nyata yang ditemukan di dalam repositori:

| Modul / Domain | Path File | Komponen / Simbol Utama | Status Implementasi Aktual |
| :--- | :--- | :--- | :--- |
| **Android Manifest** | `/app/src/main/AndroidManifest.xml` | `<service AudioRecordingService>`, `<provider FileProvider>`, izin: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `INTERNET`. | **Terimplementasi Penuh**. Konfigurasi foreground service & FileProvider sesuai standar Android 14+. |
| **Perekaman Audio** | `/app/src/main/java/com/example/recording/AudioRecordingService.kt` | `AudioRecordingService`, `LocalBinder`, `MediaRecorder`, `startRecording()`, `pauseRecording()`, `resumeRecording()`, `stopRecording()`. | **Terimplementasi (Perlu Peningkatan)**. Menulis audio AAC ke `filesDir/recordings/rec_<id>.m4a`. Menghitung timer dan amplitudo. Memiliki fallback simulasi jika hardware mic tidak tersedia di container emulator. |
| **Entitas Data (Room)** | `/app/src/main/java/com/example/data/model/Entities.kt` | `MeetingEntity`, `TranscriptSegmentEntity`, `MeetingDocumentEntity`. | **Terimplementasi Penuh**. Struktur relasional SQLite dengan foreign key `CASCADE` dan indeks. |
| **Akses Data (DAOs)** | `/app/src/main/java/com/example/data/local/Daos.kt` | `MeetingDao`, `TranscriptDao`, `DocumentDao`. | **Terimplementasi Penuh**. Reactive Flow, pembaruan status rapat, pengubahan nama pembicara, dan pengeditan teks ucapan. |
| **Database Container** | `/app/src/main/java/com/example/data/local/AppDatabase.kt` | `AppDatabase : RoomDatabase()`. | **Terimplementasi Penuh**. Singleton dengan `fallbackToDestructiveMigration(true)` dan KSP compiler. |
| **Repositori Data** | `/app/src/main/java/com/example/data/repository/MeetingRepository.kt` | `MeetingRepository`. | **Terimplementasi Penuh**. Mengabstraksi DAO, sinkronisasi model dokumen verbatim, dan penghapusan data. |
| **State Management** | `/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt` | `MainViewModel : AndroidViewModel`. | **Terimplementasi Penuh**. StateFlow terpusat untuk status rekam, data rapat, segmen aktif, dokumen aktif, dan status AI. |
| **Integrasi AI** | `/app/src/main/java/com/example/ai/GeminiAiManager.kt` | `GeminiAiManager`, model: `gemini-3.5-transcribe`, `gemini-3.8-flash`, `gemini-4-argon` (Thinking HIGH). | **Sebagian**. Model penata dokumen (`organizeDocumentWithThinking`) terhubung. Transkripsi audio (`transcribeMeetingAudio`) **belum mengirim data audio**. |
| **Keamanan App Check** | `/app/src/main/java/com/example/ai/AppCheckHelper.kt` | `configureAppCheck(context, intent)`. | **Terimplementasi Penuh**. Inisialisasi token debug synchronous (`commit()`) sesuai kontrak Firebase AI Logic. |
| **Tampilan Utama** | `/app/src/main/java/com/example/ui/screens/HomeScreen.kt` | `HomeScreen`, `MeetingCard`, dialog mulai rapat. | **Terimplementasi Penuh**. Pencarian judul, status badge, tombol aksi apung (FAB). |
| **Tampilan Studio** | `/app/src/main/java/com/example/ui/screens/RecordingStudioScreen.kt` | `RecordingStudioScreen`, `AudioWaveformVisualizer`. | **Terimplementasi Penuh**. Visualizer gelombang suara dinamis, timer monospaced, tombol jeda/lanjut, tombol selesai. |
| **Tampilan Dokumen** | `/app/src/main/java/com/example/ui/screens/TranscriptWorkspaceScreen.kt` | `TranscriptWorkspaceScreen`, tab Verbatim ↔ Tertata. | **Terimplementasi Penuh**. Switcher dwimode, pencarian teks, dialog koreksi ucapan, dialog ganti nama pembicara. |
| **Ekspor Dokumen** | `/app/src/main/java/com/example/export/ExportHelper.kt` | `ExportHelper` (`generatePdf`, `generateMarkdown`, `generatePlainText`, `shareFile`). | **Terimplementasi Penuh**. PDF multi-halaman berbasis Canvas, dokumen Markdown `.md`, teks polos `.txt`, integrasi Android Sharesheet. |
| **Pengujian Unit** | `/app/src/test/java/com/example/ExampleRobolectricTest.kt` | `readStringFromContext`, `insertAndQueryMeeting`, `insertAndRenameSpeakerSegments`, `generatePdfAndMarkdownFiles`. | **Terimplementasi Penuh**. 4/4 uji coba lulus pada environment Robolectric JVM. |

---

## C. Temuan Audit Berbasis Bukti (Detailed Findings)

### 1. [FINDING-01] [Prioritas P0] [Status: Terbukti dari Kode]
**File audio rekaman tidak pernah dikirimkan ke model Speech-to-Text.**
- **Lokasi Kode**: `/app/src/main/java/com/example/ai/GeminiAiManager.kt`, baris 58–111.
- **Bukti Konkret**:
  ```kotlin
  suspend fun transcribeMeetingAudio(
      meetingId: String,
      meetingTitle: String,
      audioFile: File?,
      durationMs: Long
  ): List<TranscriptSegmentEntity> = withContext(Dispatchers.IO) {
      val prompt = """
          Kamu adalah sistem transkripsi verbatim profesional untuk pertemuan...
          Nama pertemuan: "$meetingTitle".
          Durasi perkiraan: ${durationMs / 1000} detik.
          ...
      """.trimIndent()

      val responseText = withTimeout(45_000L) {
          val response = transcribeModel.generateContent(prompt) // Hanya prompt teks string!
          response.text
      }
      ...
  ```
- **Dampak bagi Pengguna**: Parameter `audioFile` sama sekali tidak dibaca atau dikonversi menjadi payload biner. Model AI hanya menerima teks judul rapat dan menghasilkan dialog rekaan berdasarkan judul tersebut. Jika API offline, sistem mengembalikan data contoh statis (`generateBilingualVerbatimSegments`). Suara asli peserta rapat tidak ditranskripsi.
- **Rekomendasi Pembenahan**:
  1. Perbarui `transcribeMeetingAudio` untuk membaca biner audio dari file fisik.
  2. Gunakan konstruksi multimodal Gemini SDK:
     ```kotlin
     val audioBytes = audioFile.readBytes()
     val response = transcribeModel.generateContent(
         content {
             blob("audio/mp4", audioBytes) // atau format chunk
             text("Transkripsikan rekaman percakapan ini secara verbatim ke JSON...")
         }
     )
     ```
  3. Untuk audio panjang, terapkan strategi *chunking* (misal per 5–10 menit) agar tidak melampaui limit ukuran payload inline (20MB) pada koneksi seluler.

---

### 2. [FINDING-02] [Prioritas P0] [Status: Terbukti dari Kode]
**Integrasi provider Groq belum ada di dalam basis kode.**
- **Lokasi Kode**: Seluruh repositori (hasil pencarian `grep -i "groq"` bernilai nol).
- **Bukti Konkret**: Tidak ada interface `GroqApi`, model request/response OpenAI-compatible, baseUrl `https://api.groq.com/openai/v1/`, maupun mekanisme penanganan kunci otentikasi Groq.
- **Dampak bagi Pengguna**: Jika kuota Gemini habis (HTTP 429), App Check gagal, atau jaringan ke Google bermasalah, aplikasi langsung jatuh ke penataan lokal sederhana (*local fallback template*). Tidak ada cadangan pemrosesan komputasi awan.
- **Rekomendasi Pembenahan**:
  1. Buat abstraksi provider:
     ```kotlin
     interface DocumentStructuringProvider {
         suspend fun organizeDocument(title: String, verbatimText: String): String
     }
     ```
  2. Implementasikan `GeminiStructuringProvider` (model `gemini-4-argon`) dan `GroqStructuringProvider` (model `llama-3.3-70b-versatile` via client `OkHttp` yang sudah terpasang).
  3. Konfigurasikan fallback otomatis saat Gemini mengalami error `429 Too Many Requests`, timeout, atau `5xx Server Error`.

---

### 3. [FINDING-03] [Prioritas P1] [Status: Terbukti dari Kode]
**Audio disimpan secara permanen di memori internal tanpa mekanisme auto-cleanup.**
- **Lokasi Kode**: 
  - `/app/src/main/java/com/example/recording/AudioRecordingService.kt` (baris 117–121)
  - `/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt` (baris 153–178)
  - `/app/src/main/java/com/example/data/repository/MeetingRepository.kt` (baris 136–145)
- **Bukti Konkret**:
  Audio ditulis ke `Context.filesDir/recordings/rec_${meetingId}.m4a`. Ketika transkripsi selesai, transkrip disimpan ke database, tetapi file fisik audio tidak pernah dibersihkan. Audio baru dihapus jika pengguna memilih opsi destruktif "Hapus Rapat" yang sekaligus melenyapkan data transkrip dan dokumen.
- **Dampak bagi Pengguna**:
  Pengguna yang merekam 5–10 jam rapat akan mendapati ruang penyimpanan internal ponsel berkurang ratusan megabyte tanpa ada tombol atau proses otomatis untuk membebaskan ruang tersebut.
- **Rekomendasi Pembenahan**:
  1. Tambahkan fungsi idempotent: `MeetingRepository.cleanupTemporaryAudio(meetingId: String): Boolean`.
  2. Eksekusi pembersihan segera setelah transkrip verbatim berhasil divalidasi dan tersimpan di database.
  3. Set `MeetingEntity.audioFilePath = null` di database (tidak memerlukan migrasi tabel karena kolom ini sudah bertipe `String?`).
  4. Jika transkripsi gagal, pertahankan file audio dan berikan tombol "Coba Transkripsi Ulang" atau "Hapus File Audio".

---

### 4. [FINDING-04] [Prioritas P1] [Status: Terbukti dari Analisis Platform]
**Kerentanan kontainer MPEG_4 pada durasi perekaman 60+ menit.**
- **Lokasi Kode**: `/app/src/main/java/com/example/recording/AudioRecordingService.kt`, baris 126–138.
- **Bukti Konkret**:
  Format output yang dikonfigurasi adalah `MediaRecorder.OutputFormat.MPEG_4` dengan encoder `MediaRecorder.AudioEncoder.AAC`. Format ini mengumpulkan indeks tabel frame audio pada atom `moov` yang baru ditulis ke file saat pemanggilan `mediaRecorder.stop()` berhasil dieksekusi.
- **Dampak bagi Pengguna**:
  Pada sesi rapat berdurasi 60–70 menit, jika baterai perangkat habis mendadak di menit ke-59 atau OS Android mematikan proses karena beban memori sistem, file `.m4a` yang tertinggal di disk tidak memiliki header penutup yang valid, sehingga file rusak dan seluruh rekaman 59 menit hilang.
- **Rekomendasi Pembenahan**:
  - Ubah format keluaran menjadi format streaming seperti `MediaRecorder.OutputFormat.AAC_ADTS` atau terapkan mekanisme perekaman rolling per segmen waktu (chunk 5–10 menit). Dengan AAC ADTS, setiap frame audio membawa header sendiri, sehingga pemutusan paksa tetap menyisakan file audio yang valid hingga milidetik terakhir.

---

### 5. [FINDING-05] [Prioritas P1] [Status: Terbukti dari Kode]
**Ketiadaan mekanisme chunking untuk dokumen rapat berdurasi panjang.**
- **Lokasi Kode**: `/app/src/main/java/com/example/ai/GeminiAiManager.kt`, baris 113–168.
- **Bukti Konkret**:
  Seluruh teks transkrip verbatim dikirimkan sekaligus dalam satu string prompt tunggal dengan batas timeout `60_000L` (60 detik).
- **Dampak bagi Pengguna**:
  Rapat berdurasi 60 menit menghasilkan 15.000–25.000 kata transkrip. Mengirimkan seluruh teks ini dalam satu pemanggilan rentan memicu timeout coroutine atau batas token keluaran model terlampaui sehingga kesimpulan atau diskusi bagian akhir terpotong.
- **Rekomendasi Pembenahan**:
  Buat deteksi panjang teks: jika jumlah kata transkrip melebihi batas aman (misal > 3.500 kata), bagi transkrip menjadi 2–3 babak pembahasan, proses penataan per babak, kemudian satukan dalam dokumen utuh.

---

## D. Audio Lifecycle & Retention Policy

### 1. Alur Aktual Saat Ini
```
[User Tekan Mulai] ──> Tulis file permanen: filesDir/recordings/rec_<id>.m4a
                              │
                              ▼
[User Tekan Selesai] ─> Catat path ke Room (MeetingEntity.audioFilePath)
                              │
                              ▼
[Proses Transkripsi] ─> Audio fisik diabaikan (hanya kirim teks judul)
                              │
                              ▼
[Hasil Tersimpan] ───> File audio tetap tertinggal di memori internal selamanya
                              │
                              ▼
[Hapus Rapat] ────────> Audio baru terhapus bersamaan dengan musnahnya transkrip & dokumen
```

### 2. Rekomendasi Alur Target (Resilient Temporary Audio)
```
[User Tekan Mulai]
       │
       ▼
Tulis ke file sementara: cacheDir/temp_audio/chunk_<id>_001.aac (Format AAC ADTS)
       │
       ▼
[User Tekan Selesai]
       │
       ▼
Status: RECORDED, audioAvailable = true
       │
       ▼
[Proses Transkripsi Nyata] ──> Baca biner audio ──> Kirim ke STT
       │
       ├── JIKA GAGAL: 
       │   • Audio JANGAN dihapus.
       │   • Status: ERROR / RETRY_READY.
       │   • Pengguna dapat mencoba transkripsi ulang kapan saja.
       │
       └── JIKA BERHASIL:
           • Simpan seluruh segmen verbatim & timestamp ke Room.
           • Jalankan Auto-Cleanup: Hapus file fisik chunk_<id>_*.aac dari cacheDir.
           • Update Room: MeetingEntity.audioFilePath = null.
           • Transkrip mentah, timestamp, dan dokumen tetap tersimpan permanen.
```

---

## E. Integrasi AI: Gemini & Rencana Fallback Groq

### Status Integrasi Provider Saat Ini:

| Komponen Fungsional | Provider Utama | Provider Fallback | Status Kesiapan Rapat Nyata |
| :--- | :--- | :--- | :--- |
| **Speech-to-Text (Audio → Teks)** | `gemini-3.5-transcribe` | Tidak ada | **0% (Belum Aktif)**. Tidak ada pengiriman biner audio ke API. |
| **Penataan Dokumen (Teks → Dokumen)** | `gemini-4-argon` (Thinking HIGH) | Fallback lokal statis | **75% (Aktif)**. Mengirim prompt terstruktur, namun belum memiliki chunking teks panjang. |
| **Fallback Cloud Penataan Dokumen** | Groq (`llama-3.3-70b-versatile`) | Tidak ada | **0% (Belum Diimplementasikan)**. |

### Rencana Arsitektur Fallback Groq yang Direkomendasikan:
1. **Titik Fallback**: Ditempatkan khusus pada tahap **Penataan Dokumen Terstruktur** dari transkrip verbatim.
2. **Kondisi Pemicu Fallback**:
   - Gemini mengembalikan status `HTTP 429` (Quota / Rate Limit).
   - Gemini mengalami `TimeoutCancellationException` (> 60 detik).
   - Gemini mengalami kegagalan App Check attestation / `HTTP 403`.
3. **Mekanisme Eksekusi**:
   - Memanfaatkan library `OkHttp` yang sudah terpasang di `build.gradle.kts`.
   - Mengirim request POST JSON ke `https://api.groq.com/openai/v1/chat/completions` dengan model `llama-3.3-70b-versatile`.
   - Menerapkan instruksi sistem *Zero-Omission* yang identik agar format dokumen tetap konsisten.
4. **Pencegahan Biaya & Loop**: Fallback hanya dicoba 1 kali; jika Groq juga gagal, sistem otomatis beralih ke *Local Editorial Formatter* tanpa menampilkan error putus asa kepada pengguna.

---

## F. Keandalan, Keamanan, dan Hasil Pengujian Aktual

### 1. Hasil Build & Verifikasi Lingkungan
- Pemeriksaan kompilasi: `compile_applet` -> **SUCCEEDED** (0 error kompilasi).
- Eksekusi pengujian lokal JVM: `gradle :app:testDebugUnitTest` -> **BUILD SUCCESSFUL (100% lulus)**:
  - `readStringFromContext`: Lulus (validasi string resource `GuzNotara`).
  - `insertAndQueryMeeting`: Lulus (validasi persistensi SQLite DAO).
  - `insertAndRenameSpeakerSegments`: Lulus (validasi cascading update Room).
  - `generatePdfAndMarkdownFiles`: Lulus (validasi pembuatan berkas fisik PDF dan Markdown).

### 2. Batasan Lingkungan Eksekusi (Cloud Container)
- Lingkungan kerja adalah container Linux cloud tanpa hardware mikrofon fisik nyata. 
- `AudioRecordingService` telah dilengkapi penanganan protektif: jika hardware mikrofon emulator gagal membuka PCM stream, service secara adaptif menjalankan simulasi aliran biner audio agar aplikasi tidak mengalami *fatal crash*. Namun, **kualitas penangkapan suara mikrofon fisik pada smartphone nyata wajib diuji pada perangkat Android fisik pengguna**.

### 3. Analisis Keamanan & Privasi Data
- **Kredensial**: Tidak ada API Key privat yang tertulis secara langsung (*hardcoded*) di dalam source code Kotlin.
- **App Check**: File `AppCheckHelper.kt` telah menggunakan penulisan token debug synchronous (`commit()`), mencegah *race condition* yang lazim menyebabkan penolakan token HTTP 403.
- **Penyimpanan Privat**: File rekaman dan database Room berada di bawah direktori privat aplikasi (`/data/user/0/com.aistudio.guznotara.app/`), terlindung dari akses aplikasi pihak ketiga tanpa hak *root*.
- **Berbagi File**: Seluruh ekspor berkas (PDF, MD, TXT) dibagikan melalui Android `FileProvider` dengan flag hak akses baca sementara saja (`FLAG_GRANT_READ_URI_PERMISSION`).

---

## G. Rekomendasi Rencana Pembaruan (Next-Update Plan)

Untuk mencapai aplikasi yang benar-benar stabil, hemat memori, dan berfungsi nyata, berikut urutan implementasi bertahap yang paling aman dan efisien:

### Tahap 1: Perbaikan Kritis Jalur Audio & STT (Critical Audio & STT Fix)
1. **Koneksi Audio Riil ke Speech-to-Text**:
   - Implementasikan pembacaan data audio rekaman dan kirimkan sebagai payload biner multimodal ke model STT Gemini.
   - *Kriteria Penerimaan (Acceptance Criteria)*: Teks transkrip yang muncul di tab Verbatim mencerminkan suara nyata yang direkam, bukan judul rapat.
2. **Pembersihan Audio Sementara Otomatis (Auto-Cleanup Engine)**:
   - Buat fungsi pembersihan yang otomatis menghapus file audio lokal segera setelah transkrip berhasil disimpan ke database.
   - Perbarui kolom `audioFilePath` menjadi `null` tanpa merusak transkrip mentah, timestamp, atau dokumen.
   - *Kriteria Penerimaan*: Folder rekaman audio kembali berukuran 0 KB setelah transkripsi selesai; data teks dan ekspor tetap utuh 100%.

### Tahap 2: Keandalan Sesi 60+ Menit & Format Audio Tahan Crash
1. **Container Audio Tahan Gangguan**:
   - Ubah format audio ke AAC ADTS atau buat partisi file per segmen 5 menit selama perekaman berlangsung.
   - *Kriteria Penerimaan*: Menghentikan paksa aplikasi di tengah perekaman tetap menyisakan file audio yang valid dan dapat ditranskripsi hingga detik sebelum penghentian.
2. **Chunking Transkrip Panjang untuk Penataan Dokumen**:
   - Terapkan pemecahan teks cerdas jika transkrip melebihi 3.500 kata agar tidak terkena timeout 60 detik.
   - *Kriteria Penerimaan*: Sesi rapat 60–70 menit berhasil ditata menjadi dokumen lengkap tanpa memotong bagian penutup atau kesimpulan.

### Tahap 3: Implementasi Fallback Groq untuk Penataan Dokumen
1. **Integrasi Provider Groq**:
   - Buat layer interface penata dokumen dengan dua implementasi: `Gemini` (utama) dan `Groq` (cadangan via OkHttp).
   - *Kriteria Penerimaan*: Simulasi kegagalan kuota Gemini (429) otomatis mengalihkan proses ke Groq dan menghasilkan dokumen terstruktur dengan gaya editorial yang seragam.

### Tahap 4: Pembaruan Dokumentasi & Uji Regresi Menyeluruh
1. Sinkronkan dokumen arsitektur (`ARCHITECTURE.md`, `IMPLEMENTATION_PLAN.md`, `TECHNICAL_DECISIONS.md`, `KNOWN_LIMITATIONS.md`).
2. Tambahkan unit test baru untuk `AutoCleanupHelper` dan `GroqFallbackEngine`.

---

## H. Keputusan yang Perlu Ditentukan Pengguna

Sebelum kita beralih ke tahap implementasi pada sesi berikutnya, silakan tentukan arahan Anda untuk 2 poin kebijakan produk berikut:

1. **Penanganan Audio Jika Transkripsi Mengalami Kegagalan Jaringan/API**:
   - **Opsi A (Rekomendasi)**: Pertahankan file audio fisik sementara, tampilkan status *"Gagal Transkripsi"*, dan sediakan tombol *"Coba Lagi"* serta tombol *"Hapus Audio Saja"*. File audio baru dibersihkan setelah transkripsi berhasil atau saat pengguna membatalkannya secara manual.
   - **Opsi B**: Hapus file audio secara otomatis setelah batas waktu tertentu (misal 24 jam) untuk menjamin kapasitas memori perangkat selalu bersih.
2. **Metode Konfigurasi API Key Groq**:
   - **Opsi A (Rekomendasi)**: Dikonfigurasi melalui Secrets Panel AI Studio (injeksi via `BuildConfig.GROQ_API_KEY`).
   - **Opsi B**: Disediakan menu input teks manual di dalam halaman pengaturan aplikasi agar pengguna dapat mengganti key secara langsung dari antarmuka.

---
*Laporan audit komprehensif ini telah disimpan di berkas `/AUDIT_REPORT.md` pada root repositori untuk menjadi referensi teknis resmi sebelum melangkah ke pembaruan kode.*
