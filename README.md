# Catet AI (Private & On-Device Meeting Assistant)

[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%20%28Android%208.0%29-blue.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35%20%28Android%2015%29-brightgreen.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20Architecture%20%2B%20MVI-orange.svg)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-Proprietary%20%2F%20Private-red.svg)](#)

> **Catet AI** adalah aplikasi asisten rapat pintar berbasis Android yang dirancang dengan filosofi **Local-First & On-Device Intelligence**. Seluruh proses perekaman, transkripsi suara (*Speech-to-Text*), dan perumusan notulensi rapat (*AI Summarizer*) berjalan langsung di dalam perangkat smartphone tanpa pernah mengirim rekaman audio rahasia ke server pihak ketiga.

---

## 🎯 Nilai Utama & Pembeda dari Kompetitor (Fireflies / Otter)

| Masalah pada Solusi Cloud (Fireflies/Otter) | Solusi di **Catet AI** |
| :--- | :--- |
| **Kerahasiaan Data & Kepatuhan Hukum:** Audio rapat dikirim ke server luar negeri, melanggar regulasi privasi perusahaan perbankan, instansi pemerintah, dan hukum. | **100% Zero-Cloud Storage:** Rekaman dan transkrip tersimpan secara terenkripsi (AES-256) di database lokal perangkat. Data tidak pernah meninggalkan HP Anda. |
| **Ketergantungan Internet:** Tidak bisa digunakan saat rapat di lokasi proyek pedalaman, pesawat, atau ruang *bunker* tanpa sinyal. | **Offline-First Ready:** Mesin transkripsi (*Whisper on-device*) dan perangkum (*Small Language Model*) berjalan 100% tanpa koneksi internet. |
| **Biaya Langganan Mahal:** Menguras \$10 - \$30/bulan per pengguna karena beban biaya server GPU cloud yang tinggi. | **Zero Recurring Server Cost:** Memanfaatkan komputasi NPU/GPU lokal smartphone pengguna, memungkinkan skema lisensi mandiri (*one-time purchase / freemium*). |
| **Konteks Bahasa Lokal (Indoglish & Istilah Kantor):** Sering salah mengartikan istilah kerja lokal dan singkatan kantor Indonesia. | **Context-Aware Prompt Biasing:** Menggunakan *initial prompt injection* dan pembersihan berbasis LLM untuk menangani istilah teknis, nama peserta, dan bahasa percakapan lokal. |

---

## 🏛️ Arsitektur Sistem (Clean Architecture)

Aplikasi dibangun mengikuti rekomendasi resmi Google untuk skalabilitas jangka panjang dan kemudahan pengujian (*testability*):

```
┌──────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│            (Jetpack Compose UI + MVI / ViewModel)            │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                       Domain Layer                           │
│        (Business Models + Use Cases - Pure Kotlin)           │
└──────────────┬───────────────────────────────┬───────────────┘
               │                               │
┌──────────────▼──────────────┐ ┌──────────────▼───────────────┐
│         Data Layer          │ │         Core / AI Engine      │
│   (Room Database + Repos)   │ │  (AudioRecord, VAD, Whisper) │
└─────────────────────────────┘ └──────────────────────────────┘
```

### Komponen Utama:
1. **Audio Capture Engine:** Android `AudioRecord` streaming buffer 16kHz 16-bit PCM Mono dengan `ForegroundService` tipe `microphone` (Android 14+ compliant).
2. **Voice Activity Detection (VAD):** Silero VAD (ONNX Runtime) untuk memangkas keheningan (*silence removal*), menghemat komputasi hingga 40%.
3. **Speech-to-Text (STT):** `whisper.cpp` / `sherpa-onnx` dengan model kuantisasi GGUF/GGML (`whisper-base` dan `whisper-large-v3-turbo`).
4. **Local Summarizer & Action Item Extractor:** Small Language Model (MediaPipe LLM Inference / llama.cpp - Qwen 2.5 / Gemma 2).
5. **Secure Local Storage:** Room DB dengan enkripsi SQLCipher + Android Keystore.

---

## 📂 Struktur Repositori

```
catet-ai/
├── docs/                        # Dokumentasi Engineering & SSOT
│   ├── 01_PRD_CATET_AI.md       # Product Requirements Document
│   ├── 02_TECH_SPEC_CATET_AI.md # Spesifikasi Teknis & Arsitektur Sistem
│   ├── 03_SPRINT_ROADMAP.md     # Roadmap Pengerjaan & Definition of Done
│   └── architecture/            # Diagram Alur & Skema Desain
├── app/                         # Modul Aplikasi Utama Android (Kotlin)
│   ├── src/main/java/com/catetai/
│   │   ├── core/                # Audio DSP, VAD, JNI Whisper Wrapper
│   │   ├── data/                # Database Room, Entity, Repository Impl
│   │   ├── domain/              # UseCases & Business Models
│   │   └── presentation/        # Jetpack Compose UI Screens & ViewModels
├── gradle/                      # Konfigurasi Build Gradle
├── .gitignore                   # Android & AI Model Git Ignore Rules
└── README.md                    # Dokumentasi Utama Proyek
```

---

## 🚀 Rencana Rilis Bertahap (Roadmap Milestones)

- [ ] **Fase 0: Blueprint & Standar Engineering** (PRD, Tech Spec, Setup Lingkungan)
- [ ] **Fase 1: Core Audio Engine & Local DB** (Perekam 16kHz background service + Room DB)
- [ ] **Fase 2: Audio Pre-processing & STT Pipeline** (Silero VAD + Whisper on-device)
- [ ] **Fase 3: Local Summarizer & Action Items** (Prompting LLM notulensi & to-do list)
- [ ] **Fase 4: UI/UX Jetpack Compose & Export Feature** (Waveform, tap-to-listen, ekspor PDF/MD)

---

## 🛠️ Standar Kontribusi & Git Workflow

Proyek ini menerapkan standar engineering perusahaan startup:
* **Branching Strategy:** 
  * `main`: Cabang produksi yang selalu siap dijalankan.
  * `feature/<nama-fitur>`: Cabang pengerjaan fitur baru.
* **Format Pesan Commit (Conventional Commits):**
  * `feat: <penambahan fitur baru>`
  * `fix: <perbaikan bug>`
  * `docs: <pembaruan atau penambahan dokumentasi>`
  * `refactor: <perapihan struktur kode tanpa merubah fungsi>`
  * `test: <penambahan unit test atau automated test>`
  * `chore: <konfigurasi build, gradle, atau tools>`

---

## 📜 Lisensi & Kerahasiaan
Seluruh arsitektur dan kode sumber dalam repositori ini dilindungi hak cipta privat.
