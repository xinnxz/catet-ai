# 01 — Product Requirements Document (PRD)
## Catet AI v2: Transkripsi & Notulensi Rapat Privat Berbasis Impor

| Metadata            | Isi                                        |
| :------------------ | :----------------------------------------- |
| **Versi Dokumen**   | 2.0.0                                      |
| **Status**          | DRAFT — Menunggu Review & Approval         |
| **Penulis**         | Luthfi Fahmi Alviansyah                    |
| **Tanggal Dibuat**  | 18 November 2026                           |
| **Revisi Terakhir** | 18 November 2026                           |
| **Target Rilis MVP**| Q1 2027                                    |
| **Platform**        | Android (Kotlin + Jetpack Compose)         |
| **Referensi Produk**| [Vibe](https://github.com/thewh1teagle/vibe) (aplikasi transkripsi desktop) |

---

## Daftar Isi

1. [Ringkasan Eksekutif](#1-ringkasan-eksekutif)
2. [Latar Belakang & Pernyataan Masalah](#2-latar-belakang--pernyataan-masalah)
3. [Visi & Proposisi Nilai](#3-visi--proposisi-nilai)
4. [Target Pengguna](#4-target-pengguna)
5. [Alur Utama Pengguna](#5-alur-utama-pengguna)
6. [Analisis Kompetitor](#6-analisis-kompetitor)
7. [Batasan Ruang Lingkup](#7-batasan-ruang-lingkup)
8. [User Stories & Acceptance Criteria](#8-user-stories--acceptance-criteria)
9. [Kebutuhan Fungsional](#9-kebutuhan-fungsional)
10. [Kebutuhan Non-Fungsional](#10-kebutuhan-non-fungsional)
11. [Spesifikasi Model AI](#11-spesifikasi-model-ai)
12. [Strategi Perangkuman](#12-strategi-perangkuman)
13. [Skenario Tepi & Penanganan Kegagalan](#13-skenario-tepi--penanganan-kegagalan)
14. [Metrik Keberhasilan](#14-metrik-keberhasilan)
15. [Asumsi & Ketergantungan](#15-asumsi--ketergantungan)
16. [Risiko & Mitigasi](#16-risiko--mitigasi)
17. [Glosarium](#17-glosarium)

---

## 1. Ringkasan Eksekutif

**Catet AI** adalah aplikasi Android yang mengubah berkas rekaman suara rapat menjadi notulensi terstruktur (transkrip bertanda waktu, ringkasan, keputusan, dan daftar tugas) secara otomatis, privat, dan luring.

### Alur Inti (Empat Langkah)

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  1. REKAM   │───▶│  2. IMPOR   │───▶│ 3. PROSES   │───▶│ 4. EKSPOR   │
│  (App Lain) │    │ (Catet AI)  │    │ (On-Device) │    │   (PDF)     │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
  Perekam Suara      Pilih berkas      Whisper LV3T       Unduh/Bagikan
  bawaan HP,         audio dari        Q5 → Transkrip     dokumen PDF
  Voice Recorder,    penyimpanan HP    → Ringkasan        lengkap
  atau app lain      atau app lain     otomatis
```

**Perbedaan utama dari versi sebelumnya (v1):** Aplikasi **tidak lagi merekam audio sendiri**. Pengguna merekam menggunakan aplikasi perekam suara mana pun yang sudah ada di perangkatnya, lalu mengimpor berkas hasil rekaman ke Catet AI untuk diproses. Pendekatan ini menyederhanakan arsitektur secara drastis (menghilangkan `ForegroundService`, `AudioRecord`, `WakeLock`, penanganan interupsi mikrofon) dan memungkinkan rilis MVP lebih cepat.

**Model AI:** Whisper Large v3 Turbo yang dikompresi (*quantized*) ke format GGUF Q5 (~574 MB), dijalankan sepenuhnya di dalam perangkat melalui pustaka `whisper.cpp` dengan ikatan JNI (*JNI bindings*). Kompresi ini menurunkan kebutuhan RAM dari ~2 GB (versi asli FP16) menjadi ~750 MB – 1 GB, sehingga aman untuk perangkat dengan RAM 8 GB ke atas, dengan penurunan akurasi yang sangat kecil (selisih WER < 1%).

Seluruh data — berkas audio impor, transkripsi, dan notulensi — tersimpan 100% di perangkat pengguna, terenkripsi, dan tidak pernah dikirim ke server mana pun (kecuali pengguna secara eksplisit memilih mode BYOK Cloud).

---

## 2. Latar Belakang & Pernyataan Masalah

### 2.1 Konteks Pasar

Pasar global aplikasi Meeting Intelligence diproyeksikan tumbuh dari $2,1 miliar (2024) menjadi $7,5 miliar (2030). Namun, mayoritas solusi yang tersedia (Fireflies, Otter, Fathom, Tactiq) berjalan sepenuhnya di komputasi awan dan menyimpan data percakapan di server luar negeri.

Di sisi lain, aplikasi transkripsi berbasis perangkat lokal seperti **Vibe** (desktop, Rust/Tauri) telah membuktikan bahwa model Whisper dapat dijalankan secara luring dengan hasil akurat. Namun, Vibe hanya tersedia untuk desktop — belum ada padanannya di platform Android.

### 2.2 Masalah Nyata yang Ditemukan

Berdasarkan pengalaman langsung (sesi *onboarding engineering* di startup DASH, 16 November 2026):

| No | Masalah | Bukti Nyata |
| :--: | :--- | :--- |
| **M-01** | **Istilah lokal salah diartikan oleh STT internasional.** | Model Whisper `small` mentranskrip kata "MagangHub" menjadi "semang-manggung hati", dan "WFO/WFH" menjadi "Web O & Web A". |
| **M-02** | **Tidak ada solusi transkripsi rapat yang privat di Indonesia.** | Perusahaan sektor keuangan, hukum, dan pemerintahan di Indonesia secara eksplisit melarang pengiriman rekaman rapat ke server asing. |
| **M-03** | **Biaya langganan cloud terlalu mahal untuk individu dan tim kecil.** | Fireflies Pro = $10/bulan/pengguna, Business = $29/bulan/pengguna. Tim 10 orang mengeluarkan $290/bulan (~Rp 4,6 juta) hanya untuk notulensi. |
| **M-04** | **Aplikasi transkripsi lokal hanya tersedia di desktop.** | Vibe, Buzz, MacWhisper hanya mendukung Windows/macOS/Linux. Pengguna Android tidak punya pilihan luring yang setara. |

### 2.3 Pernyataan Masalah

> Profesional dan tim kerja di Indonesia membutuhkan alat transkripsi dan notulensi rapat di **perangkat Android** yang **akurat terhadap konteks bahasa lokal**, **tidak mengirim data ke server asing**, dan **tidak membebankan biaya langganan server bulanan** — cukup impor berkas rekaman dan dapatkan hasilnya.

---

## 3. Visi & Proposisi Nilai

### 3.1 Pernyataan Visi Produk

> *"Vibe, tapi di saku Anda — impor rekaman, dapatkan notulensi, tanpa menyerahkan privasi."*

### 3.2 Proposisi Nilai

| Dimensi | Proposisi |
| :--- | :--- |
| **Privasi Mutlak** | Berkas audio, teks transkrip, dan notulensi tidak pernah meninggalkan perangkat pengguna. Tidak ada server *backend*, tidak ada telemetri audio. |
| **Model Terbaik, Di Perangkat** | Menggunakan Whisper Large v3 Turbo — model transkripsi terbaik dari OpenAI — yang dikompresi agar dapat berjalan langsung di perangkat Android tanpa internet. Akurasi setara versi *cloud* (selisih WER < 1%). |
| **Tanpa Biaya Server Berulang** | Komputasi AI menggunakan prosesor perangkat pengguna sendiri. Tidak ada server GPU yang harus dibayar setiap bulan. |
| **Alur Sederhana** | Empat langkah: rekam (di aplikasi mana pun) → impor → proses → ekspor PDF. Tidak perlu pengaturan rumit. |
| **Konteks Bahasa Indonesia** | Mendukung bahasa Indonesia, campuran Indonesia-Inggris, singkatan kantor (WFO, WFH, PIC, EOD, SPK), dan nama orang/perusahaan lokal melalui fitur kosakata kustom. |

---

## 4. Target Pengguna

### Persona A: Rania — Notulis Tim Startup (Primer)

| Atribut | Detail |
| :--- | :--- |
| **Usia & Peran** | 24 tahun, Junior Product Manager di startup teknologi Jakarta |
| **Kebiasaan** | Menghadiri 3–5 rapat per hari. Merekam pakai Voice Recorder bawaan Samsung. |
| **Frustrasi** | Menulis notulensi manual sering ketinggalan poin penting. Fireflies sering salah istilah internal. |
| **Kebutuhan** | Impor rekaman rapat → dapatkan notulensi otomatis dengan ringkasan, keputusan, dan PIC → kirim ke grup WhatsApp tim |
| **Perangkat** | Samsung Galaxy A55 (RAM 8 GB), Android 14 |

### Persona B: Pak Hendra — Kepala Divisi Hukum (Sekunder)

| Atribut | Detail |
| :--- | :--- |
| **Usia & Peran** | 48 tahun, Head of Legal & Compliance di perusahaan pembiayaan |
| **Kebiasaan** | Rapat negosiasi kontrak dan sidang internal yang sangat rahasia |
| **Frustrasi** | Dilarang keras menggunakan aplikasi yang mengirim data ke *cloud* asing |
| **Kebutuhan** | Alat transkripsi yang 100% luring dan terenkripsi di perangkatnya sendiri |
| **Perangkat** | Google Pixel 8 Pro (RAM 12 GB), Android 15 |

### Persona C: Dina — Mahasiswi Tingkat Akhir (Tersier)

| Atribut | Detail |
| :--- | :--- |
| **Usia & Peran** | 21 tahun, mahasiswi semester 7, sedang menyusun skripsi |
| **Kebiasaan** | Merekam penjelasan dosen pembimbing untuk didengarkan ulang |
| **Frustrasi** | Memutar ulang rekaman 1 jam hanya untuk mencari satu penjelasan yang dilupa |
| **Kebutuhan** | Transkrip bertanda waktu agar bisa langsung melompat ke bagian penjelasan tertentu |
| **Perangkat** | Xiaomi Redmi Note 12 (RAM 6 GB), Android 13 |

> **Catatan tentang Persona C:** Perangkat Dina memiliki RAM 6 GB. Model Whisper Large v3 Turbo Q5 membutuhkan ~750 MB – 1 GB RAM saat inferensi. Meskipun secara teknis masih bisa berjalan, performa mungkin lebih lambat akibat tekanan memori dari aplikasi lain. Untuk perangkat RAM 6 GB, mode BYOK Cloud direkomendasikan sebagai alternatif.

---

## 5. Alur Utama Pengguna

Bagian ini menjelaskan alur interaksi pengguna dari awal sampai akhir secara rinci.

### 5.1 Diagram Alur

```
┌──────────────────────────────────────────────────────────────────────┐
│                        ALUR PENGGUNA CATET AI                        │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ① REKAM (di luar Catet AI)                                         │
│  ├─ Buka aplikasi perekam suara bawaan HP (Voice Recorder, dll.)    │
│  ├─ Rekam percakapan rapat                                          │
│  └─ Selesai → berkas audio tersimpan di penyimpanan HP              │
│       Format: .m4a, .mp3, .wav, .ogg, .aac, .flac, .opus, .wma     │
│                                                                      │
│  ② IMPOR (di Catet AI)                                              │
│  ├─ Buka Catet AI → tekan tombol "＋ Impor Rekaman"                 │
│  ├─ Pemilih berkas sistem (SAF) terbuka                             │
│  ├─ Pilih berkas audio → Catet AI menyalin ke penyimpanan internal  │
│  └─ Berkas muncul di daftar rekaman dengan status "Siap Diproses"  │
│                                                                      │
│  ③ PROSES (otomatis atau manual)                                    │
│  ├─ Tekan "Transkrip" pada item rekaman                             │
│  ├─ [Opsional] Masukkan kosakata kustom (nama, singkatan)           │
│  ├─ Bilah kemajuan menampilkan persentase transkripsi               │
│  ├─ Whisper LV3T Q5 memproses: audio → segmen teks bertanda waktu  │
│  ├─ Setelah transkrip selesai → perangkum otomatis berjalan         │
│  └─ Hasil ditampilkan dalam DUA tab terpisah:                       │
│       Tab ① "Transkrip" — Teks asli kata-per-kata dari pembicara   │
│       Tab ② "Notulensi" — Ringkasan + Keputusan + Daftar Tugas     │
│                                                                      │
│  ④ TINJAU & EKSPOR                                                  │
│  ├─ Tab Transkrip: baca teks asli pembicara, ketuk segmen →        │
│  │   dengar audio di detik tersebut                                  │
│  ├─ Tab Notulensi: tinjau ringkasan dan daftar tugas                │
│  ├─ Tekan "Ekspor PDF" → dokumen PDF berisi KEDUA bagian:          │
│  │   transkrip asli lengkap + notulensi terstruktur                  │
│  └─ Bagikan via WhatsApp/Telegram/Email atau simpan ke Downloads    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.2 Penjelasan Langkah Kritis

**Langkah ② — Penyalinan Berkas:**
Saat pengguna memilih berkas audio melalui pemilih berkas sistem (*Storage Access Framework / SAF*), Catet AI **menyalin** berkas tersebut ke penyimpanan internal aplikasi (`app-specific storage`). Berkas asli di lokasi asal tidak diubah atau dihapus. Penyalinan ini penting karena:
- Menjamin berkas tetap tersedia meskipun pengguna menghapus berkas asli dari galeri/pengelola berkas.
- Penyimpanan internal aplikasi dilindungi *sandbox* Android — aplikasi lain tidak dapat mengaksesnya.

**Langkah ③ — Dekoding Audio:**
Model Whisper membutuhkan masukan dalam format PCM 16kHz 16-bit Mono. Karena pengguna dapat mengimpor berkas dalam berbagai format (M4A, MP3, OGG, dsb.), Catet AI harus mendekode berkas tersebut terlebih dahulu menggunakan `Android MediaCodec` API (perangkat keras, tanpa pustaka tambahan) atau `FFmpeg` (pustaka perangkat lunak, sebagai cadangan) sebelum meneruskannya ke Whisper.

---

## 6. Analisis Kompetitor

| Dimensi | Fireflies.ai | Otter.ai | Vibe (Desktop) | **Catet AI** |
| :--- | :--- | :--- | :--- | :--- |
| **Platform** | Web/Mobile (cloud) | Web/Mobile (cloud) | Desktop (Win/Mac/Linux) | **Android (lokal)** |
| **Penyimpanan Data** | Server AWS AS | Server AS | Lokal (desktop) | **Lokal (perangkat Android)** |
| **Mode Luring** | Tidak bisa | Tidak bisa | **Bisa** | **Bisa** |
| **Alur Masukan** | Bot masuk ke rapat daring | Bot masuk ke rapat daring | Impor berkas audio | **Impor berkas audio** |
| **Bahasa Indonesia** | Parsial, sering salah | Sangat terbatas | Didukung (Whisper) | **Fokus utama + kosakata kustom** |
| **Biaya** | $10–$29/pengguna/bulan | $8–$20/pengguna/bulan | Gratis (sumber terbuka) | **Gratis / Beli putus** |
| **Model STT** | Whisper (server) | Model internal | Whisper (lokal) | **Whisper LV3T Q5 (lokal)** |
| **Ekspor PDF** | Ya (berbayar) | Ya (berbayar) | Tidak (teks saja) | **Ya (gratis)** |
| **Perangkum Otomatis** | Ya (GPT cloud) | Ya (cloud) | Tidak ada | **Ya (lokal + opsional BYOK)** |
| **Diarisasi Pembicara** | Ya | Ya | Ya | **Pasca-MVP (v1.1+)** |

**Celah pasar Catet AI:** Satu-satunya aplikasi Android yang menggabungkan transkripsi luring bermutu tinggi (Whisper Large v3 Turbo) **plus** perangkum notulensi otomatis **plus** ekspor PDF — semuanya tanpa server dan tanpa biaya berulang.

---

## 7. Batasan Ruang Lingkup

### 7.1 MASUK LINGKUP (Wajib Ada di MVP v1.0)

| ID | Fitur | Deskripsi Singkat |
| :--: | :--- | :--- |
| **F-01** | **Impor Berkas Audio** | Pemilih berkas sistem (*SAF*) untuk memilih berkas audio dari penyimpanan HP. Mendukung format: WAV, M4A, MP3, OGG, AAC, FLAC, OPUS, WMA. Berkas disalin ke penyimpanan internal aplikasi. |
| **F-02** | **Dekoding Audio** | Mengubah berkas audio dari berbagai format ke PCM 16kHz 16-bit Mono yang dibutuhkan oleh Whisper. Menggunakan `Android MediaCodec` (utama) atau `FFmpeg` (cadangan). |
| **F-03** | **Transkripsi Suara Luring** | Mengubah audio menjadi teks bertanda waktu per segmen kalimat menggunakan Whisper Large v3 Turbo Q5 (~574 MB) melalui `whisper.cpp` dengan ikatan JNI. Sepenuhnya luring, tanpa internet. |
| **F-04** | **Kosakata Kustom** | Masukan istilah teknis, nama peserta rapat, dan singkatan lokal sebelum transkripsi untuk memandu *decoder* Whisper agar terhindar dari salah tafsir istilah (parameter `initial_prompt`). |
| **F-05** | **Perangkum Notulensi Otomatis** | Menghasilkan ringkasan rapat, daftar keputusan, dan daftar tugas dari transkrip. Mendukung dua mode: (a) Perangkum Ekstraktif Kotlin murni (TextRank/TF-IDF, instan, tanpa model tambahan), (b) BYOK Cloud (kunci API pengguna sendiri, kualitas tinggi). |
| **F-06** | **Manajemen Rekaman** | Daftar rekaman yang telah diimpor beserta metadata (judul, durasi, tanggal impor, ukuran berkas, status pemrosesan). Mendukung ubah nama dan hapus. |
| **F-07** | **Tampilan Dua Tab Hasil** | Layar hasil menampilkan dua tab terpisah: (a) **Tab "Transkrip"** — teks asli kata-per-kata dari pembicara, bertanda waktu per segmen kalimat, menampilkan persis apa yang diucapkan tanpa perubahan; (b) **Tab "Notulensi"** — ringkasan, keputusan, dan daftar tugas yang dihasilkan perangkum otomatis. Kedua tab dapat digulir secara independen. |
| **F-08** | **Pemutar Audio Sinkron Teks** | Ketuk segmen kalimat di tab Transkrip → audio memutar dari detik kalimat tersebut diucapkan. Kontrol pemutar: putar/jeda, geser (*seek*), kecepatan (0,5x / 1x / 1,5x / 2x). |
| **F-09** | **Ekspor PDF** | Menghasilkan dokumen PDF yang berisi **kedua** bagian: (Bagian A) transkrip asli lengkap bertanda waktu, dan (Bagian B) notulensi terstruktur (ringkasan, keputusan, daftar tugas). Mendukung penyimpanan ke folder Unduhan (*Downloads*) dan pembagian via Android *ShareSheet*. |
| **F-10** | **Penyimpanan Lokal Terenkripsi** | Basis data Room dienkripsi dengan SQLCipher (AES-256-CBC). Berkas audio impor dienkripsi saat istirahat (*at-rest*) menggunakan Google Tink `StreamingAead` (AES256-GCM-HKDF-4KB). Kunci enkripsi disimpan di Android Keystore. |
| **F-11** | **Pencarian Transkrip** | Pencarian teks bebas di seluruh transkrip yang tersimpan menggunakan Room FTS4. |
| **F-12** | **Pengelolaan Model** | Unduh model Whisper LV3T Q5 (~574 MB) saat pertama kali dibutuhkan. Tampilkan kemajuan unduhan. Deteksi apakah model sudah tersedia sebelum transkripsi. |
| **F-13** | **Mode BYOK Cloud (Opsional)** | Pengguna memasukkan kunci API Groq atau OpenAI sendiri. Audio dikirim ke API untuk transkripsi dan/atau perangkuman. Hasil disimpan hanya di lokal. Kunci API disimpan terenkripsi di Android Keystore. |

### 7.2 DI LUAR LINGKUP (Ditunda ke Versi Berikutnya)

| ID | Fitur yang Ditunda | Alasan Penundaan |
| :--: | :--- | :--- |
| **X-01** | **Perekaman audio bawaan di dalam aplikasi** | Arsitektur impor lebih sederhana. Fitur perekam bisa ditambahkan di v1.1 menggunakan kode yang sudah ada di kode sumber. |
| **X-02** | **Diarisasi pembicara (*speaker diarization*)** | Menambah kompleksitas signifikan. Ditunda ke v1.1. |
| **X-03** | **Bot masuk ke Zoom/Google Meet/Teams** | Membutuhkan infrastruktur server yang mahal. Bukan urusan MVP. |
| **X-04** | **Sinkronisasi data antar perangkat** | Melanggar prinsip *local-only* di tahap awal. |
| **X-05** | **Versi iOS** | Fokus platform pertama adalah Android. |
| **X-06** | **Transkripsi langsung saat sedang merekam (*live transcription*)** | Membutuhkan optimasi *streaming inference* yang berat. Alur impor lebih stabil dan akurat. |
| **X-07** | **Model Bahasa Kecil (SLM) di perangkat untuk perangkuman** | Membutuhkan tambahan ~2 GB RAM dan ~2 GB penyimpanan. Kompleksitas tinggi untuk MVP. Dievaluasi untuk v1.1 setelah MVP stabil. |

---

## 8. User Stories & Acceptance Criteria

### US-01: Mengimpor Berkas Rekaman

> **Sebagai** Rania (notulis tim),
> **Saya ingin** mengimpor berkas rekaman rapat dari aplikasi perekam suara bawaan HP saya ke dalam Catet AI,
> **Sehingga** rekaman tersebut siap diproses menjadi notulensi otomatis.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-01.1 | Tombol "Impor Rekaman" tersedia di layar utama. Menekannya membuka pemilih berkas sistem Android (*SAF Intent*). | Verifikasi visual: pemilih berkas muncul. |
| AC-01.2 | Mendukung format: WAV, M4A, MP3, OGG, AAC, FLAC, OPUS, WMA. Berkas di luar format ini ditolak dengan pesan kesalahan yang jelas. | Uji impor setiap format. Uji impor berkas `.pdf` → harus ditolak. |
| AC-01.3 | Berkas audio disalin ke penyimpanan internal aplikasi. Berkas asli tidak diubah atau dihapus. | Verifikasi: berkas asli masih ada. Berkas salinan ada di `/data/data/com.catetai/...`. |
| AC-01.4 | Selama penyalinan berkas besar (>100 MB), tampilkan bilah kemajuan (*progress bar*) yang menunjukkan persentase. | Uji dengan berkas WAV 200 MB. |
| AC-01.5 | Setelah impor berhasil, rekaman muncul di daftar dengan status "Siap Diproses" beserta metadata: nama berkas asli (sebagai judul), durasi, ukuran, dan tanggal impor. | Verifikasi visual dan data di basis data. |
| AC-01.6 | Aplikasi juga dapat menerima berkas audio melalui Android *ShareSheet* (niat berbagi / *share intent*) dari aplikasi perekam atau pengelola berkas lain. | Buka aplikasi perekam → pilih rekaman → Bagikan → pilih Catet AI → berkas diimpor. |

---

### US-02: Melihat dan Mengelola Daftar Rekaman

> **Sebagai** pengguna,
> **Saya ingin** melihat semua rekaman yang telah diimpor dalam daftar yang terurut berdasarkan tanggal terbaru,
> **Sehingga** saya bisa menemukan rekaman yang saya cari dengan cepat.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-02.1 | Daftar rekaman menampilkan: judul, tanggal impor, durasi, ukuran berkas, dan status pemrosesan (Siap Diproses / Sedang Diproses / Selesai / Gagal). | Verifikasi visual: semua kolom terisi. |
| AC-02.2 | Pengguna bisa mengubah judul rekaman. | Ketuk judul → ubah → simpan → verifikasi judul berubah. |
| AC-02.3 | Pengguna bisa menghapus rekaman (dengan dialog konfirmasi). Penghapusan menghapus berkas audio **dan** data transkrip terkait secara permanen. | Hapus satu rekaman, verifikasi berkas dan entri basis data terhapus. |
| AC-02.4 | Geser ke kiri (*swipe-to-delete*) tersedia tetapi diblokir untuk status "Sedang Diproses". | Coba geser item berstatus "Sedang Diproses" → tidak terjadi apa-apa. |

---

### US-03: Mentranskrip Rekaman Menjadi Teks

> **Sebagai** Rania,
> **Saya ingin** rekaman rapat yang saya impor diubah menjadi teks transkrip secara otomatis dengan akurasi tinggi,
> **Sehingga** saya bisa membaca isi rapat tanpa harus memutar ulang audionya.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-03.1 | Tekan tombol "Transkrip" pada item rekaman. Bilah kemajuan menampilkan persentase (0% → 100%). | Verifikasi visual. |
| AC-03.2 | Setiap segmen kalimat dalam transkrip memiliki tanda waktu awal dalam format `[MM:SS]`. | Verifikasi: ketuk tanda waktu → audio berputar di detik tersebut. |
| AC-03.3 | Pada audio rekaman rapat berbahasa Indonesia yang jernih (SNR ≥ 20 dB, jarak mikrofon ≤ 1 meter), akurasi transkripsi (WER) ≤ 15%. | Bandingkan transkrip otomatis dengan transkrip manual sebagai acuan. |
| AC-03.4 | Fitur **Kosakata Kustom** berfungsi: masukkan istilah "MagangHub, Dash, WFO, WFH" sebelum transkripsi → kata-kata tersebut muncul benar di transkrip. | Uji dengan audio DASH *onboarding*. |
| AC-03.5 | Proses transkripsi dapat dibatalkan di tengah jalan tanpa menyebabkan *crash* atau berkas rusak. | Tekan "Batal" di kemajuan 50%, verifikasi aplikasi kembali normal. |
| AC-03.6 | Waktu transkripsi untuk rekaman 5 menit ≤ 45 detik, 30 menit ≤ 5 menit, 1 jam ≤ 10 menit (di perangkat RAM 8 GB+). | Ukur waktu di perangkat target (TECNO CN5). |

---

### US-04: Mendapatkan Ringkasan dan Notulensi Otomatis

> **Sebagai** Rania,
> **Saya ingin** mendapatkan ringkasan rapat, keputusan, dan daftar tugas beserta PIC secara otomatis dari transkrip,
> **Sehingga** saya bisa langsung membagikan notulensi ke tim tanpa menyusun ulang secara manual.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-04.1 | Setelah transkripsi selesai, tombol "Buat Notulensi" aktif. Perangkuman dimulai secara otomatis atau dengan satu ketukan. | Verifikasi visual. |
| AC-04.2 | Keluaran notulensi terdiri atas 3 bagian wajib: (a) **Ringkasan Rapat** (maks. 5 poin utama), (b) **Keputusan Rapat** (daftar bernomor), (c) **Daftar Tugas** (deskripsi tugas, PIC jika disebutkan, tenggat jika dinyatakan). | Verifikasi kelengkapan ketiga bagian. |
| AC-04.3 | **Pencegahan Halusinasi PIC:** Jika komitmen tugas diucapkan dengan kata ganti orang pertama ("saya", "aku") tanpa nama eksplisit, kolom PIC wajib tertulis *"Belum teridentifikasi (Pembicara)"*. Nama hanya boleh dicantumkan jika disebutkan secara verbal dalam dialog atau terdaftar di Kosakata Kustom. | Uji dengan ujaran "Bagian API besok saya yang bereskan". Pastikan PIC bukan nama acak. |
| AC-04.4 | Notulensi tidak mengandung fakta palsu yang tidak ada di dalam transkrip. | Tinjau manual: bandingkan setiap butir notulensi dengan isi transkrip. |

---

### US-05: Memutar Audio dari Teks Transkrip

> **Sebagai** Pak Hendra,
> **Saya ingin** mengetuk kalimat di transkrip dan langsung mendengar audio aslinya di detik tersebut,
> **Sehingga** saya bisa memverifikasi sendiri apakah transkrip sudah benar untuk kata-kata kritis.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-05.1 | Mengetuk segmen kalimat di transkrip langsung memutar audio mulai dari tanda waktu segmen tersebut (toleransi ±1 detik). | Audio yang terdengar sesuai dengan teks yang diketuk. |
| AC-05.2 | Terdapat kontrol pemutar mini: putar/jeda, bilah geser (*seek bar*), kecepatan 0,5x / 1x / 1,5x / 2x. | Verifikasi visual dan fungsional. |

---

### US-06: Mengekspor Notulensi ke PDF

> **Sebagai** Rania,
> **Saya ingin** mengekspor hasil notulensi rapat ke berkas PDF yang terformat rapi,
> **Sehingga** saya bisa mengirimnya ke grup WhatsApp tim atau menyimpannya sebagai arsip resmi.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-06.1 | Tombol "Ekspor PDF" menghasilkan berkas PDF yang berisi **dua bagian utama**: **(Bagian A) Transkrip Asli** — teks kata-per-kata dari pembicara, lengkap bertanda waktu per segmen, dan **(Bagian B) Notulensi** — ringkasan rapat, keputusan, dan daftar tugas. Disertai pula header berisi judul rapat, tanggal, dan durasi. | Buka PDF di penampil PDF, verifikasi kedua bagian ada dan lengkap. |
| AC-06.2 | PDF terformat rapi: judul tebal, poin bernomor, pemisah bagian, tanda waktu di margin. | Verifikasi visual. |
| AC-06.3 | Setelah PDF dihasilkan, pengguna bisa memilih: (a) "Simpan ke Downloads" atau (b) "Bagikan" (membuka Android *ShareSheet* untuk mengirim ke WhatsApp, Telegram, Email, dsb.). | Verifikasi kedua opsi berfungsi. |
| AC-06.4 | Tersedia juga opsi ekspor dalam format teks biasa (.txt) dan Markdown (.md) selain PDF. | Verifikasi ketiga format dapat dihasilkan. |

---

### US-07: Mencari di Seluruh Transkrip

> **Sebagai** Dina (mahasiswi),
> **Saya ingin** mencari kata kunci tertentu di seluruh transkrip yang pernah saya buat,
> **Sehingga** saya bisa menemukan penjelasan dosen tentang topik tertentu tanpa membuka satu per satu.

| ID | Kriteria Penerimaan | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-07.1 | Kolom pencarian di halaman utama menerima kata kunci dan menampilkan hasil dari semua rekaman yang mengandung kata tersebut, dalam waktu < 1 detik untuk 100 rekaman. | Ukur waktu respons. |
| AC-07.2 | Hasil pencarian menampilkan: judul rekaman, potongan kalimat (*snippet*) yang mengandung kata kunci, dan tanda waktu. Ketuk hasil → langsung ke segmen transkrip yang tepat. | Verifikasi navigasi dan sorotan kata. |

---

## 9. Kebutuhan Fungsional

| ID | Modul | Fungsi | Ref. US |
| :--: | :--- | :--- | :---: |
| FR-01 | Impor | Membuka pemilih berkas sistem (*SAF*) dengan filter MIME type `audio/*`. Menyalin berkas terpilih ke penyimpanan internal. Mendukung penerimaan berkas via *Share Intent*. | US-01 |
| FR-02 | Dekoder Audio | Mengubah berkas audio (M4A, MP3, OGG, AAC, FLAC, OPUS, WMA, WAV) ke format PCM 16kHz 16-bit Mono menggunakan `Android MediaCodec` API. Cadangan: `FFmpeg` (pustaka bawaan) untuk format yang tidak didukung `MediaCodec`. | US-01, US-03 |
| FR-03 | Mesin Transkripsi | Menjalankan model Whisper Large v3 Turbo Q5 (~574 MB GGUF) melalui `whisper.cpp` JNI untuk menghasilkan teks bertanda waktu per segmen kalimat. Memproses audio per *chunk* (30 detik, sesuai jendela Whisper) secara sekuensial. | US-03 |
| FR-04 | Mesin Transkripsi | Mendukung parameter `initial_prompt` untuk memasukkan kosakata kustom ke *decoder* Whisper. | US-03 |
| FR-05 | Mesin Transkripsi | Mendukung mode BYOK: pengguna memasukkan kunci API Groq/OpenAI, audio dikirim ke API untuk transkripsi. Kunci API disimpan terenkripsi di Android Keystore. | US-03 |
| FR-06 | Perangkum | Mode Ekstraktif: Perangkum berbasis TextRank/TF-IDF dalam Kotlin murni. Menghasilkan kutipan kunci + kandidat butir tindakan via filter aturan kata kunci ("tolong", "harus", "paling lambat", hari/tanggal). Tanpa model tambahan, instan (< 1 detik), < 50 MB RAM. | US-04 |
| FR-07 | Perangkum | Mode BYOK Cloud: Mengirim transkrip teks ke API LLM (Groq/OpenAI) menggunakan kunci pengguna untuk menghasilkan notulensi generatif penuh. Menerapkan *system prompt* anti-halusinasi PIC. | US-04 |
| FR-08 | Manajemen Data | CRUD rekaman (Buat saat impor, Baca daftar, Ubah judul, Hapus beserta berkas dan data terkait). | US-02 |
| FR-09 | Tampilan Hasil | Menyajikan keluaran pemrosesan dalam dua tab terpisah: (a) Tab "Transkrip" menampilkan teks asli verbatim bertanda waktu per segmen kalimat; (b) Tab "Notulensi" menampilkan ringkasan, keputusan, dan daftar tugas. | US-03, US-04 |
| FR-10 | Pemutar Audio | Pemutar audio dengan kemampuan lompat ke tanda waktu tertentu (dari ketukan teks transkrip) dan kontrol kecepatan. | US-05 |
| FR-11 | Ekspor | Menghasilkan berkas PDF dengan dua bagian: (A) transkrip asli lengkap bertanda waktu, (B) notulensi terstruktur. Juga menghasilkan `.md` dan `.txt`. Mendukung penyimpanan ke Downloads dan *ShareSheet*. | US-06 |
| FR-12 | Pencarian | Pencarian teks bebas pada kolom teks transkrip menggunakan Room FTS4. | US-07 |
| FR-13 | Basis Data | Skema Room dengan tabel: `recordings`, `transcript_segments`, `summaries`. Enkripsi via SQLCipher (AES-256-CBC). `exportSchema = true`. | Semua |
| FR-14 | Pengelola Model | Mengunduh berkas model GGUF (~574 MB) dari sumber yang dikonfigurasi (Hugging Face). Menyimpan di penyimpanan internal. Menampilkan kemajuan unduhan. Memvalidasi integritas berkas (SHA-256). | US-03 |

---

## 10. Kebutuhan Non-Fungsional

| ID | Kategori | Kebutuhan | Metrik Terukur | Cara Pengujian |
| :--: | :--- | :--- | :--- | :--- |
| NFR-01 | **Performa** | Transkripsi berkas pada perangkat (model LV3T Q5): audio 5 menit selesai dalam ≤ 45 detik. | Rasio waktu proses vs durasi audio ≤ 0,15x. | Ukur di TECNO CN5 (RAM 8 GB). |
| NFR-02 | **Performa** | Transkripsi BYOK Cloud: audio 10 menit selesai dalam ≤ 30 detik. | Latensi ujung-ke-ujung ≤ 30 detik. | Ukur dengan koneksi WiFi standar. |
| NFR-03 | **Performa** | Perangkuman ekstraktif selesai dalam ≤ 1 detik untuk transkrip 10.000 kata. | Latensi ≤ 1 detik. | Ukur di perangkat target. |
| NFR-04 | **Memori** | Pemakaian RAM selama inferensi Whisper LV3T Q5 tidak melebihi 1,5 GB (RSS). | RSS ≤ 1.500 MB. | Android Profiler Memory. |
| NFR-05 | **Penyimpanan** | Ukuran APK dasar (tanpa model AI) ≤ 15 MB. Model diunduh terpisah. | APK ≤ 15 MB. | Ukur APK rilis. |
| NFR-06 | **Keamanan** | Seluruh data di penyimpanan internal terenkripsi (basis data: SQLCipher, berkas audio: Tink StreamingAead). | Berkas tidak terbaca tanpa kunci. | Coba buka `.db` dengan SQLite browser → harus gagal. |
| NFR-07 | **Keamanan** | Kunci API BYOK disimpan di Android Keystore (*hardware-backed* jika tersedia). | Kunci tidak tersimpan sebagai teks biasa. | Inspeksi SharedPreferences dan berkas internal. |
| NFR-08 | **Kompatibilitas** | Aplikasi berjalan di Android 8.0 (API 26) sampai Android 15 (API 35) tanpa *crash*. | 0 *crash* di rentang SDK 26–35. | Uji di emulator API 26, 30, 33, 35. |
| NFR-09 | **Aksesibilitas** | Rasio kontras warna teks terhadap latar belakang memenuhi standar WCAG 2.1 AA (≥ 4,5:1). | Rasio kontras ≥ 4,5:1 untuk semua pasangan warna. | Tes unit `WcagContrastTest` yang sudah ada. |
| NFR-10 | **Reliabilitas** | Jika aplikasi ditutup paksa (*force stop*) saat transkripsi sedang berjalan, data yang sudah diproses tidak hilang. Saat dibuka kembali, rekaman menunjukkan status "Terganggu — Proses Ulang?" | Data aman setelah *force stop*. | `adb shell am force-stop`, verifikasi data. |

---

## 11. Spesifikasi Model AI

### 11.1 Model Utama: Whisper Large v3 Turbo (Quantized Q5)

| Atribut | Spesifikasi |
| :--- | :--- |
| **Nama Model** | `whisper-large-v3-turbo` |
| **Arsitektur** | *Encoder-decoder transformer*. 809 juta parameter. Lapisan *decoder* dipangkas dari 32 menjadi 4 (dibanding Large v3 standar). |
| **Format Berkas** | GGUF Q5_0 (kompresi 5-bit) |
| **Ukuran Berkas** | ~574 MB |
| **RAM Saat Inferensi** | ~750 MB – 1.000 MB |
| **Pustaka Eksekusi** | `whisper.cpp` dengan ikatan JNI (*JNI bindings*) untuk Android NDK |
| **Arsitektur CPU** | ARM64 (ARMv8-A ke atas). ARMv7 32-bit **tidak didukung**. |
| **Format Masukan** | PCM 16kHz 16-bit Mono (berkas audio lain didekode terlebih dahulu) |
| **Jendela Pemrosesan** | 30 detik per *chunk* (standar Whisper) |
| **Bahasa** | Mendukung 99 bahasa. Dioptimalkan untuk Bahasa Indonesia dan Inggris melalui kosakata kustom. |
| **Sumber Unduh** | Hugging Face (tautan dikonfigurasi di aplikasi) |

### 11.2 Dampak Kompresi (Quantization) terhadap Akurasi

Berdasarkan riset empiris komunitas:

| Versi | Ukuran | RAM | Selisih WER vs FP16 | Keterangan |
| :--- | :--- | :--- | :--- | :--- |
| FP16 (asli) | ~1,62 GB | ~2,0 – 2,4 GB | Acuan (0%) | Terlalu berat untuk HP kelas menengah. |
| INT8 | ~810 MB | ~1,2 GB | ~0,0% – 0,2% | Nyaris identik dengan FP16. |
| **Q5_0 (dipilih)** | **~574 MB** | **~750 MB – 1 GB** | **~0,4% – 1,0%** | **Keseimbangan optimal ukuran vs akurasi.** |
| INT4 | ~405 MB | ~600 MB | ~1,0% – 1,5% | Penurunan mulai terasa pada audio bising. |

> **Kesimpulan:** Versi Q5_0 dipilih karena menawarkan keseimbangan terbaik: penurunan akurasi yang hampir tidak terasa (< 1% WER) dengan penghematan memori ~60% dibanding versi asli, sehingga aman untuk perangkat Android dengan RAM 8 GB ke atas.

### 11.3 Estimasi Kecepatan Transkripsi (Berbasis Berkas, Bukan *Streaming*)

Pemrosesan berbasis berkas (*batch inference*) lebih cepat dan stabil dibanding mode *streaming* karena Whisper dapat mengoptimalkan konteks penuh setiap *chunk* 30 detik.

| Durasi Audio | Estimasi Waktu (RAM 8 GB, CPU ARM) | Estimasi Waktu (RAM 12 GB+, CPU ARM cepat) |
| :--- | :--- | :--- |
| 5 menit | 20 – 45 detik | 10 – 25 detik |
| 10 menit | 40 – 90 detik | 20 – 50 detik |
| 30 menit | 2 – 5 menit | 1 – 3 menit |
| 1 jam | 5 – 10 menit | 3 – 6 menit |

> **Catatan:** Angka di atas adalah estimasi berdasarkan *benchmark* komunitas dan dapat bervariasi signifikan tergantung pada chipset spesifik, beban aplikasi lain yang berjalan, dan kondisi termal perangkat. Angka pasti akan divalidasi di perangkat target (TECNO CN5) selama implementasi.

### 11.4 Perangkat Minimum yang Disarankan untuk Mode Luring

| Spesifikasi | Minimum | Disarankan |
| :--- | :--- | :--- |
| **RAM** | 8 GB | 12 GB ke atas |
| **Penyimpanan Tersedia** | 1 GB (untuk model + berkas kerja) | 2 GB ke atas |
| **Prosesor** | ARM64, Snapdragon 600-series / setara | Snapdragon 700-series+ / Dimensity 8000+ |
| **Android** | 8.0 (API 26) | 12.0+ (API 31+) |

Untuk perangkat di bawah spesifikasi minimum, mode BYOK Cloud (F-12) tersedia sebagai alternatif.

---

## 12. Strategi Perangkuman

Perangkum notulensi tersedia dalam dua mode yang dapat dipilih pengguna di pengaturan:

### Mode A: Perangkum Ekstraktif (Bawaan, Tanpa Internet)

| Atribut | Detail |
| :--- | :--- |
| **Algoritma** | TextRank / TF-IDF dalam Kotlin murni |
| **Memori Tambahan** | < 50 MB (tanpa model AI tambahan) |
| **Latensi** | < 1 detik untuk transkrip 10.000 kata |
| **Kualitas** | Memilih kalimat-kalimat paling representatif dari transkrip asli (kumpulan kutipan kunci). Tidak memparafrasa. |
| **Deteksi Tugas** | Filter aturan berbasis kata kunci: "tolong", "harus", "paling lambat", "deadline", hari/tanggal, angka tenggat. Hasilnya berupa **kandidat** yang dapat dikonfirmasi pengguna. |
| **Keterbatasan** | Tidak menghasilkan parafrasa, tidak menangkap keputusan implisit, tidak menyusun prioritas. Cocok untuk "ringkasan cepat" sebelum membaca transkrip lengkap. |

### Mode B: BYOK Cloud (Opsional, Perlu Internet & Kunci API)

| Atribut | Detail |
| :--- | :--- |
| **Penyedia** | Groq API (`llama-3.3-70b`) atau OpenAI API (`gpt-4o-mini`) |
| **Latensi** | < 15 detik untuk transkrip 10.000 kata |
| **Kualitas** | Notulensi generatif penuh: ringkasan parafrasa, keputusan eksplisit & implisit, daftar tugas dengan PIC dan tenggat. |
| **Biaya** | Ditanggung pengguna (kunci API pribadi). Estimasi: < $0,01 per sesi rapat 30 menit (Groq). |
| **Privasi** | Teks transkrip dikirim ke API. **Berkas audio TIDAK dikirim** (transkripsi tetap lokal). Hasil disimpan hanya di perangkat. |

> **Catatan Produk:** Untuk MVP, Mode A (Ekstraktif) adalah bawaan dan selalu tersedia tanpa biaya maupun internet. Mode B (BYOK) adalah peningkatan opsional bagi pengguna yang menginginkan notulensi berkualitas tinggi.

> **Rencana Pasca-MVP (v1.1):** Evaluasi penambahan Mode C (SLM di perangkat, misal Qwen 2.5 3B Q4 ~2 GB) untuk notulensi generatif tanpa internet. Evaluasi dilakukan setelah MVP stabil, dengan membandingkan keluaran 2–3 transkrip rapat nyata berbahasa Indonesia antara Ekstraktif, SLM 3B, dan Cloud.

---

## 13. Skenario Tepi & Penanganan Kegagalan

| ID | Skenario | Perilaku yang Diharapkan |
| :--: | :--- | :--- |
| **EC-01** | **Berkas audio yang diimpor rusak atau tidak valid.** | Tampilkan pesan: "Berkas audio tidak dapat dibaca. Pastikan berkas tidak rusak dan formatnya didukung." Rekaman tidak ditambahkan ke daftar. |
| **EC-02** | **Berkas audio terlalu pendek (< 3 detik).** | Tampilkan pesan: "Rekaman terlalu pendek untuk ditranskrip (minimum 3 detik)." |
| **EC-03** | **Berkas audio sangat panjang (> 3 jam).** | Audio dibagi menjadi *chunk* per 30 menit. Bilah kemajuan menunjukkan *chunk* mana yang sedang diproses ("Memproses bagian 3 dari 7"). |
| **EC-04** | **Penyimpanan HP penuh saat mengimpor atau mendekode.** | Periksa ruang tersedia sebelum menyalin. Jika kurang dari (ukuran berkas × 2 + 100 MB): tampilkan pesan "Ruang penyimpanan tidak cukup. Butuh setidaknya X MB." |
| **EC-05** | **Model AI belum diunduh saat pengguna mencoba transkripsi.** | Tampilkan dialog: "Model transkripsi belum tersedia. Unduh model (574 MB) sekarang? [Unduh] [Gunakan Mode Cloud]" |
| **EC-06** | **Unduhan model terputus di tengah jalan.** | Unduhan mendukung *resume* (HTTP Range header). Pengguna bisa melanjutkan unduhan yang terputus tanpa mengulang dari awal. |
| **EC-07** | **Audio rekaman 100% hening.** | Setelah transkripsi, jika tidak ada segmen teks yang dihasilkan, tampilkan: "Tidak ditemukan percakapan dalam rekaman ini." |
| **EC-08** | **Koneksi internet terputus saat transkripsi/perangkuman mode BYOK.** | Proses dihentikan dengan pesan: "Koneksi internet terputus. Coba lagi saat koneksi stabil, atau gunakan mode luring." Tidak ada data yang hilang. |
| **EC-09** | **Aplikasi ditutup paksa saat transkripsi sedang berjalan.** | Segmen yang sudah selesai diproses tersimpan di basis data. Saat aplikasi dibuka kembali, rekaman menunjukkan status "Terganggu — Proses Ulang?" dengan opsi melanjutkan dari *chunk* terakhir. |
| **EC-10** | **Pengguna mengimpor berkas yang sama dua kali.** | Tampilkan peringatan: "Berkas dengan nama dan ukuran yang sama sudah ada. Impor tetap sebagai salinan baru? [Ya] [Batal]" |
| **EC-11** | **Format audio tidak didukung oleh MediaCodec (misal WMA lama).** | Coba dekode menggunakan FFmpeg (pustaka cadangan). Jika tetap gagal: "Format audio ini tidak didukung di perangkat Anda." |

---

## 14. Metrik Keberhasilan

| ID | Metrik | Target MVP | Cara Pengukuran |
| :--: | :--- | :--- | :--- |
| SM-01 | **Task Completion Rate** (Pengguna berhasil mendapat notulensi PDF dari rekaman tanpa bantuan) | ≥ 90% | Uji coba dengan 10 pengguna beta. |
| SM-02 | **Word Error Rate (WER)** transkripsi Bahasa Indonesia jernih | ≤ 15% (Whisper LV3T Q5 lokal) | Ukur dengan 5 sampel audio rapat berdurasi 10 menit. |
| SM-03 | **Notulensi Relevance Score** | ≥ 80% poin penting tercakup | 3 peninjau manusia menilai keluaran vs isi rapat. |
| SM-04 | **Crash-Free Rate** | ≥ 99,5% | Pencatatan *crash* lokal terenkripsi (tanpa telemetri pihak ketiga). |
| SM-05 | **Waktu Pemrosesan Ujung-ke-Ujung** (dari impor sampai PDF siap) | ≤ 7 menit untuk rekaman 30 menit | Ukur di perangkat target. |
| SM-06 | **Tingkat Keberhasilan Impor** (berkas berhasil diimpor dan didekode dari total percobaan) | ≥ 95% | Uji dengan 20 berkas dari 5 aplikasi perekam yang berbeda. |

---

## 15. Asumsi & Ketergantungan

### 15.1 Asumsi

| ID | Asumsi |
| :--: | :--- |
| A-01 | Pengguna merekam audio menggunakan aplikasi perekam suara terpisah (Voice Recorder bawaan HP, Easy Voice Recorder, dsb.), bukan di dalam Catet AI. |
| A-02 | Berkas audio yang diimpor berada di penyimpanan lokal perangkat (internal atau SD card) yang dapat diakses melalui pemilih berkas sistem. |
| A-03 | Pengguna bersedia mengunduh model AI (~574 MB) saat pertama kali menggunakan fitur transkripsi luring. |
| A-04 | HP target utama memiliki prosesor ARM64 (ARMv8-A ke atas) dan RAM ≥ 8 GB untuk mode luring. |
| A-05 | Jaringan internet (jika menggunakan mode BYOK) memiliki kecepatan minimal 1 Mbps *upload*. |
| A-06 | Kualitas audio rekaman bergantung sepenuhnya pada aplikasi perekam dan kondisi lingkungan. Catet AI tidak bertanggung jawab atas kualitas audio masukan. |

### 15.2 Ketergantungan Teknologi Pihak Ketiga

| Komponen | Ketergantungan | Lisensi | Risiko |
| :--- | :--- | :--- | :--- |
| Mesin Transkripsi Lokal | `whisper.cpp` (dengan ikatan JNI/NDK) | MIT | Rendah — proyek sumber terbuka aktif, komunitas besar. |
| Dekoder Audio (cadangan) | `FFmpeg` (pustaka bawaan, hanya kodek yang dibutuhkan) | LGPL 2.1 | Rendah — hanya digunakan untuk format yang tidak didukung MediaCodec. Pastikan kepatuhan LGPL (*dynamic linking*). |
| Basis Data Terenkripsi | SQLCipher (Community Edition) | BSD | Rendah — standar industri untuk SQLite terenkripsi. |
| Enkripsi Berkas | Google Tink | Apache 2.0 | Rendah — pustaka kriptografi resmi Google. |
| BYOK API (Opsional) | Groq API / OpenAI API | Proprietary | Sedang — harga dan ketersediaan bisa berubah. Mitigasi: mendukung > 1 penyedia. |
| Pembuatan PDF | `Android PdfDocument` API (bawaan SDK) | Bagian dari Android SDK | Sangat Rendah — API bawaan, tanpa pustaka tambahan. |

---

## 16. Risiko & Mitigasi

| ID | Risiko | Dampak | Probabilitas | Mitigasi |
| :--: | :--- | :--- | :--- | :--- |
| R-01 | **Model terlalu berat untuk HP RAM 6 GB** sehingga terjadi *crash* atau *kill* oleh Android LMK. | Tinggi | Sedang | Sediakan mode BYOK Cloud sebagai alternatif bawaan. Tampilkan peringatan jika RAM terdeteksi < 8 GB. |
| R-02 | **Format audio dari aplikasi perekam tertentu tidak didukung** oleh MediaCodec. | Sedang | Rendah | Sertakan FFmpeg sebagai dekoder cadangan untuk format langka. |
| R-03 | **Ukuran model (574 MB) terlalu besar** sehingga pengguna enggan mengunduh. | Sedang | Sedang | Tampilkan manfaat jelas ("transkripsi akurat tanpa internet selamanya"). Unduhan mendukung *resume*. Pertimbangkan model yang lebih kecil (Q4, ~405 MB) sebagai opsi di v1.1. |
| R-04 | **Perangkat menjadi panas (*thermal throttling*) saat transkripsi panjang (> 30 menit)**, memperlambat proses. | Sedang | Tinggi | Tampilkan estimasi waktu sebelum proses dimulai. Proses dalam *chunk* dengan jeda singkat antar *chunk* untuk memberi waktu pendinginan. |
| R-05 | **Groq/OpenAI mengubah kebijakan API** atau menaikkan harga. | Sedang | Rendah | Arsitektur BYOK mendukung beberapa penyedia. Jika satu bermasalah, pengguna memasukkan kunci dari penyedia lain. |
| R-06 | **Whisper salah mentranskripsi istilah lokal** (nama orang, perusahaan, singkatan). | Sedang | Tinggi | Fitur Kosakata Kustom (F-04) memandu *decoder*. Dokumentasikan batasan model di FAQ aplikasi. |
| R-07 | **Pustaka `whisper.cpp` JNI *crash* pada arsitektur ARM tertentu.** | Tinggi | Rendah | Tangkap *crash* JNI dengan *signal handler*. Sediakan *fallback* ke mode BYOK Cloud. Laporkan ke *upstream*. |

---

## 17. Glosarium

| Istilah | Definisi |
| :--- | :--- |
| **STT** | *Speech-to-Text*. Teknologi mengubah sinyal audio suara manusia menjadi teks tertulis. |
| **WER** | *Word Error Rate*. Metrik standar industri untuk mengukur akurasi transkripsi. WER 10% = dari 100 kata, 10 kata salah (semakin rendah semakin baik). |
| **GGUF** | Format berkas model AI terkuantisasi yang digunakan oleh `whisper.cpp` dan `llama.cpp`. Dirancang agar efisien di perangkat dengan memori terbatas. |
| **Quantization (Q5)** | Teknik kompresi model AI dengan mengurangi presisi bobot dari 16-bit (*floating point*) menjadi 5-bit. Mengurangi ukuran dan kebutuhan RAM secara drastis dengan penurunan akurasi yang sangat kecil. |
| **JNI** | *Java Native Interface*. Mekanisme yang memungkinkan kode Kotlin/Java di Android memanggil pustaka C/C++ (dalam hal ini `whisper.cpp`). |
| **NDK** | *Native Development Kit*. Perangkat pengembangan Android untuk membangun kode C/C++ yang berjalan di perangkat Android. |
| **SAF** | *Storage Access Framework*. API Android untuk mengakses berkas di penyimpanan perangkat melalui pemilih berkas sistem yang aman. |
| **BYOK** | *Bring Your Own Key*. Model di mana pengguna memasukkan kunci API miliknya sendiri untuk mengakses layanan *cloud*. |
| **MediaCodec** | API Android bawaan untuk mendekode (dan mengenkode) berkas media. Memanfaatkan akselerasi perangkat keras jika tersedia. |
| **SQLCipher** | Ekstensi sumber terbuka untuk SQLite yang menambahkan enkripsi transparan AES-256 pada basis data. |
| **Tink** | Pustaka kriptografi sumber terbuka dari Google. Digunakan untuk enkripsi berkas audio saat istirahat (*at-rest*). |
| **TextRank** | Algoritma perangkuman teks ekstraktif berbasis graf, terinspirasi dari PageRank Google. Memilih kalimat-kalimat paling penting dari sebuah dokumen tanpa menghasilkan teks baru. |
| **LMK** | *Low Memory Killer*. Mekanisme Android yang secara otomatis mematikan proses aplikasi saat memori perangkat hampir habis untuk menjaga stabilitas sistem. |
| **FTS4** | *Full-Text Search* versi 4. Ekstensi SQLite untuk pencarian teks cepat di dalam basis data. |
| **Chunk** | Potongan audio berdurasi tetap (30 detik) yang diproses satu per satu oleh model Whisper. |
| **SLM** | *Small Language Model*. Model bahasa besar versi ringkas (1B–3B parameter) yang bisa berjalan di perangkat *mobile*. |

---

> **Status Dokumen:** DRAFT v2.0.0
> **Perubahan Utama dari v1:** Alur inti berubah dari perekaman bawaan menjadi impor berkas. Model STT difokuskan pada Whisper Large v3 Turbo Q5. Ekspor PDF ditambahkan sebagai fitur inti.
> **Langkah Selanjutnya:** Review → Approval → Lanjut ke `02_TECH_SPEC_CATET_AI.md`
