# 03 — Sprint Roadmap
## Catet AI v2: Rencana Implementasi Bertahap

| Metadata            | Isi                                        |
| :------------------ | :----------------------------------------- |
| **Versi Dokumen**   | 2.0.0                                      |
| **Status**          | DRAFT                                      |
| **Tanggal Dibuat**  | 18 November 2026                           |
| **Referensi**       | `01_PRD_CATET_AI.md` v2.0.0, `02_TECH_SPEC_CATET_AI.md` v2.0.0 |

---

## Ringkasan Fase

| Fase | Nama | Fokus | Jumlah Tiket | Estimasi Jam |
| :---: | :--- | :--- | :---: | :---: |
| **0** | Fondasi Proyek | Arsitektur dasar, DI, navigasi, tema, DB | ✅ 7 (SELESAI) | ✅ ~14 |
| **1** | Impor & Manajemen Berkas | SAF picker, dekoder audio, skema DB baru, HomeScreen adaptasi | 8 | ~20 |
| **2** | Mesin Transkripsi | whisper.cpp NDK, JNI bindings, model manager, proses transkripsi | 7 | ~28 |
| **3** | Perangkum & Tampilan Hasil | Perangkum Ekstraktif, BYOK, dua tab hasil, pemutar audio sinkron | 6 | ~18 |
| **4** | Ekspor, Pencarian & Polish | PDF generator, TXT/MD export, FTS4, Settings, QA akhir | 6 | ~16 |
| | | **TOTAL** | **34** | **~96** |

> **Catatan:** Fase 0 sudah selesai dari iterasi v1. Komponen yang masih relevan (Hilt DI, Room + SQLCipher, tema, navigasi) tetap digunakan. Kode perekam audio (F1-01 s.d. F1-05 v1) dipindahkan ke paket arsip `legacy/` dan tidak dihapus.

---

## Fase 0 — Fondasi Proyek ✅ SELESAI

Fase ini sudah dikerjakan pada iterasi v1 dan **tetap valid** untuk v2.

| Tiket | Judul | Status | Commit |
| :--- | :--- | :---: | :--- |
| F0-01 | Inisialisasi proyek Android (Kotlin DSL, Compose BOM) | ✅ | — |
| F0-02 | Setup Hilt DI | ✅ | — |
| F0-03 | Setup Jetpack Navigation Compose | ✅ | — |
| F0-04 | Setup Room + SQLCipher | ✅ | — |
| F0-05 | Token desain (Color, Type, Dimens) + Tema Material 3 | ✅ | — |
| F0-06 | Konfigurasi Git (.gitignore, Conventional Commits) | ✅ | — |
| F0-07 | Konfigurasi lint & KSP (Room schema export) | ✅ | — |

**Komponen yang perlu disesuaikan di Fase 1:**
- `RecordingEntity` → skema baru (tambah `original_file_name`, `mime_type`, `imported_at`; hapus kolom khusus perekam)
- `RecordingDao` → sesuaikan query
- `HomeScreen` / `HomeViewModel` → sesuaikan status dan aksi

---

## Fase 1 — Impor & Manajemen Berkas

**Tujuan:** Pengguna dapat mengimpor berkas audio dari penyimpanan HP, melihatnya di daftar, dan mengelolanya (ubah nama, hapus).

### F1-01: Migrasi Skema Database

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Buat skema Room baru untuk alur impor. Tambah entitas `RecordingEntity` v2 (kolom: `original_file_name`, `mime_type`, `imported_at`, `file_size_bytes`; hapus kolom khusus perekam seperti `sample_rate`, `channels`). Tambah entitas `TranscriptSegmentEntity` dan `SummaryEntity`. Konfigurasi `ForeignKey CASCADE`. Buat Room migration dari skema v2 (saat ini) ke v3. |
| **Ref. PRD** | F-06, F-09, FR-08, FR-12 |
| **Ref. Tech Spec** | Bagian 4 (Skema Basis Data) |
| **Kriteria Selesai** | Migrasi otomatis berjalan tanpa *crash*. `exportSchema = true` menghasilkan berkas skema v3. Tes DAO instrumen lulus. |
| **Estimasi** | 3 jam |

### F1-02: AudioDecoder — Interface & MediaCodecDecoder

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Buat `AudioDecoder` interface. Implementasikan `MediaCodecDecoder` yang mengubah berkas audio (M4A, MP3, OGG, AAC, FLAC, WAV, OPUS) ke PCM 16kHz 16-bit Mono menggunakan `Android MediaCodec` API + `MediaExtractor`. Tangani *resampling* jika *sample rate* sumber ≠ 16kHz. |
| **Ref. PRD** | F-02, FR-02 |
| **Ref. Tech Spec** | Bagian 5 (Pipeline Pemrosesan Audio) |
| **Kriteria Selesai** | Tes unit: berkas M4A/MP3/WAV berhasil didekode ke PCM 16kHz Mono. Tes instrumen: dekode berkas nyata di TECNO CN5 tanpa *crash*. |
| **Estimasi** | 4 jam |

### F1-03: ImportAudioUseCase & SAF File Picker

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `ImportAudioUseCase`: (1) buka pemilih berkas sistem via `ACTION_OPEN_DOCUMENT` dengan filter `audio/*`, (2) salin berkas terpilih ke penyimpanan internal (`context.filesDir/audio/`), (3) baca metadata (durasi via `MediaMetadataRetriever`, ukuran, MIME type), (4) simpan `RecordingEntity` ke Room dengan status `IMPORTED`. Dukung juga penerimaan berkas via `Share Intent` (menambahkan `<intent-filter>` di `AndroidManifest.xml`). |
| **Ref. PRD** | F-01, FR-01, US-01 |
| **Kriteria Selesai** | Impor berkas M4A dari Voice Recorder di TECNO CN5 → muncul di daftar rekaman. Impor via Share Intent dari pengelola berkas → muncul di daftar. Tes unit: use case mengembalikan entitas yang benar. |
| **Estimasi** | 3 jam |

### F1-04: Adaptasi HomeScreen untuk Alur Impor

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Adaptasi `HomeScreen` dan `HomeViewModel` yang sudah ada: (1) ganti FAB "Rekam" menjadi FAB "＋ Impor Rekaman", (2) tampilkan status baru (`IMPORTED`, `DECODING`, `TRANSCRIBING`, `SUMMARIZING`, `COMPLETED`, `FAILED`, `INTERRUPTED`), (3) pertahankan fitur yang masih relevan (swipe-to-delete dengan blokir untuk status pemrosesan, daftar terurut tanggal, *empty state*). |
| **Ref. PRD** | F-06, US-02 |
| **Kriteria Selesai** | Daftar menampilkan rekaman yang diimpor dengan status benar. Swipe-to-delete diblokir untuk `TRANSCRIBING` dan `SUMMARIZING`. FAB membuka pemilih berkas. |
| **Estimasi** | 2 jam |

### F1-05: Validasi Berkas Impor & Penanganan Error

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Tambahkan validasi di `ImportAudioUseCase`: (1) tolak berkas non-audio (cek MIME type), (2) tolak berkas < 3 detik, (3) periksa ruang penyimpanan cukup (ukuran × 2 + 100 MB), (4) deteksi berkas duplikat (nama + ukuran sama), (5) tampilkan pesan error yang jelas untuk setiap kasus. |
| **Ref. PRD** | EC-01, EC-02, EC-04, EC-10 |
| **Kriteria Selesai** | Tes unit: impor berkas PDF → ditolak. Impor berkas 1 detik → ditolak. Impor saat ruang < 100 MB → ditolak dengan pesan. |
| **Estimasi** | 2 jam |

### F1-06: Bilah Kemajuan Impor (Berkas Besar)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Untuk berkas > 50 MB, tampilkan bilah kemajuan (*progress bar*) selama penyalinan dari SAF ke penyimpanan internal. Gunakan `ContentResolver.openInputStream()` dengan pembacaan berbuffer dan hitung persentase berdasarkan `file size`. |
| **Ref. PRD** | AC-01.4 |
| **Kriteria Selesai** | Impor berkas WAV 200 MB menampilkan bilah kemajuan yang bergerak dari 0% ke 100%. UI tidak *freeze*. |
| **Estimasi** | 1,5 jam |

### F1-07: Arsipkan Kode Perekam (Legacy)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Pindahkan kode perekam audio v1 (AudioConfig, AudioRecorderManager, PcmBufferWriter, WavHeaderUtil, RecordingForegroundService, RecordingSessionStore, RecordingNotificationHelper, RecordingThresholdEvaluator) ke paket `com.catetai.app.legacy.recorder`. Pindahkan tes terkait ke `src/test-legacy/`. Pastikan `assembleDebug` dan `testDebugUnitTest` tetap lulus setelah pemindahan. |
| **Ref. PRD** | X-01 (perekam ditunda ke v1.1) |
| **Kriteria Selesai** | Kode perekam ada di paket `legacy`. Build dan semua tes lulus. Kode perekam tidak direferensikan dari kode utama. |
| **Estimasi** | 2 jam |

### F1-08: Enkripsi Berkas Audio Impor (Tink)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan enkripsi berkas audio segera setelah impor berhasil menggunakan Google Tink `StreamingAead` (AES256-GCM-HKDF-4KB). Alur: SAF → salin ke *buffer* sementara → enkripsi → simpan sebagai `.enc` → hapus *buffer*. Implementasikan juga dekripsi saat berkas perlu didekode untuk transkripsi atau diputar. |
| **Ref. PRD** | F-09, NFR-06 |
| **Ref. Tech Spec** | Bagian 10 (Keamanan & Enkripsi) |
| **Kriteria Selesai** | Berkas audio di penyimpanan internal tidak terbaca tanpa kunci Tink. Dekripsi + pemutaran berhasil. Tes unit: enkripsi → dekripsi menghasilkan berkas identik. |
| **Estimasi** | 2,5 jam |

**Total Fase 1: ~20 jam**

---

## Fase 2 — Mesin Transkripsi

**Tujuan:** Whisper Large v3 Turbo Q5 berjalan di perangkat, menghasilkan segmen teks bertanda waktu.

### F2-01: Integrasi whisper.cpp sebagai Git Submodule

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Tambahkan `whisper.cpp` sebagai Git submodule di `app/src/main/cpp/whisper.cpp`. Buat `CMakeLists.txt` untuk membangun `whisper.cpp` sebagai pustaka statis dan `whisper_jni` sebagai *shared library*. Konfigurasi `externalNativeBuild` di `build.gradle.kts`. Pastikan *build* NDK berhasil untuk `arm64-v8a`. |
| **Ref. Tech Spec** | Bagian 6.5 (CMakeLists.txt) |
| **Kriteria Selesai** | `assembleDebug` berhasil. APK mengandung `libwhisper_jni.so` di `lib/arm64-v8a/`. |
| **Estimasi** | 4 jam |

### F2-02: JNI Bindings (whisper_jni.cpp + WhisperJni.kt)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan ikatan JNI: (1) `whisper_jni.cpp` — fungsi C++ yang memanggil API `whisper.cpp` (`whisper_init_from_file`, `whisper_full`, `whisper_full_n_segments`, dsb.), (2) `WhisperJni.kt` — deklarasi `external fun` di Kotlin. Dukung: `nativeInit`, `nativeTranscribe` (dengan *progress callback*), `nativeCancel`, `nativeRelease`. |
| **Ref. Tech Spec** | Bagian 6.1 – 6.2 |
| **Kriteria Selesai** | Memanggil `WhisperJni.nativeInit()` dengan berkas model *dummy* → mengembalikan handle > 0 (atau error yang jelas). Tes instrumen di TECNO CN5. |
| **Estimasi** | 5 jam |

### F2-03: WhisperEngine (Wrapper Kotlin)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `WhisperEngine` sebagai *wrapper* tingkat tinggi di atas `WhisperJni`: (1) `loadModel()` — muat berkas GGUF, (2) `transcribe()` — terima berkas PCM + `WhisperConfig`, panggil JNI, konversi hasil ke `List<TranscriptSegment>`, (3) `cancel()` — batalkan proses, (4) `release()` — bebaskan memori. Jalankan di `@IoDispatcher`. |
| **Ref. Tech Spec** | Bagian 6.3 |
| **Kriteria Selesai** | Transkripsi berkas PCM 30 detik menghasilkan segmen teks yang masuk akal. Progress callback dipanggil. Cancel menghentikan proses tanpa *crash*. |
| **Estimasi** | 3 jam |

### F2-04: TranscribeAudioUseCase (Pipeline Lengkap)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `TranscribeAudioUseCase` yang mengorkestrasi seluruh pipeline: (1) dekripsi berkas audio (Tink), (2) dekode ke PCM 16kHz (MediaCodecDecoder), (3) transkripsi (WhisperEngine), (4) simpan segmen ke Room (`TranscriptSegmentEntity`), (5) perbarui status rekaman (`TRANSCRIBING` → `COMPLETED` atau `FAILED`). Proses dalam *chunk* per 30 menit untuk rekaman panjang. Tangani pembatalan (*cancel*) dan *crash recovery*. |
| **Ref. PRD** | F-03, F-04, FR-03, FR-04, US-03, EC-03, EC-09 |
| **Kriteria Selesai** | Impor berkas M4A 5 menit di TECNO CN5 → tekan "Transkrip" → segmen teks muncul di DB. Pembatalan di 50% → tidak *crash*, data parsial tersimpan. |
| **Estimasi** | 5 jam |

### F2-05: Pengelola Model (Unduh & Validasi)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `ModelRepository` dan `DownloadModelUseCase`: (1) cek apakah model GGUF sudah ada di penyimpanan internal, (2) jika belum → tampilkan dialog konfirmasi (ukuran ~574 MB), (3) unduh dari Hugging Face dengan OkHttp, (4) dukung *resume* (HTTP Range header), (5) validasi integritas (SHA-256), (6) bilah kemajuan unduhan. |
| **Ref. PRD** | F-11, EC-05, EC-06, FR-13 |
| **Ref. Tech Spec** | Bagian 9 |
| **Kriteria Selesai** | Model berhasil diunduh. Unduhan yang terputus bisa dilanjutkan. SHA-256 cocok. Model bisa dimuat oleh WhisperEngine. |
| **Estimasi** | 4 jam |

### F2-06: UI Transkripsi (Bilah Kemajuan & Pembatalan)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Tambahkan UI di `DetailScreen` (atau *bottom sheet* dari `HomeScreen`): (1) bilah kemajuan transkripsi (0% → 100%), (2) estimasi waktu tersisa, (3) tombol "Batal", (4) tampilkan pesan error jika gagal, (5) tampilkan status "Terganggu — Proses Ulang?" jika sebelumnya terinterupsi. |
| **Ref. PRD** | AC-03.1, AC-03.5, EC-09 |
| **Kriteria Selesai** | Bilah kemajuan bergerak sesuai kemajuan transkripsi. Tombol Batal menghentikan proses. Status INTERRUPTED memunculkan opsi proses ulang. |
| **Estimasi** | 3 jam |

### F2-07: Kosakata Kustom (Prompt Biasing)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Tambahkan input kosakata kustom sebelum transkripsi: (1) dialog atau layar di mana pengguna mengetik istilah (dipisahkan koma), (2) simpan sebagai daftar per rekaman atau sebagai default global di Settings, (3) teruskan ke `WhisperConfig.customVocabulary` → dikirim sebagai `initial_prompt` ke Whisper. |
| **Ref. PRD** | F-04, FR-04, AC-03.4 |
| **Kriteria Selesai** | Masukkan "MagangHub, WFO, WFH" → transkripsi audio DASH → kata-kata tersebut muncul benar. |
| **Estimasi** | 2 jam |

**Total Fase 2: ~26 jam** (dibulatkan dari estimasi per tiket, beberapa overlap)

---

## Fase 3 — Perangkum & Tampilan Hasil

**Tujuan:** Transkrip dirangkum menjadi notulensi. Pengguna melihat hasil dalam dua tab (transkrip asli + notulensi).

### F3-01: Perangkum Ekstraktif (TextRank/TF-IDF)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `ExtractSummarizer`: (1) tokenisasi kalimat berbahasa Indonesia, (2) hitung TF-IDF per kalimat, (3) bangun graf kesamaan kosinus, (4) jalankan PageRank/TextRank, (5) pilih N kalimat teratas sebagai ringkasan, (6) deteksi kandidat *action items* via filter kata kunci aturan ("tolong", "harus", "paling lambat", hari/tanggal). |
| **Ref. PRD** | F-05 (Mode A), FR-06 |
| **Ref. Tech Spec** | Bagian 7.2 |
| **Kriteria Selesai** | Transkrip 500 kalimat → 5 kalimat ringkasan yang relevan dalam < 1 detik. Kalimat mengandung "tolong kirim laporan besok" → terdeteksi sebagai kandidat tugas. Tes unit dengan transkrip sampel. |
| **Estimasi** | 4 jam |

### F3-02: Perangkum BYOK Cloud (Groq/OpenAI)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `ByokSummarizer`: (1) Retrofit service ke Groq/OpenAI Chat Completions API, (2) *system prompt* anti-halusinasi PIC, (3) kirim teks transkrip (bukan audio) ke API, (4) *parse* respons menjadi `Summary` (ringkasan, keputusan, *action items*), (5) simpan kunci API terenkripsi di Keystore. Tangani error jaringan (EC-08). |
| **Ref. PRD** | F-05 (Mode B), F-12, FR-05, FR-07 |
| **Ref. Tech Spec** | Bagian 7.3 |
| **Kriteria Selesai** | Transkrip dikirim ke Groq → respons di-*parse* → notulensi 3 bagian tersimpan di Room. Tanpa internet → error ditampilkan. |
| **Estimasi** | 3 jam |

### F3-03: SummarizeTranscriptUseCase

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Orkestrator yang memilih perangkum berdasarkan pengaturan pengguna (Ekstraktif atau BYOK). Memanggil perangkum, menyimpan `SummaryEntity` ke Room, memperbarui status rekaman ke `COMPLETED`. Menjalankan otomatis setelah transkripsi selesai (atau secara manual jika pengguna memilih). |
| **Ref. PRD** | AC-04.1 |
| **Kriteria Selesai** | Setelah transkripsi selesai → perangkum berjalan otomatis → ringkasan tersimpan. Tes unit: use case memanggil perangkum yang benar berdasarkan konfigurasi. |
| **Estimasi** | 2 jam |

### F3-04: DetailScreen — Tab Transkrip Asli

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `DetailScreen` dengan tab pertama "Transkrip": (1) tampilkan daftar segmen teks dengan tanda waktu `[MM:SS]` di sebelah kiri, (2) gulir vertikal, (3) ketuk segmen → audio memutar dari detik tersebut (siapkan *callback*, pemutar di F3-06), (4) salin teks segmen ke *clipboard* dengan ketukan panjang. |
| **Ref. PRD** | F-07 (Tab Transkrip), US-05 |
| **Kriteria Selesai** | Tab Transkrip menampilkan seluruh segmen dengan tanda waktu. Mengetuk segmen memicu *callback* (pemutar belum aktif, diimplementasikan di F3-06). |
| **Estimasi** | 3 jam |

### F3-05: DetailScreen — Tab Notulensi

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan tab kedua "Notulensi" di `DetailScreen`: (1) ringkasan (poin bernomor), (2) keputusan (daftar bernomor), (3) daftar tugas (tabel: Tugas / PIC / Tenggat), (4) label mode yang digunakan ("Perangkum Ekstraktif" atau "BYOK Cloud"), (5) tombol "Proses Ulang" untuk menjalankan perangkum lagi dengan mode berbeda. |
| **Ref. PRD** | F-07 (Tab Notulensi), US-04 |
| **Kriteria Selesai** | Tab Notulensi menampilkan ketiga bagian notulensi. Tombol "Proses Ulang" memicu perangkuman ulang. |
| **Estimasi** | 3 jam |

### F3-06: Pemutar Audio Mini Sinkron Teks

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `MiniPlayer` *composable* di bagian bawah `DetailScreen`: (1) putar/jeda, (2) bilah geser (*seek bar*), (3) kontrol kecepatan (0,5x / 1x / 1,5x / 2x), (4) ketuk segmen di Tab Transkrip → `MediaPlayer` / `ExoPlayer` lompat ke `startMs` segmen. Dekripsi berkas audio ke *buffer* sementara untuk pemutaran. |
| **Ref. PRD** | F-08 (v2 ID), FR-09 (Pemutar Audio), US-05, AC-05.1, AC-05.2 |
| **Kriteria Selesai** | Ketuk segmen → audio memutar dari detik yang tepat (±1 detik). Kecepatan pemutaran bisa diubah. |
| **Estimasi** | 3 jam |

**Total Fase 3: ~18 jam**

---

## Fase 4 — Ekspor, Pencarian & Polish

**Tujuan:** Pengguna bisa mengekspor hasil ke PDF, mencari di transkrip, dan aplikasi siap untuk rilis beta.

### F4-01: Ekspor PDF

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `PdfGenerator` dan `ExportPdfUseCase`: (1) buat dokumen PDF A4 menggunakan `Android PdfDocument` API, (2) **Bagian A** — Notulensi: header (judul, tanggal, durasi), ringkasan, keputusan, tabel tugas, (3) **Bagian B** — Transkrip Asli Lengkap: semua segmen dengan tanda waktu, (4) *footer* halaman dengan nomor halaman, (5) simpan ke Downloads atau bagikan via ShareSheet. |
| **Ref. PRD** | F-09, FR-10 (v2 ID), US-06, AC-06.1 – AC-06.3 |
| **Ref. Tech Spec** | Bagian 8 |
| **Kriteria Selesai** | PDF berisi kedua bagian (notulensi + transkrip asli). PDF terbaca di penampil PDF Android. Bagikan via WhatsApp → PDF terkirim. |
| **Estimasi** | 4 jam |

### F4-02: Ekspor Markdown & Teks Biasa

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Tambahkan opsi ekspor `.md` (Markdown terformat) dan `.txt` (teks biasa) selain PDF. Simpan ke folder Downloads. Bagikan via ShareSheet. |
| **Ref. PRD** | AC-06.4 |
| **Kriteria Selesai** | Berkas `.md` dan `.txt` tersimpan di Downloads. Isi lengkap (notulensi + transkrip asli). |
| **Estimasi** | 2 jam |

### F4-03: Pencarian Teks Transkrip (FTS4)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `SearchTranscriptUseCase` dan UI pencarian: (1) buat tabel FTS4 `transcript_segments_fts`, (2) kolom pencarian di HomeScreen, (3) hasil menampilkan: judul rekaman, *snippet* kalimat, tanda waktu, (4) ketuk hasil → navigasi ke segmen di DetailScreen. |
| **Ref. PRD** | F-11, FR-12 (v2 ID), US-07, AC-07.1, AC-07.2 |
| **Kriteria Selesai** | Ketik "deployment" → hasil dari semua rekaman muncul < 1 detik. Ketuk → navigasi ke segmen. |
| **Estimasi** | 3 jam |

### F4-04: Layar Pengaturan (Settings)

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | Implementasikan `SettingsScreen`: (1) pilihan mode perangkum (Ekstraktif / BYOK Cloud), (2) input & simpan kunci API BYOK (terenkripsi Keystore), (3) kelola model (status unduhan, hapus model, unduh ulang), (4) kosakata kustom default (global), (5) info versi aplikasi & ukuran data. |
| **Ref. PRD** | F-04, F-12, F-13 |
| **Kriteria Selesai** | Pengaturan tersimpan persisten. Kunci API tersimpan terenkripsi. Hapus model → model terhapus, transkripsi tidak bisa jalan sampai unduh ulang. |
| **Estimasi** | 3 jam |

### F4-05: Penyempurnaan UI & Aksesibilitas

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | (1) *Empty state* yang informatif di HomeScreen (ilustrasi + ajakan impor), (2) animasi transisi antar layar, (3) *content description* untuk aksesibilitas (TalkBack), (4) validasi kontras WCAG AA (tes sudah ada), (5) *loading skeleton* saat data dimuat, (6) *dark mode* lengkap. |
| **Ref. PRD** | NFR-09 |
| **Kriteria Selesai** | Dark mode lengkap tanpa elemen yang tidak terlihat. TalkBack membaca semua elemen interaktif. Tes WCAG lulus. |
| **Estimasi** | 2 jam |

### F4-06: QA Akhir & Rilis Beta

| Atribut | Detail |
| :--- | :--- |
| **Deskripsi** | (1) Jalankan seluruh alur di TECNO CN5: impor M4A/MP3/WAV → transkrip → ringkasan → ekspor PDF → bagikan, (2) tes performa (ukur waktu transkripsi 5 menit, 30 menit), (3) tes memori (Android Profiler: RSS ≤ 1,5 GB), (4) tes termal (transkripsi 30 menit, pantau throttling), (5) tes *force stop* → data aman, (6) tes instalasi bersih (*clean install*), (7) tes 5 format audio dari 5 aplikasi perekam berbeda, (8) buat APK rilis (ukur ukuran ≤ 15 MB tanpa model). |
| **Ref. PRD** | Semua NFR, semua SM |
| **Kriteria Selesai** | Semua tes manual lulus. APK ≤ 15 MB. 0 *crash*. Waktu transkripsi sesuai estimasi. |
| **Estimasi** | 2 jam (eksekusi tes manual) |

**Total Fase 4: ~16 jam**

---

## Diagram Ketergantungan Antar Fase

```
Fase 0 (SELESAI)
    │
    ▼
Fase 1 (Impor & DB)
    │
    ├──────────────────────┐
    ▼                      ▼
Fase 2 (Whisper)      F1-08 (Enkripsi)
    │                      │
    ▼                      │
Fase 3 (Perangkum)◄───────┘
    │
    ▼
Fase 4 (Ekspor & QA)
```

**Catatan:** Fase 2 dan sebagian Fase 1 (F1-08) bisa dikerjakan secara paralel oleh pengembang berbeda. Fase 3 bergantung pada Fase 2 (butuh segmen transkrip untuk dirangkum). Fase 4 bergantung pada Fase 3 (butuh notulensi untuk diekspor).

---

## Rencana Pasca-MVP (v1.1+)

Fitur-fitur berikut akan dievaluasi setelah MVP stabil dan mendapat umpan balik pengguna:

| Prioritas | Fitur | Estimasi | Catatan |
| :---: | :--- | :--- | :--- |
| 1 | **Perekaman audio bawaan** (kode sudah ada di `legacy/`) | ~8 jam | Migrasi kode perekam + integrasi ke alur baru |
| 2 | **SLM di perangkat untuk perangkuman** (Qwen 2.5 3B Q4) | ~12 jam | Evaluasi perbandingan Ekstraktif vs SLM pada 2–3 transkrip nyata |
| 3 | **Diarisasi pembicara** (*speaker diarization*) | ~16 jam | Tambahan model pyannote atau NeMo |
| 4 | **Model Whisper yang lebih kecil** (Q4, ~405 MB) sebagai opsi | ~2 jam | Untuk perangkat RAM 6 GB |
| 5 | **Transkripsi langsung (*live transcription*)** | ~20 jam | Mode *streaming* Whisper |

---

> **Status Dokumen:** DRAFT v2.0.0
> **Langkah Selanjutnya:** Review → Approval → Mulai implementasi Fase 1
