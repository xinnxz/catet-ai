# 01 — Product Requirements Document (PRD)
## Catet AI: Private & On-Device Meeting Assistant

| Metadata         | Isi                                      |
| :--------------- | :--------------------------------------- |
| **Versi Dokumen**| 1.0.0                                    |
| **Status**       | DRAFT — Menunggu Review & Approval       |
| **Penulis**      | Luthfi Fahmi Alviansyah                  |
| **Tanggal Dibuat**| 17 November 2026                        |
| **Revisi Terakhir**| 17 November 2026                       |
| **Target Rilis MVP**| Q1 2027                               |
| **Platform**     | Android (Kotlin + Jetpack Compose)       |

---

## Daftar Isi

1. [Ringkasan Eksekutif](#1-ringkasan-eksekutif)
2. [Latar Belakang & Pernyataan Masalah](#2-latar-belakang--pernyataan-masalah)
3. [Visi & Proposisi Nilai](#3-visi--proposisi-nilai)
4. [Target Pengguna (User Persona)](#4-target-pengguna-user-persona)
5. [Analisis Kompetitor](#5-analisis-kompetitor)
6. [Batasan Ruang Lingkup (Scope Boundary)](#6-batasan-ruang-lingkup-scope-boundary)
7. [User Stories & Acceptance Criteria](#7-user-stories--acceptance-criteria)
8. [Kebutuhan Fungsional (Functional Requirements)](#8-kebutuhan-fungsional-functional-requirements)
9. [Kebutuhan Non-Fungsional (Non-Functional Requirements)](#9-kebutuhan-non-fungsional-non-functional-requirements)
10. [Skenario Tepi & Penanganan Kegagalan (Edge Cases)](#10-skenario-tepi--penanganan-kegagalan-edge-cases)
11. [Model Pemrosesan (Tiered Engine Strategy)](#11-model-pemrosesan-tiered-engine-strategy)
12. [Metrik Keberhasilan (Success Metrics)](#12-metrik-keberhasilan-success-metrics)
13. [Asumsi & Ketergantungan (Assumptions & Dependencies)](#13-asumsi--ketergantungan-assumptions--dependencies)
14. [Risiko & Mitigasi](#14-risiko--mitigasi)
15. [Glosarium](#15-glosarium)

---

## 1. Ringkasan Eksekutif

**Catet AI** adalah aplikasi asisten rapat berbasis Android yang mengubah rekaman suara percakapan menjadi notulensi rapat terstruktur (ringkasan, keputusan, dan daftar tugas) secara otomatis.

Berbeda dari kompetitor seperti Fireflies.ai dan Otter.ai yang mengirim seluruh rekaman audio ke server cloud di luar negeri, Catet AI menerapkan arsitektur **Local-First**: seluruh data (rekaman audio, transkrip teks, dan hasil notulensi) tersimpan secara eksklusif di penyimpanan internal perangkat pengguna yang dilindungi oleh enkripsi perangkat keras.

Mesin kecerdasan buatan (transkripsi suara dan perangkum teks) dirancang dengan sistem **3 Tingkatan (Tiered Engine)** agar dapat berjalan di berbagai kondisi:
- **Mode Offline:** 100% di dalam HP tanpa internet.
- **Mode Hybrid (BYOK):** Pemrosesan kilat via API cloud menggunakan kunci API milik pengguna sendiri, namun penyimpanan hasil tetap 100% lokal.

---

## 2. Latar Belakang & Pernyataan Masalah

### 2.1 Konteks Pasar

Pasar global aplikasi Meeting Intelligence diproyeksikan tumbuh dari \$2.1 miliar (2024) menjadi \$7.5 miliar (2030). Namun, mayoritas solusi yang ada (Fireflies, Otter, Fathom, Tactiq) berjalan sepenuhnya di cloud dan menyimpan data percakapan di server luar negeri.

### 2.2 Masalah Nyata yang Ditemukan

Berdasarkan pengalaman langsung (sesi onboarding engineering di startup DASH, 16 November 2026), ditemukan 4 masalah konkret:

| No | Masalah | Bukti Nyata |
| :--: | :--- | :--- |
| **M-01** | **Istilah lokal salah diartikan oleh STT internasional.** | Model Whisper `small` mentranskrip kata "MagangHub" menjadi "semang-manggung hati", dan "WFO/WFH" menjadi "Web O & Web A". |
| **M-02** | **Audio yang direkam HP dari jarak jauh sulit ditangkap.** | Pembicara yang duduk 3 meter dari HP mengalami degradasi konsonan karena gema ruangan dan desis AC. Model `small` gagal menangkap beberapa kalimat. |
| **M-03** | **Tidak ada solusi transkripsi rapat yang privasi-first di Indonesia.** | Perusahaan sektor keuangan, hukum, dan pemerintahan di Indonesia secara eksplisit melarang pengiriman rekaman rapat ke server asing. |
| **M-04** | **Biaya langganan Fireflies terlalu mahal untuk individu dan tim kecil.** | Fireflies Pro = \$10/bulan/user, Business = \$29/bulan/user. Tim 10 orang mengeluarkan \$290/bulan (~Rp 4.6 juta) hanya untuk notulensi. |

### 2.3 Pernyataan Masalah (Formal)

> Profesional dan tim kerja di Indonesia membutuhkan alat transkripsi dan notulensi rapat yang **akurat terhadap konteks bahasa lokal**, **tidak mengirim data ke server asing**, dan **tidak membebankan biaya langganan server bulanan**.

---

## 3. Visi & Proposisi Nilai

### 3.1 Pernyataan Visi Produk

> *"Menjadikan setiap orang memiliki asisten notulis pribadi di dalam sakunya — tanpa perlu menyerahkan privasinya kepada siapa pun."*

### 3.2 Proposisi Nilai (Value Proposition)

| Dimensi | Proposisi |
| :--- | :--- |
| **Privasi Mutlak** | Rekaman suara, teks transkrip, dan notulensi tidak pernah meninggalkan perangkat pengguna. Tidak ada server backend, tidak ada telemetri audio. |
| **Konteks Bahasa Indonesia** | Mendukung bahasa Indonesia, campuran Inggris-Indonesia (*Indoglish*), singkatan kantor (WFO, WFH, PIC, EOD, SPK), dan nama orang/perusahaan lokal melalui fitur kosakata kustom (*prompt biasing*). |
| **Tanpa Biaya Server Berulang** | Komputasi AI menggunakan prosesor perangkat pengguna sendiri. Tidak ada server GPU yang harus dibayar setiap bulan. Model bisnis: beli putus (*one-time*) atau freemium. |
| **Siap Offline** | Dapat digunakan di lokasi tanpa sinyal internet (rapat di kapal, tambang, pelosok, ruang sidang tertutup). |

---

## 4. Target Pengguna (User Persona)

### Persona A: Rania — Notulis Tim Startup (Primer)

| Atribut | Detail |
| :--- | :--- |
| **Usia & Peran** | 24 tahun, Junior Product Manager di startup teknologi Jakarta |
| **Kebiasaan** | Menghadiri 3–5 rapat per hari (standup, sprint planning, 1-on-1) |
| **Frustrasi Saat Ini** | Menulis notulensi manual sering ketinggalan poin penting; Fireflies sering salah menangkap istilah internal tim ("Plane", "squad", "blocker") |
| **Kebutuhan Utama** | Rekam percakapan saat rapat tatap muka → dapatkan notulensi otomatis dengan ringkasan, keputusan, dan daftar PIC |
| **Perangkat** | Samsung Galaxy A55 (RAM 8GB), Android 14 |

### Persona B: Pak Hendra — Kepala Divisi Hukum Perusahaan (Sekunder)

| Atribut | Detail |
| :--- | :--- |
| **Usia & Peran** | 48 tahun, Head of Legal & Compliance di perusahaan pembiayaan |
| **Kebiasaan** | Memimpin rapat negosiasi kontrak dan sidang internal yang bersifat sangat rahasia |
| **Frustrasi Saat Ini** | Dilarang keras oleh kebijakan perusahaan untuk menggunakan aplikasi apa pun yang mengirim data ke cloud asing. Notulensi masih dikerjakan manual oleh sekretaris. |
| **Kebutuhan Utama** | Alat yang 100% offline dan terenkripsi di perangkatnya sendiri |
| **Perangkat** | Google Pixel 8 Pro (RAM 12GB), Android 15 |

### Persona C: Dina — Mahasiswa Tingkat Akhir (Tersier)

| Atribut | Detail |
| :--- | :--- |
| **Usia & Peran** | 21 tahun, mahasiswa semester 7 yang sedang menyusun skripsi |
| **Kebiasaan** | Merekam penjelasan dosen pembimbing untuk didengarkan ulang nanti |
| **Frustrasi Saat Ini** | Memutar ulang rekaman 1 jam hanya untuk mencari satu penjelasan yang dilupakan |
| **Kebutuhan Utama** | Transkrip ber-timestamp agar bisa langsung melompat ke bagian penjelasan tertentu tanpa memutar ulang keseluruhan |
| **Perangkat** | Xiaomi Redmi Note 12 (RAM 6GB), Android 13 |

---

## 5. Analisis Kompetitor

| Dimensi Perbandingan | Fireflies.ai | Otter.ai | Fathom | **Catet AI** |
| :--- | :--- | :--- | :--- | :--- |
| **Penyimpanan Data** | Cloud (Server AWS US) | Cloud (Server US) | Cloud (Server US) | **100% Lokal di HP** |
| **Mode Offline** | Tidak bisa | Tidak bisa | Tidak bisa | **Bisa (On-Device AI)** |
| **Bahasa Indonesia** | Parsial, sering salah | Sangat terbatas | Tidak didukung | **Fokus utama + prompt biasing** |
| **Biaya (per bulan)** | \$10 – \$29/user | \$8.33 – \$20/user | \$15 – \$29/user | **Gratis / Beli putus** |
| **Integrasi Bot Meeting Cloud** | Zoom, Meet, Teams (otomatis) | Zoom, Meet | Zoom, Meet | **Tidak ada (rekam via mikrofon)** |
| **Kualitas pada Audio Noisy** | Baik (server GPU besar) | Cukup | Baik | **Setara (large-v3-turbo on-device / BYOK)** |
| **Speaker Diarization** | Ya | Ya | Ya | **Ya (Fase 2+)** |

**Celah Pasar (Niche) Catet AI:**

Catet AI **tidak bersaing langsung** di arena bot meeting cloud (itu memerlukan infrastruktur server yang berat dan mahal). Sebaliknya, Catet AI menguasai segmen yang **tidak bisa dijangkau** oleh kompetitor mana pun:
1. Rapat tatap muka di ruangan fisik (kantor, ruang sidang, aula).
2. Lingkungan kerahasiaan tinggi (bank, kantor hukum, pemerintahan).
3. Lokasi tanpa internet (proyek lapangan, desa, transportasi).
4. Pengguna yang menolak biaya langganan berulang.

---

## 6. Batasan Ruang Lingkup (Scope Boundary)

Bagian ini secara eksplisit mendefinisikan apa yang **wajib ada** dan apa yang **dilarang dikerjakan** pada rilis MVP, untuk mencegah scope creep.

### 6.1 IN-SCOPE (Wajib Ada di MVP v1.0)

| ID | Fitur | Deskripsi Singkat |
| :--: | :--- | :--- |
| **F-01** | Perekaman Audio Latar Belakang | Merekam audio 16kHz PCM Mono via `ForegroundService`. Tetap aktif saat layar mati. |
| **F-02** | Manajemen Rekaman | Daftar rekaman dengan metadata (judul, durasi, tanggal, ukuran file). Bisa rename, hapus. |
| **F-03** | Voice Activity Detection (VAD) | Memotong segmen hening dari audio sebelum diproses STT. |
| **F-04** | Transkripsi Suara (STT) | Mengubah audio menjadi teks ber-timestamp per segmen kalimat. Mendukung mode on-device dan BYOK cloud. |
| **F-05** | Kosakata Kustom (Prompt Biasing) | Kolom input untuk memasukkan istilah, nama peserta, dan singkatan sebelum merekam. |
| **F-06** | Perangkum Notulensi (AI Summarizer) | Menghasilkan: (a) Ringkasan Rapat, (b) Keputusan Rapat, (c) Daftar Tugas / Action Items beserta PIC-nya. |
| **F-07** | Pemutar Audio Sinkron Teks | Tap sebuah kalimat di transkrip → audio langsung berputar di detik kalimat tersebut diucapkan. |
| **F-08** | Ekspor Hasil | Ekspor notulensi ke format Markdown (.md) dan teks biasa (.txt). Fitur "Bagikan" ke WhatsApp / aplikasi lain. |
| **F-09** | Penyimpanan Lokal Terenkripsi | Semua data (audio, transkrip, notulensi) tersimpan di database lokal Room dengan enkripsi SQLCipher (AES-256). |
| **F-10** | Pencarian Transkrip | Pencarian teks bebas (*full-text search*) di seluruh transkrip yang tersimpan. |

### 6.2 OUT-OF-SCOPE (Dilarang Dikerjakan Sekarang — Ditunda ke Fase Berikutnya)

| ID | Fitur yang Ditunda | Alasan Penundaan |
| :--: | :--- | :--- |
| **X-01** | Bot otomatis masuk ke Zoom / Google Meet / Teams | Membutuhkan infrastruktur server headless browser yang sangat kompleks dan mahal. Bukan urusan MVP. |
| **X-02** | Sinkronisasi data antar perangkat / cloud backup | Melanggar prinsip local-only di tahap awal. Jika dibutuhkan nanti, harus melalui enkripsi end-to-end. |
| **X-03** | Versi iOS (iPhone) | Fokus platform pertama adalah Android. iOS dipertimbangkan setelah product-market fit tercapai. |
| **X-04** | Integrasi kalender otomatis (Google Calendar) | Fitur nice-to-have, bukan critical path untuk MVP. |
| **X-05** | Real-time live transcription (teks muncul saat sedang merekam) | Membutuhkan optimasi streaming inference yang sangat berat di CPU mobile. Fase MVP: transkrip setelah selesai rekam. |
| **X-06** | Multi-bahasa selain Indonesia dan Inggris | Model Whisper mendukung 99 bahasa, namun optimasi prompt biasing difokuskan pada Bahasa Indonesia dan Inggris dulu. |
| **X-07** | Perekaman audio internal dari aplikasi meeting di HP yang sama | Android 10+ membatasi audio playback capture. Memerlukan riset panjang dan workaround khusus per OEM. |

---

## 7. User Stories & Acceptance Criteria

Setiap user story ditulis dalam format standar Agile dan memiliki **kriteria penerimaan (acceptance criteria)** yang bersifat biner: hanya ada status **LULUS** atau **GAGAL**, tanpa ruang abu-abu.

---

### US-01: Merekam Rapat Tatap Muka

> **Sebagai** Rania (notulis tim),  
> **Saya ingin** merekam percakapan rapat tatap muka dengan menekan satu tombol,  
> **Sehingga** saya tidak perlu lagi menulis notulensi secara manual sambil mendengarkan.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-01.1 | Perekaman dimulai dalam waktu kurang dari 2 detik setelah tombol "Rekam" ditekan. | Ukur waktu dengan stopwatch. |
| AC-01.2 | Perekaman tetap berjalan tanpa terputus selama minimal 120 menit dengan layar HP mati. | Rekam selama 2 jam, verifikasi file audio utuh dan durasinya sesuai. |
| AC-01.3 | Notifikasi persisten (*foreground notification*) muncul selama perekaman berlangsung, menampilkan durasi berjalan dan tombol "Berhenti". | Verifikasi visual di notification bar. |
| AC-01.4 | Perekaman tidak terganggu jika ada telepon masuk dan panggilan ditolak/diabaikan. | Simulasikan panggilan masuk saat sedang merekam, verifikasi audio tidak terputus. |
| AC-01.5 | File audio tersimpan dalam format WAV 16kHz 16-bit PCM Mono di penyimpanan internal aplikasi (bukan SD card atau folder publik). | Periksa path file dan properti audio menggunakan `MediaMetadataRetriever`. |

---

### US-02: Melihat dan Mengelola Daftar Rekaman

> **Sebagai** pengguna,  
> **Saya ingin** melihat semua rekaman rapat saya dalam daftar yang terurut berdasarkan tanggal terbaru,  
> **Sehingga** saya bisa menemukan rekaman yang saya cari dengan cepat.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-02.1 | Daftar rekaman menampilkan: judul, tanggal & waktu, durasi, dan status pemrosesan (Belum Ditranskrip / Sedang Diproses / Selesai). | Verifikasi visual: semua kolom terisi pada setiap item. |
| AC-02.2 | Pengguna bisa mengubah judul rekaman (*rename*). | Tap judul → ubah → simpan → verifikasi judul berubah di daftar dan di database. |
| AC-02.3 | Pengguna bisa menghapus rekaman (dengan dialog konfirmasi). Penghapusan menghapus file audio DAN data transkrip terkait secara permanen. | Hapus satu rekaman, verifikasi file fisik dan entri database terhapus. |

---

### US-03: Mentranskrip Rekaman Menjadi Teks

> **Sebagai** Rania,  
> **Saya ingin** rekaman rapat saya diubah menjadi teks transkrip secara otomatis dengan akurasi tinggi,  
> **Sehingga** saya bisa membaca isi rapat tanpa harus memutar ulang audionya.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-03.1 | Pengguna memilih sebuah rekaman dan menekan tombol "Transkrip". Proses dimulai dengan indikator kemajuan (*progress bar*) yang menunjukkan persentase. | Verifikasi visual: progress bar bergerak dari 0% ke 100%. |
| AC-03.2 | Setiap segmen kalimat dalam transkrip memiliki timestamp awal dalam format `[MM:SS]`. | Verifikasi: tap timestamp → audio berputar di detik tersebut (lihat US-05). |
| AC-03.3 | Pada audio rekaman rapat berbahasa Indonesia yang jernih (SNR ≥ 20dB, jarak mikrofon ≤ 1 meter), akurasi transkripsi (Word Error Rate) ≤ 15% menggunakan model `large-v3-turbo`. | Bandingkan transkrip otomatis dengan transkrip manual sebagai *ground truth*. Hitung WER. |
| AC-03.4 | Fitur **Kosakata Kustom** berfungsi: jika pengguna memasukkan istilah "MagangHub, Dash, WFO, WFH, EPD" sebelum transkripsi, model tidak lagi menebak kata-kata tersebut secara salah. | Uji dengan audio DASH onboarding. Verifikasi kata-kata tersebut muncul benar di transkrip. |
| AC-03.5 | Proses transkripsi dapat dibatalkan oleh pengguna di tengah jalan tanpa menyebabkan crash atau file rusak. | Tekan "Batal" di progress 50%, verifikasi aplikasi kembali ke daftar tanpa error. |

---

### US-04: Mendapatkan Notulensi Rapat Otomatis

> **Sebagai** Rania,  
> **Saya ingin** mendapatkan ringkasan rapat, keputusan, dan daftar tugas beserta PIC-nya secara otomatis dari transkrip,  
> **Sehingga** saya tinggal menyebarkan notulensi ke tim tanpa harus menulis ulang.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-04.1 | Setelah transkrip selesai, tombol "Buat Notulensi" muncul. Menekan tombol ini menghasilkan dokumen notulensi terstruktur dalam waktu kurang dari 60 detik untuk transkrip 30 menit (mode on-device). | Ukur waktu dan verifikasi output terstruktur. |
| AC-04.2 | Output notulensi terdiri atas 3 bagian wajib: (a) **Ringkasan Rapat** (maks 5 poin), (b) **Keputusan Rapat** (daftar bernomor), (c) **Daftar Tugas / Action Items** (berisi: tugas, PIC jika teridentifikasi, tenggat jika disebutkan). | Verifikasi ketiga bagian ada dan berisi konten yang relevan dengan isi transkrip. |
| AC-04.3 | Notulensi tidak mengandung informasi yang tidak ada di transkrip (*no hallucination*). Jika suatu informasi tidak disebutkan di rekaman, notulensi tidak boleh mengarangnya. | Review manual: bandingkan setiap poin notulensi dengan isi transkrip. |

---

### US-05: Memutar Audio dari Teks Transkrip

> **Sebagai** Pak Hendra,  
> **Saya ingin** bisa mengetuk sebuah kalimat di transkrip dan langsung mendengar audio aslinya di detik tersebut,  
> **Sehingga** saya bisa memverifikasi sendiri apakah transkrip sudah benar untuk kata-kata kritis.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-05.1 | Mengetuk sebuah segmen kalimat di layar transkrip langsung memutar audio mulai dari timestamp segmen tersebut (toleransi ±1 detik). | Verifikasi: audio yang terdengar sesuai dengan teks yang ditap. |
| AC-05.2 | Terdapat kontrol pemutar mini (play/pause, seek bar, kecepatan 0.5x/1x/1.5x/2x). | Verifikasi visual dan fungsional semua kontrol. |

---

### US-06: Mengekspor dan Membagikan Notulensi

> **Sebagai** Rania,  
> **Saya ingin** mengirim hasil notulensi ke grup WhatsApp tim saya dengan satu kali tap,  
> **Sehingga** seluruh tim langsung mendapat notulensi tanpa harus membuka aplikasi Catet AI.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-06.1 | Tombol "Bagikan" menampilkan Android share sheet standar yang memungkinkan pengiriman ke WhatsApp, Telegram, Email, dan aplikasi lainnya. | Verifikasi: share sheet muncul dan data terkirim ke WA/Telegram. |
| AC-06.2 | Format yang dikirim adalah teks biasa yang terformat rapi (judul tebal, poin bernomor, pemisah bagian). | Verifikasi: buka pesan yang diterima di WA, teks terbaca rapi. |
| AC-06.3 | Tersedia opsi "Simpan sebagai File" yang menghasilkan file `.md` atau `.txt` di folder Downloads perangkat. | Verifikasi: file muncul di folder Downloads dan bisa dibuka oleh aplikasi file manager. |

---

### US-07: Mencari di Seluruh Transkrip

> **Sebagai** Dina (mahasiswi),  
> **Saya ingin** mencari kata kunci tertentu di seluruh transkrip yang pernah saya buat,  
> **Sehingga** saya bisa menemukan penjelasan dosen tentang topik tertentu tanpa membuka satu per satu.

**Acceptance Criteria:**
| ID | Kriteria | Cara Verifikasi |
| :--: | :--- | :--- |
| AC-07.1 | Kolom pencarian di halaman utama bisa menerima kata kunci dan menampilkan hasil dari semua rekaman yang mengandung kata tersebut, dalam waktu kurang dari 1 detik untuk 100 rekaman. | Ukur waktu respons pencarian. |
| AC-07.2 | Hasil pencarian menampilkan: judul rekaman, potongan kalimat yang mengandung kata kunci (*snippet*), dan timestamp. Tap hasil → langsung ke segmen transkrip yang tepat. | Verifikasi navigasi dan highlight kata. |

---

## 8. Kebutuhan Fungsional (Functional Requirements)

Tabel ini merangkum setiap fungsi teknis yang harus dibangun. Setiap fungsi merujuk ke satu atau lebih User Story di atas.

| ID | Modul | Fungsi | Ref. US |
| :--: | :--- | :--- | :---: |
| FR-01 | Audio Engine | Merekam audio stream PCM 16kHz 16-bit Mono via `AudioRecord` API di dalam `ForegroundService` tipe `FOREGROUND_SERVICE_TYPE_MICROPHONE`. | US-01 |
| FR-02 | Audio Engine | Menyimpan audio buffer secara periodik ke file `.wav` di penyimpanan internal (`app-specific storage`). | US-01 |
| FR-03 | Audio Engine | Menangani interupsi audio (panggilan masuk, alarm, media lain) dengan `AudioFocusRequest` tanpa menghentikan perekaman. | US-01 |
| FR-04 | Manajemen Data | CRUD rekaman (Create, Read, Update judul, Delete beserta file audio dan data terkait). | US-02 |
| FR-05 | VAD | Menjalankan Silero VAD (ONNX Runtime Mobile) pada audio sebelum STT untuk memotong segmen hening (threshold default: 300ms). | US-03 |
| FR-06 | STT Engine | Menjalankan model Whisper (via `whisper.cpp` / `sherpa-onnx`) pada segmen audio yang lolos VAD dan menghasilkan teks ber-timestamp per segmen. | US-03 |
| FR-07 | STT Engine | Mendukung parameter `initial_prompt` untuk memasukkan kosakata kustom pengguna ke decoder Whisper. | US-03 |
| FR-08 | STT Engine | Mendukung mode BYOK: pengguna memasukkan API key Groq / OpenAI sendiri, audio dikirim ke API cloud untuk transkripsi, namun hasil teks disimpan hanya di lokal. API key disimpan terenkripsi di Android Keystore. | US-03 |
| FR-09 | Summarizer | Mengirim teks transkrip ke modul perangkum (on-device SLM atau BYOK cloud LLM) dengan prompt template terstruktur untuk menghasilkan 3 bagian notulensi. | US-04 |
| FR-10 | Audio Player | Pemutar audio dengan kemampuan seek ke timestamp tertentu (dari tap teks transkrip) dan kontrol kecepatan playback. | US-05 |
| FR-11 | Ekspor | Menghasilkan file `.md` dan `.txt` dari notulensi dan menyimpannya di folder Downloads. Mendukung Android `ShareSheet` intent. | US-06 |
| FR-12 | Pencarian | Full-text search pada kolom teks transkrip menggunakan Room FTS4 (*Full-Text Search*). | US-07 |
| FR-13 | Database | Skema database Room dengan tabel: `recordings`, `transcript_segments`, `summaries`. Enkripsi via SQLCipher. | Semua |

---

## 9. Kebutuhan Non-Fungsional (Non-Functional Requirements)

Setiap NFR **harus terukur** agar bisa diuji. Tidak ada kata ambigu seperti "harus cepat" atau "harus aman".

| ID | Kategori | Kebutuhan | Metrik Terukur | Cara Pengujian |
| :--: | :--- | :--- | :--- | :--- |
| NFR-01 | **Performa** | Perekaman audio tidak menyebabkan ANR (*Application Not Responding*) atau frame drop di UI. | 0 ANR selama sesi rekam 2 jam. UI tetap 60fps saat membuka daftar rekaman. | Android Profiler + StrictMode. |
| NFR-02 | **Performa** | Transkripsi on-device (model `base`) untuk audio 10 menit selesai dalam waktu kurang dari 5 menit di HP RAM 6GB. | Rasio waktu proses vs durasi audio ≤ 0.5x. | Benchmark di Xiaomi Redmi Note 12. |
| NFR-03 | **Performa** | Transkripsi BYOK (Groq API, model `large-v3-turbo`) untuk audio 10 menit selesai dalam waktu kurang dari 30 detik (tergantung koneksi internet). | Latency end-to-end ≤ 30 detik. | Benchmark dengan koneksi WiFi standar. |
| NFR-04 | **Memori** | Pemakaian RAM aplikasi tidak melebihi 500MB saat transkripsi on-device dengan model `base`. | RSS ≤ 500MB. | Android Profiler Memory tab. |
| NFR-05 | **Memori** | Pemakaian RAM aplikasi tidak melebihi 2.5GB saat transkripsi on-device dengan model `large-v3-turbo`. | RSS ≤ 2500MB. | Android Profiler Memory tab. |
| NFR-06 | **Baterai** | Perekaman audio latar belakang selama 1 jam mengonsumsi tidak lebih dari 5% baterai (pada HP baterai 5000mAh). | Drain rate ≤ 5%/jam. | Battery Historian. |
| NFR-07 | **Keamanan** | Semua data sensitif (rekaman, transkrip, notulensi, API key) tersimpan di penyimpanan internal aplikasi (*app-specific storage*) yang tidak bisa diakses oleh aplikasi lain. | Path file: `/data/data/com.catetai/...` | Verifikasi dengan `adb shell ls`. |
| NFR-08 | **Keamanan** | Database Room dienkripsi dengan SQLCipher (AES-256-CBC). Kunci enkripsi disimpan di Android Keystore (hardware-backed jika tersedia). | Database file tidak bisa dibaca tanpa kunci. | Coba buka file `.db` dengan SQLite browser → harus gagal. |
| NFR-09 | **Kompatibilitas** | Aplikasi berjalan di Android 8.0 (API 26) sampai Android 15 (API 35) tanpa crash. | 0 crash di rentang SDK 26–35. | Uji di emulator API 26, 30, 33, 35. |
| NFR-10 | **Ukuran Aplikasi** | Ukuran APK dasar (tanpa model AI) tidak melebihi 15MB. Model AI diunduh terpisah saat pertama kali dibutuhkan. | APK ≤ 15MB, model download terpisah. | Ukur ukuran APK hasil build release. |
| NFR-11 | **Reliabilitas** | Aplikasi tidak kehilangan data rekaman jika proses tiba-tiba dihentikan oleh sistem (*process death*). Audio yang sudah direkam hingga titik terakhir tetap tersimpan utuh. | File audio valid sampai detik terakhir sebelum force stop. | Force stop via `adb shell am force-stop`, verifikasi file audio. |

---

## 10. Skenario Tepi & Penanganan Kegagalan (Edge Cases)

| ID | Skenario | Perilaku yang Diharapkan |
| :--: | :--- | :--- |
| **EC-01** | **Baterai habis saat sedang merekam.** | Ketika level baterai turun ke ≤ 5%, aplikasi menampilkan notifikasi peringatan. Ketika level baterai turun ke ≤ 2%, perekaman dihentikan secara aman (*graceful stop*) dan file audio yang sudah terekam disimpan utuh sampai detik terakhir. |
| **EC-02** | **Penyimpanan HP penuh saat sedang merekam.** | Aplikasi memonitor ruang disk tersisa setiap 30 detik selama perekaman. Jika tersisa kurang dari 100MB, tampilkan peringatan. Jika tersisa kurang dari 50MB, hentikan perekaman secara aman dan simpan file yang sudah ada. |
| **EC-03** | **Panggilan telepon masuk saat sedang merekam.** | Perekaman **tidak berhenti**. Aplikasi meminta `AudioFocusRequest` tipe `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. Jika pengguna mengangkat telepon, volume mikrofon mungkin berkurang (tergantung OEM), namun perekaman tetap berjalan. |
| **EC-04** | **Pengguna menutup aplikasi (swipe dari recent apps) saat sedang merekam.** | `ForegroundService` tetap berjalan karena terikat dengan notifikasi persisten. Perekaman tidak terpengaruh. |
| **EC-05** | **HP di-restart saat sedang merekam.** | Perekaman berhenti (tidak bisa dicegah). File audio yang sudah terekam disimpan utuh sampai detik terakhir sebelum restart. Saat aplikasi dibuka kembali setelah boot, rekaman tersebut muncul di daftar dengan status "Terpotong — Perangkat Mati Ulang". |
| **EC-06** | **Koneksi internet terputus saat transkripsi mode BYOK (cloud).** | Proses dihentikan dengan pesan error yang jelas: "Koneksi internet terputus. Transkrip belum selesai. Coba lagi saat koneksi stabil." Tidak ada data yang hilang. |
| **EC-07** | **Audio rekaman 100% hening (rapat belum dimulai / salah rekam).** | VAD mendeteksi bahwa tidak ada segmen suara yang valid. Pengguna diberi notifikasi: "Tidak ditemukan percakapan dalam rekaman ini." |
| **EC-08** | **Model AI on-device belum diunduh saat pengguna mencoba transkripsi offline.** | Tampilkan dialog: "Model transkripsi offline belum tersedia. Unduh model (ukuran: ~75MB) sekarang? Atau gunakan mode cloud (membutuhkan internet dan API key)." |
| **EC-09** | **Rekaman sangat panjang (lebih dari 3 jam).** | Audio dibagi menjadi *chunk* per 30 menit untuk diproses secara bertahap. Progress bar menunjukkan chunk mana yang sedang diproses (misalnya "Memproses bagian 3 dari 7"). |

---

## 11. Model Pemrosesan (Tiered Engine Strategy)

Pengguna dapat memilih profil pemrosesan di halaman pengaturan (*Settings*). Pemilihan ini menentukan mesin AI mana yang digunakan untuk transkripsi dan perangkuman.

| Profil | Mesin STT | Mesin Summarizer | Koneksi Internet | HP Minimum | Deskripsi UX |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hemat (Offline Ringan)** | Whisper `base` / Sherpa-onnx `zipformer` on-device | On-device SLM (Gemma 2 2B Q4) | Tidak perlu | RAM 4GB | Untuk HP spesifikasi rendah. Akurasi cukup, kecepatan sedang. |
| **Optimal (Offline Berat)** | Whisper `large-v3-turbo` on-device (GGUF Q5) | On-device SLM (Qwen 2.5 3B Q4) | Tidak perlu | RAM 8GB+ | Untuk HP spesifikasi tinggi. Akurasi tinggi, baterai bekerja lebih keras. |
| **Turbo (BYOK Cloud)** | API Groq / API OpenAI (`whisper-large-v3-turbo`) | API Groq / API OpenAI (`llama-3.3-70b` / `gpt-4o-mini`) | **Diperlukan** | RAM 4GB | Tercepat dan terakurat. Pengguna memasukkan API key sendiri. Data tetap disimpan 100% lokal. |

---

## 12. Metrik Keberhasilan (Success Metrics)

Metrik ini digunakan untuk mengevaluasi apakah MVP berhasil memecahkan masalah yang didefinisikan di bagian 2.

| ID | Metrik | Target MVP | Cara Pengukuran |
| :--: | :--- | :--- | :--- |
| SM-01 | **Task Completion Rate** (Pengguna berhasil mendapat notulensi dari audio tanpa bantuan) | ≥ 90% | Uji coba dengan 10 pengguna beta. Hitung berapa yang berhasil tanpa bertanya. |
| SM-02 | **Word Error Rate (WER)** transkripsi Bahasa Indonesia jernih | ≤ 15% (mode Turbo), ≤ 25% (mode Hemat) | Benchmark dengan 5 sampel audio rapat berdurasi 10 menit. |
| SM-03 | **Notulensi Relevance Score** (Notulensi mencakup poin penting yang ada di transkrip) | ≥ 80% poin penting tercakup | 3 reviewer manusia menilai output notulensi vs isi rapat sebenarnya. |
| SM-04 | **Crash-Free Rate** | ≥ 99.5% | Firebase Crashlytics (atau logging lokal). |
| SM-05 | **Waktu Pemrosesan End-to-End** (dari selesai rekam sampai notulensi tampil) | ≤ 10 menit untuk rekaman 30 menit (mode Hemat) | Benchmark di perangkat target. |

---

## 13. Asumsi & Ketergantungan (Assumptions & Dependencies)

### 13.1 Asumsi

| ID | Asumsi |
| :--: | :--- |
| A-01 | Pengguna merekam rapat tatap muka menggunakan mikrofon HP. Aplikasi **tidak** merekam audio internal dari aplikasi lain di HP yang sama. |
| A-02 | Jaringan internet (jika menggunakan mode BYOK) memiliki kecepatan minimal 1 Mbps upload. |
| A-03 | Pengguna bersedia mengunduh model AI secara terpisah (75MB – 800MB, tergantung profil) saat pertama kali menggunakan fitur offline. |
| A-04 | HP target utama adalah Android dengan prosesor ARM64 (ARMv8-A ke atas). Prosesor ARMv7 (32-bit) tidak didukung. |

### 13.2 Ketergantungan Teknologi Pihak Ketiga

| Komponen | Ketergantungan | Lisensi | Risiko |
| :--- | :--- | :--- | :--- |
| STT On-Device | `whisper.cpp` / `sherpa-onnx` | MIT | Rendah — proyek open-source aktif dengan komunitas besar. |
| VAD | Silero VAD (ONNX) | MIT | Rendah — model stabil, ukuran kecil (~2MB). |
| LLM On-Device | `llama.cpp` / MediaPipe LLM | MIT / Apache 2.0 | Sedang — performa di perangkat mobile masih berkembang. |
| Database Enkripsi | SQLCipher (Community Edition) | BSD | Rendah — standar industri untuk SQLite terenkripsi. |
| BYOK API (Opsional) | Groq API / OpenAI API | Proprietary (pay-as-you-go) | Sedang — kebijakan harga dan ketersediaan API bisa berubah. Mitigasi: mendukung lebih dari satu provider. |

---

## 14. Risiko & Mitigasi

| ID | Risiko | Dampak | Probabilitas | Mitigasi |
| :--: | :--- | :--- | :--- | :--- |
| R-01 | Model Whisper on-device terlalu lambat di HP murah (RAM 4GB) sehingga pengguna frustasi menunggu. | Tinggi | Sedang | Sediakan mode BYOK cloud sebagai default untuk HP spesifikasi rendah. Tampilkan estimasi waktu sebelum proses dimulai. |
| R-02 | OEM tertentu (Xiaomi, Oppo, Vivo) secara agresif mematikan background service meski sudah menggunakan Foreground Service. | Tinggi | Tinggi | Implementasi panduan `dontkillmyapp.com` per OEM. Tampilkan panduan satu kali kepada pengguna untuk menonaktifkan battery optimization untuk aplikasi ini. |
| R-03 | Ukuran model AI terlalu besar, pengguna enggan mengunduh. | Sedang | Sedang | Gunakan model terkuantisasi paling kecil (`base`, Q4 / Q5) sebagai default. Unduhan bersifat opsional per profil. |
| R-04 | Kualitas audio rekaman HP sangat buruk (gema tinggi, banyak noise) sehingga transkripsi tidak berguna. | Tinggi | Sedang | Implementasikan pre-processing audio (Noise Suppression + AGC) sebelum STT. Tampilkan indikator kualitas mikrofon saat mulai rekam. |
| R-05 | Groq/OpenAI mengubah kebijakan API atau menaikkan harga secara drastis. | Sedang | Rendah | Arsitektur BYOK mendukung beberapa provider. Jika satu bermasalah, pengguna bisa memasukkan key dari provider lain. |

---

## 15. Glosarium

| Istilah | Definisi |
| :--- | :--- |
| **STT** | *Speech-to-Text*. Teknologi mengubah sinyal audio suara manusia menjadi teks tertulis. |
| **VAD** | *Voice Activity Detection*. Algoritma untuk mendeteksi keberadaan suara manusia dan membedakannya dari keheningan atau noise lingkungan. |
| **WER** | *Word Error Rate*. Metrik standar industri untuk mengukur akurasi transkripsi. WER 10% = dari 100 kata, 10 kata salah. |
| **SNR** | *Signal-to-Noise Ratio*. Perbandingan kekuatan sinyal suara yang diinginkan terhadap noise latar. Semakin tinggi (dalam dB), semakin jernih suara. |
| **BYOK** | *Bring Your Own Key*. Model di mana pengguna memasukkan API key miliknya sendiri untuk mengakses layanan cloud, sehingga pengembang aplikasi tidak menanggung biaya server. |
| **On-Device** | Pemrosesan yang berjalan seluruhnya di dalam perangkat pengguna tanpa mengirim data ke server eksternal. |
| **GGUF** | Format file model AI terkuantisasi yang digunakan oleh `llama.cpp` dan `whisper.cpp`. Dirancang agar efisien di perangkat dengan memori terbatas. |
| **Prompt Biasing** | Teknik memberikan konteks kosakata awal kepada model Whisper agar memprioritaskan kata-kata tertentu saat proses dekoding. |
| **ForegroundService** | Komponen Android yang menjalankan operasi berkepanjangan di latar belakang dengan notifikasi persisten, sehingga sistem operasi tidak mematikannya. |
| **SQLCipher** | Ekstensi open-source untuk SQLite yang menambahkan enkripsi transparan AES-256 pada database. |
| **SLM** | *Small Language Model*. Model bahasa besar (*LLM*) versi ringkas (1B–3B parameter) yang bisa berjalan di perangkat mobile. |
| **Clean Architecture** | Pola arsitektur perangkat lunak yang memisahkan kode menjadi lapisan independen (Presentation, Domain, Data) agar mudah diuji dan dimodifikasi. |

---

> **Status Dokumen:** DRAFT v1.0.0  
> **Langkah Selanjutnya:** Review oleh stakeholder → Approval → Lanjut ke `02_TECH_SPEC_CATET_AI.md`

