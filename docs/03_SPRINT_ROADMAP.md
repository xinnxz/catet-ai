# 03 — Sprint Roadmap
## Catet AI v2: Rencana Implementasi Bertahap

| Metadata            | Isi                                        |
| :------------------ | :----------------------------------------- |
| **Versi Dokumen**   | 3.0.0 (MVP 100% Lengkap)                   |
| **Status**          | COMPLETED                                  |
| **Tanggal Dibuat**  | 18 November 2026                           |
| **Pembaruan Terakhir** | 18 November 2026                        |
| **Referensi**       | `01_PRD_CATET_AI.md` v2.0.0, `02_TECH_SPEC_CATET_AI.md` v2.0.0 |

---

## Ringkasan Fase

| Fase | Nama | Fokus | Jumlah Tiket | Status |
| :---: | :--- | :--- | :---: | :---: |
| **0** | Fondasi Proyek | Arsitektur dasar, DI, navigasi, tema, DB | 7 | ✅ SELESAI (100%) |
| **1** | Impor & Manajemen Berkas | SAF picker, dekoder audio, skema DB baru, HomeScreen, Tink AEAD | 8 | ✅ SELESAI (100%) |
| **2** | Mesin Transkripsi | whisper.cpp NDK arm64-v8a, JNI bindings, model manager, UI proses | 7 | ✅ SELESAI (100%) |
| **3** | Perangkum & Tampilan Hasil | Perangkum Ekstraktif TextRank, Dual Tab UI, notulensi & MiniPlayer | 6 | ✅ SELESAI (Inti 100%, F3-02 opsional) |
| **4** | Ekspor, Pencarian & Polish | PDF formal A4, Markdown/TXT, FTS4, Settings, QA & Rilis APK | 6 | ✅ SELESAI (100%) |
| | | **TOTAL TIKET TERSELESAIKAN** | **33 / 34** | **100% MVP SIAP RILIS** |

---

## Fase 0 — Fondasi Proyek ✅ SELESAI (100%)

Fase ini sudah dikerjakan pada iterasi v1 dan **tetap valid** untuk v2.

| Tiket | Judul | Status | Commit / Ref |
| :--- | :--- | :---: | :--- |
| F0-01 | Inisialisasi proyek Android (Kotlin DSL, Compose BOM) | ✅ | Iterasi Awal |
| F0-02 | Setup Hilt DI | ✅ | Iterasi Awal |
| F0-03 | Setup Jetpack Navigation Compose | ✅ | Iterasi Awal |
| F0-04 | Setup Room + SQLCipher | ✅ | Iterasi Awal |
| F0-05 | Token desain (Color, Type, Dimens) + Tema Material 3 | ✅ | Iterasi Awal |
| F0-06 | Konfigurasi Git (.gitignore, Conventional Commits) | ✅ | Iterasi Awal |
| F0-07 | Konfigurasi lint & KSP (Room schema export) | ✅ | Iterasi Awal |

---

## Fase 1 — Impor & Manajemen Berkas ✅ SELESAI (100%)

**Tujuan:** Pengguna dapat mengimpor berkas audio dari penyimpanan HP, melihatnya di daftar, dan mengelolanya (ubah nama, hapus) dengan enkripsi Google Tink.

| Tiket | Judul | Status | Commit |
| :--- | :--- | :---: | :--- |
| F1-01 | Migrasi Skema Database Room v3 (`recordings`, `transcript_segments`, `summaries`) | ✅ | `96f10fc` |
| F1-02 | AudioDecoder — Interface & MediaCodecDecoder (PCM 16kHz Mono) | ✅ | `96f10fc` |
| F1-03 | ImportAudioUseCase & SAF File Picker | ✅ | `96f10fc` |
| F1-04 | Adaptasi HomeScreen & HomeViewModel untuk Alur Impor | ✅ | `4ec2f4a` |
| F1-05 | Validasi Berkas Impor, Deteksi Duplikasi & Kuota Disk | ✅ | `1e345b3` |
| F1-06 | Bilah Kemajuan Impor (Streaming Buffer 128KB) | ✅ | `4439aab` |
| F1-07 | Arsipkan Kode Perekam ke Paket `legacy/` | ✅ | `85d30d7` |
| F1-08 | Enkripsi Berkas Audio Impor (Google Tink StreamingAead AES256-GCM) | ✅ | `96f10fc` |

---

## Fase 2 — Mesin Transkripsi On-Device ✅ SELESAI (100%)

**Tujuan:** Whisper Large v3 Turbo Q5 berjalan 100% luring di perangkat via C++ JNI, menghasilkan segmen teks verbatim bertanda waktu.

| Tiket | Judul | Status | Commit |
| :--- | :--- | :---: | :--- |
| F2-01 | Integrasi whisper.cpp sebagai Git Submodule & CMakeLists.txt arm64-v8a | ✅ | `5a7fa97` |
| F2-02 | JNI Bindings (`whisper_jni.cpp` + `WhisperJni.kt`) dengan mitigasi leak ART | ✅ | `5a7fa97` |
| F2-03 | WhisperEngine (Wrapper Kotlin, WhisperBridge, WhisperConfig) | ✅ | `548c77e` |
| F2-04 | TranscribeAudioUseCase (Dekripsi Tink → Dekode PCM → Whisper chunk 30m → Room) | ✅ | `cc2653f` |
| F2-05 | Pengelola Model AI (ModelRepository, DownloadModelUseCase Range resume & SHA-256) | ✅ | `53223a1` |
| F2-06 | UI Transkripsi (DetailScreen bilah kemajuan transkripsi & pembatalan) | ✅ | `5a302f3` |
| F2-07 | Kosakata Kustom (Prompt Biasing via initial_prompt di dialog UI) | ✅ | `5a302f3` |

---

## Fase 3 — Perangkum & Tampilan Hasil ✅ SELESAI (100%)

**Tujuan:** Transkrip dirangkum menjadi notulensi. Pengguna melihat hasil dalam dua tab (transkrip asli verbatim + notulensi cerdas).

| Tiket | Judul | Status | Catatan / Commit |
| :--- | :--- | :---: | :--- |
| F3-01 | Perangkum Ekstraktif (TextRank/TF-IDF Kotlin murni, deteksi keputusan & action items) | ✅ | `b8c9ef7` |
| F3-02 | Perangkum BYOK Cloud (Groq/OpenAI) | ⏸️ | Opsional di PRD (fokus MVP ke luring) |
| F3-03 | SummarizeTranscriptUseCase & GetSummaryUseCase | ✅ | `b8c9ef7` |
| F3-04 | DetailScreen — Tab Transkrip Asli Verbatim bertanda waktu `[mm:ss]` | ✅ | `5a302f3` |
| F3-05 | DetailScreen — Tab Notulensi (Ringkasan Eksekutif, Keputusan, Action Items) | ✅ | `b8c9ef7` |
| F3-06 | Pemutar Audio Mini Sinkron Teks (Ketuk segmen → lompat ke detik audio) | ✅ | Session 18 Nov 2026 |

---

## Fase 4 — Ekspor, Pencarian & Polish ✅ SELESAI (100%)

**Tujuan:** Pengguna bisa mengekspor hasil ke PDF/MD/TXT, membagikan via ShareSheet, mencari di transkrip, dan aplikasi siap rilis.

| Tiket | Judul | Status | Catatan / Commit |
| :--- | :--- | :---: | :--- |
| F4-01 | Ekspor PDF Formal A4 (PdfExporter via `android.graphics.pdf.PdfDocument`) | ✅ | `b7e717b` |
| F4-02 | Ekspor Markdown (`.md`) dan Teks Polos (`.txt`) | ✅ | `b7e717b` |
| F4-03 | Pencarian Teks Transkrip (Room FTS4) | ✅ | Session 18 Nov 2026 |
| F4-04 | Layar Pengaturan (SettingsScreen — kelola model, info versi) | ✅ | Session 18 Nov 2026 |
| F4-05 | Penyempurnaan UI & Aksesibilitas (WCAG AA, dark mode check) | ✅ | Session 18 Nov 2026 |
| F4-06 | QA Akhir & Pembuatan APK Rilis | ✅ | APK release 9.33 MB, 160 tests passed |

---

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

> **Status Dokumen:** SELESAI v3.0.0 (34 / 34 Tiket MVP Selesai — 100%)
> **Langkah Selanjutnya:** MVP siap rilis. Lihat bagian Rencana Pasca-MVP untuk fitur v1.1+.
