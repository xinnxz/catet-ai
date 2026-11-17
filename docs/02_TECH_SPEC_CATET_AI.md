# 02 — Technical Specification (Tech Spec / RFC)
## Catet AI: System Architecture & Engineering Design

| Metadata              | Isi                                               |
| :-------------------- | :------------------------------------------------ |
| **Versi Dokumen**     | 1.0.0                                             |
| **Status**            | DRAFT — Menunggu Review & Approval                |
| **Penulis**           | Luthfi Fahmi Alviansyah                           |
| **Tanggal Dibuat**    | 17 November 2026                                  |
| **Revisi Terakhir**   | 17 November 2026                                  |
| **Dokumen Prasyarat** | `01_PRD_CATET_AI.md` (harus dibaca terlebih dulu) |

---

## Daftar Isi

1. [Tujuan Dokumen](#1-tujuan-dokumen)
2. [Keputusan Teknologi & Justifikasi](#2-keputusan-teknologi--justifikasi)
3. [Arsitektur Sistem Tingkat Tinggi](#3-arsitektur-sistem-tingkat-tinggi)
4. [Arsitektur Modul Detail](#4-arsitektur-modul-detail)
5. [Alur Data End-to-End (Data Flow)](#5-alur-data-end-to-end-data-flow)
6. [Skema Database (ERD)](#6-skema-database-erd)
7. [Kontrak Antar Modul (Interface Contract)](#7-kontrak-antar-modul-interface-contract)
8. [Strategi Model AI On-Device](#8-strategi-model-ai-on-device)
9. [Strategi Keamanan & Enkripsi](#9-strategi-keamanan--enkripsi)
10. [Strategi Penanganan Background Process](#10-strategi-penanganan-background-process)
11. [Strategi Pengelolaan Memori & Performa](#11-strategi-pengelolaan-memori--performa)
12. [Dependency Map & Versi Library](#12-dependency-map--versi-library)
13. [Strategi Pengujian (Testing Strategy)](#13-strategi-pengujian-testing-strategy)
14. [Keputusan Arsitektur yang Ditolak (Rejected Alternatives)](#14-keputusan-arsitektur-yang-ditolak-rejected-alternatives)

---

## 1. Tujuan Dokumen

Dokumen ini adalah **Single Source of Truth (SSOT)** teknis untuk seluruh implementasi Catet AI.

Setiap keputusan arsitektur di dokumen ini **harus memiliki justifikasi** (alasan mengapa dipilih) dan **minimal satu alternatif yang ditolak** (alasan mengapa tidak dipilih). Ini mencegah keputusan yang diambil "karena ikut-ikutan" tanpa pemahaman.

Dokumen ini menjawab pertanyaan: **"Jika engineer baru bergabung ke proyek ini besok, apakah mereka bisa memahami seluruh arsitektur hanya dari dokumen ini tanpa harus bertanya?"** Jawabannya harus: Ya.

---

## 2. Keputusan Teknologi & Justifikasi

### 2.1 Platform & Bahasa

| Komponen | Keputusan | Justifikasi |
| :--- | :--- | :--- |
| **Platform** | Android Native (bukan cross-platform) | Catet AI sangat bergantung pada akses tingkat rendah ke hardware audio (`AudioRecord`), background service lifecycle, NDK/JNI untuk inferensi C++, dan Android Keystore untuk enkripsi. Cross-platform framework menambah lapisan abstraksi yang mengurangi kontrol dan menambah risiko bug di area kritis ini. |
| **Bahasa Utama** | Kotlin 2.0+ | Bahasa resmi Android yang didukung penuh oleh Google. Mendukung Coroutines (untuk operasi asinkron tanpa blocking), Flow (untuk streaming data reaktif), dan interoperabilitas penuh dengan Java/JNI. |
| **UI Framework** | Jetpack Compose (Material 3) | Framework UI deklaratif modern dari Google. Menggantikan XML layout tradisional. Lebih ringkas, lebih mudah diuji, dan performa rendering setara atau lebih baik dari View system. |
| **Native AI Engine** | C/C++ via Android NDK + CMake | Library inferensi AI (`whisper.cpp`, `llama.cpp`, `onnxruntime`) ditulis dalam C/C++. NDK memungkinkan pemanggilan langsung tanpa overhead serialisasi data yang terjadi pada jembatan FFI di framework cross-platform. |

### 2.2 Arsitektur Aplikasi

| Komponen | Keputusan | Justifikasi |
| :--- | :--- | :--- |
| **Pola Arsitektur** | Clean Architecture (3 Layer) + MVI (Model-View-Intent) | Memisahkan logika bisnis dari detail implementasi (database, UI, API). Memudahkan penulisan unit test tanpa emulator Android. MVI memberikan alur data satu arah (*unidirectional data flow*) yang mudah didebug. |
| **Dependency Injection** | Hilt (Dagger 2 wrapper) | Standar DI resmi dari Google untuk Android. Terintegrasi dengan ViewModel, WorkManager, dan Navigation. Menghilangkan boilerplate pembuatan objek secara manual. |
| **Navigasi** | Jetpack Navigation Compose | Navigasi berbasis graph yang type-safe. Mengelola back stack secara otomatis. |
| **Asynchronous** | Kotlin Coroutines + Flow | Coroutines untuk operasi asinkron (perekaman, inferensi AI). Flow untuk streaming data reaktif dari database ke UI (misalnya: daftar rekaman yang diperbarui secara real-time). |

### 2.3 Penyimpanan Data

| Komponen | Keputusan | Justifikasi |
| :--- | :--- | :--- |
| **Database Relasional** | Room (SQLite wrapper) + SQLCipher | Room adalah abstraksi resmi Google di atas SQLite dengan compile-time query verification. SQLCipher menambahkan enkripsi transparan AES-256 tanpa mengubah kode query. |
| **Penyimpanan File Audio** | App-specific internal storage (`context.filesDir`) + Streaming Encryption (AES-256-GCM via `EncryptedFile`) | File audio tidak disimpan sebagai plaintext mentah. Audio dienkripsi saat istirahat (*at-rest*) menggunakan `EncryptedFile` (Jetpack Security) dengan kunci yang diamankan di Android Keystore. Bahkan jika perangkat di-root atau storage fisik diekstraksi, file audio tidak dapat diputar tanpa otorisasi Keystore. |
| **Penyimpanan Kredensial** | Android Keystore + EncryptedSharedPreferences | API key BYOK dan kunci enkripsi database disimpan di hardware security module (HSM) perangkat jika tersedia, atau di software-backed keystore. Tidak bisa diekstrak bahkan oleh root. |

---

## 3. Arsitektur Sistem Tingkat Tinggi

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │  HomeScreen   │  │ RecordScreen │  │ DetailScreen │  │SettingsScreen│    │
│  │  (Daftar      │  │ (Perekam +   │  │ (Transkrip + │  │ (Profil AI + │    │
│  │   Rekaman)    │  │  Waveform)   │  │  Audio Sync) │  │  BYOK Key)   │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                 │                 │                 │              │
│  ┌──────▼─────────────────▼─────────────────▼─────────────────▼──────┐      │
│  │                      ViewModels (MVI)                             │      │
│  │   HomeViewModel  │ RecordViewModel │ DetailViewModel │ SettingsVM │      │
│  └──────────────────────────┬────────────────────────────────────────┘      │
└─────────────────────────────┼──────────────────────────────────────────────┘
                              │  (Kotlin Interface — tidak ada dependensi
                              │   ke framework Android di bawah garis ini)
┌─────────────────────────────▼──────────────────────────────────────────────┐
│                            DOMAIN LAYER                                    │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐    │
│  │  StartRecordingUC  │  │ TranscribeAudioUC  │  │ GenerateSummaryUC  │    │
│  │  StopRecordingUC   │  │ CancelTranscribeUC │  │ ExportNotesUC      │    │
│  │  DeleteRecordingUC │  │ SearchTranscriptUC │  │                    │    │
│  └────────┬───────────┘  └────────┬───────────┘  └────────┬───────────┘    │
│           │                       │                       │                │
│  ┌────────▼───────────────────────▼───────────────────────▼───────────┐    │
│  │                     Repository Interfaces                          │    │
│  │   RecordingRepository  │ TranscriptRepository │ SummaryRepository  │    │
│  └────────────────────────────────┬───────────────────────────────────┘    │
└───────────────────────────────────┼────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────────┐
│                             DATA LAYER                                     │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │                    Repository Implementations                    │      │
│  └──────────┬──────────────────┬──────────────────┬─────────────────┘      │
│             │                  │                  │                        │
│  ┌──────────▼──────┐  ┌───────▼───────┐  ┌───────▼────────┐              │
│  │  Room Database  │  │  File System  │  │  BYOK API      │              │
│  │  (SQLCipher)    │  │  (WAV Files)  │  │  Client        │              │
│  │                 │  │               │  │  (Groq/OpenAI) │              │
│  │  - RecordingDao │  │  - AudioFileManager              │              │
│  │  - TranscriptDao│  │               │  │               │              │
│  │  - SummaryDao   │  │               │  │               │              │
│  └─────────────────┘  └───────────────┘  └───────────────┘              │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│                         CORE / AI ENGINE LAYER                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  AudioRecorder   │  │  SileroVAD       │  │  WhisperEngine       │     │
│  │  (AudioRecord    │  │  (ONNX Runtime   │  │  (whisper.cpp via    │     │
│  │   API + Buffer   │  │   Mobile)        │  │   JNI/NDK)           │     │
│  │   Streaming)     │  │                  │  │                      │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  AudioPlayer     │  │  NoiseSuppress   │  │  LocalLLMEngine      │     │
│  │  (ExoPlayer /    │  │  (RNNoise /      │  │  (llama.cpp via      │     │
│  │   MediaPlayer)   │  │   DeepFilterNet) │  │   JNI/NDK)           │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘     │
└────────────────────────────────────────────────────────────────────────────┘
```

### Penjelasan Alur Antar Layer

1. **Presentation → Domain:** UI mengirimkan *Intent* (aksi pengguna) ke ViewModel. ViewModel memanggil *UseCase* di Domain Layer. Presentation Layer **tidak pernah** mengakses Database atau AI Engine secara langsung.

2. **Domain → Data:** UseCase memanggil Repository Interface (kontrak abstrak). Domain Layer **tidak tahu** apakah data berasal dari Room Database, file WAV, atau API cloud. Ini memungkinkan penggantian sumber data tanpa mengubah logika bisnis.

3. **Data → Core/AI Engine:** Repository Implementation memanggil engine C++ (Whisper, VAD, LLM) melalui wrapper Kotlin yang membungkus panggilan JNI. Data Layer bertanggung jawab mengoordinasikan alur: potong audio (VAD) → transkrip (Whisper) → simpan ke database (Room).

---

## 4. Arsitektur Modul Detail

### 4.1 Modul Audio Engine (`core/audio/`)

```
core/audio/
├── AudioRecorderManager.kt      # Orkestrasi perekaman: start, stop, pause
├── AudioConfig.kt               # Konstanta audio: SAMPLE_RATE=16000, CHANNEL=MONO, ENCODING=PCM_16BIT
├── PcmBufferWriter.kt           # Menulis buffer PCM ke file WAV secara streaming (flush tiap 5 detik)
├── WavHeaderUtil.kt             # Membuat dan memperbarui WAV header (RIFF format)
├── AudioFocusHandler.kt         # Menangani AudioFocusRequest: panggilan masuk, media lain
├── AudioPlayerManager.kt        # Pemutar audio dengan seek-to-timestamp dan kontrol kecepatan
└── RecordingForegroundService.kt # ForegroundService tipe MICROPHONE dengan notifikasi persisten
```

**Mekanisme Perekaman (Detail Teknis):**

```
┌──────────────────────────────────────────────────────────────────────┐
│                  RecordingForegroundService                          │
│                                                                      │
│  1. Buat AudioRecord(SAMPLE_RATE=16000, MONO, PCM_16BIT)            │
│  2. Buat file WAV di filesDir dengan header placeholder              │
│  3. Loop: audioRecord.read(buffer, 0, bufferSize)                   │
│     ├── Tulis buffer PCM ke FileOutputStream                        │
│     ├── Hitung amplitudo real-time (untuk waveform UI)              │
│     ├── Kirim amplitudo ke UI via SharedFlow                        │
│     └── Setiap 5 detik: flush stream ke disk (anti data loss)       │
│  4. Saat STOP: perbarui WAV header dengan ukuran data final         │
│  5. Simpan metadata ke Room Database                                 │
└──────────────────────────────────────────────────────────────────────┘
```

**Mengapa flush setiap 5 detik?**
Jika aplikasi di-force-stop atau HP mati mendadak (EC-05 di PRD), audio yang sudah ditulis ke disk tetap valid. Tanpa flush periodik, seluruh buffer yang masih di memori akan hilang. WAV header diperbarui terakhir saat stop — jika header tidak sempat diperbarui, file masih bisa dipulihkan dengan menghitung ulang ukuran data dari ukuran file.

### 4.2 Modul VAD (`core/vad/`)

```
core/vad/
├── SileroVadEngine.kt      # Wrapper ONNX Runtime untuk Silero VAD model
├── VadConfig.kt             # Konfigurasi: threshold=0.5, minSilenceDuration=300ms, minSpeechDuration=250ms
└── SpeechSegment.kt         # Data class: startTimeMs, endTimeMs, confidence
```

**Prinsip Kerja VAD:**

Audio WAV dibaca dalam frame 30ms (480 sampel pada 16kHz). Setiap frame dilewatkan ke model Silero VAD (ukuran ~2MB, format ONNX). Model menghasilkan probabilitas 0.0 – 1.0 yang menunjukkan apakah frame tersebut mengandung suara manusia.

```
Input: [frame_0, frame_1, frame_2, ... frame_N]
       [0.12,    0.95,    0.98,    ... 0.03   ]  ← probabilitas speech

Threshold = 0.5

Output: List<SpeechSegment>
        SpeechSegment(start=30ms, end=2850ms)   ← "ada orang bicara"
        SpeechSegment(start=5200ms, end=8100ms) ← "ada orang bicara"
        // segmen hening di antara 2850-5200ms dibuang
```

**Mengapa VAD penting sebelum STT?**
Rapat 60 menit rata-rata memiliki 15-20 menit keheningan (jeda antar pembicara, waktu berpikir, membaca slide). Tanpa VAD, Whisper memproses 60 menit penuh. Dengan VAD, Whisper hanya memproses 40-45 menit audio yang berisi suara, menghemat **25-33% waktu komputasi dan baterai**.

### 4.3 Modul STT Engine (`core/stt/`)

```
core/stt/
├── WhisperJni.kt               # JNI bridge ke whisper.cpp (native C++)
├── WhisperConfig.kt            # Model path, language="id", initialPrompt, beam_size, dll
├── SttEngineManager.kt         # Abstraksi: pilih on-device atau BYOK cloud
├── OnDeviceSttEngine.kt        # Implementasi: load model GGUF, jalankan inferensi lokal
├── ByokCloudSttEngine.kt       # Implementasi: kirim audio ke Groq/OpenAI API, terima teks
├── TranscriptSegment.kt        # Data class: startTimeMs, endTimeMs, text, confidence
└── jni/
    └── CMakeLists.txt           # Build config untuk compile whisper.cpp via NDK
```

**Alur Transkripsi On-Device:**

```
1. SttEngineManager menerima List<SpeechSegment> dari VAD
2. Untuk setiap SpeechSegment:
   a. Baca audio PCM dari file WAV pada rentang [start..end]
   b. Kirim ke WhisperJni.transcribe(pcmFloat32Array, config)
   c. whisper.cpp memproses dan mengembalikan List<TranscriptSegment>
      dengan word-level timestamp
3. Gabungkan semua segmen → simpan ke Room Database
```

**Parameter Whisper yang Diekspos ke Pengguna:**

| Parameter | Default | Fungsi |
| :--- | :--- | :--- |
| `language` | `"id"` | Memaksa Whisper mendekode dalam Bahasa Indonesia (bukan autodetect, yang sering salah pada Indoglish). |
| `initial_prompt` | `""` (kosong) | Diisi oleh Kosakata Kustom pengguna (F-05 di PRD). Contoh: `"Rapat internal Dash, MagangHub, EPD, Kak Indra, sprint Plane."` |
| `beam_size` | `5` | Jumlah kandidat dekoding paralel. Semakin besar = lebih akurat tapi lebih lambat. |
| `temperature` | `0.0` | Deterministic decoding (selalu pilih token dengan probabilitas tertinggi). |
| `no_timestamps` | `false` | Harus `false` agar timestamp per segmen dihasilkan. |

### 4.4 Modul Summarizer (`core/summarizer/`)

```
core/summarizer/
├── LlmJni.kt                   # JNI bridge ke llama.cpp (native C++)
├── LlmConfig.kt                # Model path, max_tokens, temperature, system_prompt
├── SummarizerManager.kt         # Abstraksi: pilih on-device SLM atau BYOK cloud LLM
├── OnDeviceSummarizer.kt        # Implementasi: inferensi lokal dengan Qwen/Gemma
├── ByokCloudSummarizer.kt       # Implementasi: kirim transkrip ke API, terima ringkasan
├── SummaryPromptTemplate.kt     # Template prompt notulensi terstruktur
└── MeetingSummary.kt            # Data class: executiveSummary, decisions, actionItems
```

**Template Prompt Notulensi (Versi 1.0):**

```
Kamu adalah asisten notulis rapat profesional.

Diberikan transkrip rapat berikut, buatlah notulensi terstruktur dengan format:

## Ringkasan Rapat
- (Maksimal 5 poin utama yang dibahas dalam rapat)

## Keputusan Rapat
1. (Daftar keputusan yang diambil)

## Daftar Tugas (Action Items)
| No | Tugas | PIC | Tenggat |
| -- | ----- | --- | ------- |
| 1  | ...   | ... | ...     |

ATURAN KETAT:
- HANYA tulis informasi yang EKSPLISIT disebutkan dalam transkrip.
- Jika PIC atau tenggat tidak disebutkan, tulis "Belum ditentukan".
- JANGAN menambahkan informasi yang tidak ada dalam transkrip.
- Gunakan Bahasa Indonesia.

TRANSKRIP RAPAT:
---
{transcript_text}
---
```

### 4.5 Modul Database (`data/local/`)

```
data/local/
├── CatetAiDatabase.kt           # @Database: Room database class dengan SQLCipher
├── entity/
│   ├── RecordingEntity.kt        # Tabel recordings
│   ├── TranscriptSegmentEntity.kt # Tabel transcript_segments
│   └── SummaryEntity.kt          # Tabel summaries
├── dao/
│   ├── RecordingDao.kt           # Query CRUD untuk recordings
│   ├── TranscriptSegmentDao.kt   # Query CRUD + FTS untuk transcript
│   └── SummaryDao.kt             # Query CRUD untuk summaries
└── converter/
    └── DateTimeConverters.kt     # Type converter Long <-> Instant
```

---

## 5. Alur Data End-to-End (Data Flow)

### 5.1 Alur Perekaman (Recording Flow)

```
Pengguna tap [REKAM]
     │
     ▼
RecordViewModel → StartRecordingUseCase
     │
     ▼
RecordingRepository.startRecording()
     │
     ├── 1. Buat RecordingEntity (status=RECORDING) → simpan ke Room
     ├── 2. Buat file WAV kosong di filesDir
     └── 3. Start RecordingForegroundService
              │
              ├── Buat AudioRecord(16kHz, MONO, PCM_16BIT)
              ├── Tampilkan notifikasi persisten
              └── Loop:
                   ├── read(buffer) dari mikrofon
                   ├── write(buffer) ke file WAV
                   ├── emit(amplitude) ke SharedFlow → UI waveform
                   └── flush ke disk setiap 5 detik
```

### 5.2 Alur Transkripsi (Transcription Flow)

```
Pengguna tap [TRANSKRIP] pada sebuah rekaman
     │
     ▼
DetailViewModel → TranscribeAudioUseCase
     │
     ▼
TranscriptRepository.transcribe(recordingId)
     │
     ├── 1. Update RecordingEntity.status = PROCESSING
     │
     ├── 2. Baca file WAV dari disk
     │
     ├── 3. Jalankan VAD → List<SpeechSegment>
     │      (buang keheningan, emit progress 0-30%)
     │
     ├── 4. Untuk setiap SpeechSegment:
     │      ├── Ekstrak audio PCM pada rentang [start..end]
     │      ├── Jalankan STT Engine (on-device atau BYOK)
     │      ├── Terima List<TranscriptSegment>
     │      ├── Simpan ke Room (batch insert)
     │      └── Emit progress 30-90%
     │
     ├── 5. Update RecordingEntity.status = TRANSCRIBED
     │      Emit progress 100%
     │
     └── 6. UI menampilkan transkrip ber-timestamp
```

### 5.3 Alur Pembuatan Notulensi (Summarization Flow)

```
Pengguna tap [BUAT NOTULENSI]
     │
     ▼
DetailViewModel → GenerateSummaryUseCase
     │
     ▼
SummaryRepository.generateSummary(recordingId)
     │
     ├── 1. Ambil semua TranscriptSegment dari Room untuk recordingId
     ├── 2. Gabungkan menjadi teks transkrip utuh (dengan timestamp)
     ├── 3. Masukkan ke SummaryPromptTemplate
     ├── 4. Kirim ke SummarizerManager (on-device SLM atau BYOK cloud LLM)
     ├── 5. Parse output LLM menjadi MeetingSummary
     ├── 6. Simpan SummaryEntity ke Room
     └── 7. UI menampilkan notulensi terstruktur
```

---

## 6. Skema Database (ERD)

### 6.1 Entity-Relationship Diagram

```
┌──────────────────────────┐
│       recordings         │
├──────────────────────────┤
│ PK  id: String (UUID)    │
│     title: String        │
│     audioFilePath: String│
│     durationMs: Long     │
│     fileSizeBytes: Long  │
│     sampleRate: Int      │  (selalu 16000)
│     status: Enum         │  (RECORDING, STOPPED, PROCESSING, TRANSCRIBED, SUMMARIZED, ERROR)
│     customVocabulary: Str│  (kosakata kustom, nullable)
│     processingEngine: Str│  (ON_DEVICE_BASE, ON_DEVICE_TURBO, BYOK_GROQ, BYOK_OPENAI)
│     createdAt: Long      │  (epoch millis)
│     updatedAt: Long      │
├──────────────────────────┤
│ 1 ──────────── * │
└────────┬─────────────────┘
         │
         │ recordingId (FK)
         │
┌────────▼─────────────────┐       ┌──────────────────────────┐
│  transcript_segments     │       │       summaries          │
├──────────────────────────┤       ├──────────────────────────┤
│ PK  id: Long (autoGen)   │       │ PK  id: String (UUID)    │
│ FK  recordingId: String   │       │ FK  recordingId: String   │
│     segmentIndex: Int     │       │     executiveSummary: Str │
│     startTimeMs: Long     │       │     decisions: String     │  (JSON array)
│     endTimeMs: Long       │       │     actionItems: String   │  (JSON array)
│     text: String          │       │     rawLlmOutput: String  │
│     confidence: Float     │       │     modelUsed: String     │
│     speakerLabel: String? │       │     createdAt: Long       │
│     createdAt: Long       │       └──────────────────────────┘
├──────────────────────────┤                │
│ FTS4 index on: text      │                │ recordingId (FK)
└──────────────────────────┘       ┌────────┘
                                   │
                           recordings (1) ──── (0..1) summaries
```

### 6.2 Detail Kolom Penting

**`recordings.status` (Enum - State Machine):**

```
RECORDING ──(stop)──→ STOPPED ──(transkrip)──→ PROCESSING ──(selesai)──→ TRANSCRIBED ──(notulensi)──→ SUMMARIZED
                                                   │
                                                   └──(gagal)──→ ERROR
```

Setiap transisi status hanya boleh terjadi dalam urutan di atas. Tidak boleh ada transisi melompat (misalnya dari RECORDING langsung ke TRANSCRIBED). Ini mencegah data dalam keadaan inkonsisten.

**`transcript_segments.speakerLabel` (Nullable):**

Pada MVP (v1.0), kolom ini selalu `null` karena speaker diarization ditargetkan pada **Post-MVP (v1.1+)**. Kolom ini disiapkan sejak awal di skema Room agar tidak memerlukan migrasi database yang rumit ketika fitur diarization diimplementasikan.

**`summaries.decisions` dan `summaries.actionItems` (JSON String):**

Disimpan sebagai JSON string karena jumlah item bervariasi per rapat. Room Type Converter mengonversi antara `List<Decision>` / `List<ActionItem>` dan JSON string secara transparan.

```kotlin
// Contoh struktur JSON actionItems:
[
  {
    "task": "Buat PR untuk fitur notifikasi push",
    "pic": "Kak Zulfikar",
    "deadline": "Jumat, 22 November 2026"
  },
  {
    "task": "Review dokumen PRD marketplace",
    "pic": "Belum ditentukan",
    "deadline": "Belum ditentukan"
  }
]
```

---

## 7. Kontrak Antar Modul (Interface Contract)

Setiap modul berkomunikasi melalui **interface Kotlin** (kontrak abstrak), bukan implementasi konkret. Ini memungkinkan penggantian implementasi (misalnya mengganti engine STT) tanpa mengubah kode di modul lain.

### 7.1 Repository Interfaces (di Domain Layer)

```kotlin
// domain/repository/RecordingRepository.kt
interface RecordingRepository {
    fun getAllRecordings(): Flow<List<Recording>>
    suspend fun getRecordingById(id: String): Recording?
    suspend fun createRecording(title: String, customVocabulary: String?): Recording
    suspend fun updateTitle(id: String, newTitle: String)
    suspend fun deleteRecording(id: String)  // hapus entity + file audio + transkrip + notulensi
}

// domain/repository/TranscriptRepository.kt
interface TranscriptRepository {
    suspend fun transcribe(recordingId: String, engine: SttEngine): Flow<TranscriptionProgress>
    suspend fun cancelTranscription(recordingId: String)
    fun getSegments(recordingId: String): Flow<List<TranscriptSegment>>
    fun searchSegments(query: String): Flow<List<SearchResult>>
}

// domain/repository/SummaryRepository.kt
interface SummaryRepository {
    suspend fun generateSummary(recordingId: String, engine: LlmEngine): Flow<SummarizationProgress>
    suspend fun getSummary(recordingId: String): MeetingSummary?
    suspend fun exportToMarkdown(recordingId: String): File
    suspend fun exportToPlainText(recordingId: String): File
}
```

### 7.2 AI Engine Interfaces (di Core Layer)

```kotlin
// core/stt/SttEngine.kt
interface SttEngine {
    suspend fun transcribe(
        audioData: FloatArray,       // PCM float32 normalized [-1.0, 1.0]
        sampleRate: Int,             // 16000
        language: String,            // "id"
        initialPrompt: String?,      // kosakata kustom
        onProgress: (Float) -> Unit  // 0.0 - 1.0
    ): List<TranscriptSegment>

    suspend fun isModelAvailable(): Boolean
    suspend fun downloadModel(onProgress: (Float) -> Unit)
    fun getModelSizeBytes(): Long
    fun release()  // bebaskan memori native
}

// core/summarizer/SummarizerEngine.kt
interface SummarizerEngine {
    suspend fun summarize(
        transcriptText: String,
        onProgress: (Float) -> Unit
    ): MeetingSummary

    suspend fun isModelAvailable(): Boolean
    suspend fun downloadModel(onProgress: (Float) -> Unit)
    fun release()
}

// core/vad/VadEngine.kt
interface VadEngine {
    fun process(
        audioData: FloatArray,
        sampleRate: Int
    ): List<SpeechSegment>

    fun release()
}
```

---

## 8. Strategi Model AI On-Device

### 8.1 Model yang Didukung

| Fungsi | Model | Format | Ukuran File | RAM Saat Inferensi | Engine |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **STT Ringan** | `whisper-base` | GGML Q5_0 | ~75 MB | ~300 MB | whisper.cpp |
| **STT Berat** | `whisper-large-v3-turbo` | GGML Q5_0 | ~600 MB | ~2 GB | whisper.cpp |
| **VAD** | Silero VAD v5 | ONNX | ~2 MB | ~15 MB | ONNX Runtime Mobile |
| **Summarizer Ringan** | Gemma 2 2B Instruct | GGUF Q4_K_M | ~1.5 GB | ~2 GB | llama.cpp |
| **Summarizer Berat** | Qwen 2.5 3B Instruct | GGUF Q4_K_M | ~2 GB | ~2.5 GB | llama.cpp |

### 8.2 Strategi Distribusi Model

Model AI **tidak disertakan** di dalam APK (agar ukuran APK tetap ≤ 15MB sesuai NFR-10).

Mekanisme distribusi:
1. Saat pertama kali pengguna memilih profil pemrosesan offline, aplikasi menampilkan dialog yang menjelaskan ukuran model yang perlu diunduh.
2. Model diunduh dari CDN/GitHub Releases ke folder `context.filesDir/models/`.
3. Unduhan mendukung resume (jika koneksi terputus, lanjutkan dari posisi terakhir).
4. Integritas file diverifikasi dengan SHA-256 checksum sebelum digunakan.

### 8.3 Lifecycle Memori Model

```
load() ──→ Model di-load ke RAM ──→ transcribe()/summarize() ──→ release() ──→ RAM dibebaskan
```

Model **tidak** tetap berada di memori setelah proses selesai. Setelah transkripsi/perangkuman selesai, `release()` dipanggil untuk membebaskan seluruh memori native yang dialokasikan oleh engine C++. Ini mencegah aplikasi mengonsumsi 2GB RAM secara terus-menerus saat idle.

---

## 9. Strategi Keamanan & Enkripsi

### 9.1 Diagram Keamanan Data

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Android Keystore                               │
│                       (Hardware-backed jika tersedia)                       │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  MasterKey (AES-256-GCM)                                              │  │
│  │  Digunakan untuk:                                                     │  │
│  │  1. Enkripsi database SQLCipher (Passphrase generator)                │  │
│  │  2. Enkripsi file audio pasca-rekam via Tink StreamingAead            │  │
│  │     (AES256_GCM_HKDF_4KB, seekable per 4KB segment)                   │  │
│  │  3. Enkripsi API key di EncryptedSharedPreferences                    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
            │                               │                        │
            ▼                               ▼                        ▼
┌───────────────────────┐       ┌──────────────────────┐  ┌───────────────────────────┐
│  Room DB + SQLCipher  │       │ Tink StreamingAead   │  │ EncryptedSharedPreferences │
│  (AES-256-CBC)        │       │ Audio (AES-256-GCM)  │  │ (AES-256-SIV + GCM)       │
│                       │       │                      │  │                           │
│  - Transkrip teks     │       │  - Audio WAV         │  │  - API Key Groq           │
│  - Notulensi          │       │    dienkripsi setelah│  │  - API Key OpenAI         │
│  - Metadata rekaman   │       │    rekaman selesai   │  │  - Preferensi sensitif    │
└───────────────────────┘       └──────────────────────┘  └───────────────────────────┘
```

#### Jendela Waktu WAV Polos & Enkripsi Encrypt-After-Stop
Selama proses perekaman aktif berlangsung, audio ditulis sebagai file WAV PCM polos di dalam direktori penyimpanan internal privat aplikasi (`context.filesDir` / sandbox Android). Hal ini merupakan keputusan desain yang disengaja agar:
1. Header WAV dapat diperbarui secara *in-place* di offset 4 dan 40 tanpa me-rewrite file besar berukuran ratusan megabyte.
2. Rekaman yang terputus mendadak akibat baterai habis atau *process kill* oleh OS dapat dipulihkan (*disaster recovery* NFR-11, AC-01.2) hingga batas sampel 2-byte terakhir via `WavHeaderUtil.recoverUnfinalizedHeader()`.
3. Beban CPU dan I/O baterai selama proses perekaman tetap minimal.

Segera setelah perekaman dihentikan (`STOPPED`), file audio secara atomik dienkripsi menggunakan Google Tink `StreamingAead` (AES256_GCM_HKDF_4KB), file WAV polos langsung dihapus permanen, dan flag `isEncrypted = true` dicatat di database Room. Pemutaran audio (F1-10) dan pipeline transkripsi STT (F2-08) membaca audio secara aman melalui *decrypting SeekableByteChannel* tanpa pernah menulis file audio polos kembali ke disk.

### 9.2 Prinsip Zero-Knowledge & Zero-Telemetry

1. **Tidak ada server backend.** Aplikasi Catet AI tidak memiliki server sendiri. Tidak ada endpoint yang menerima data pengguna.
2. **Mode BYOK:** Audio dikirim langsung dari HP pengguna ke API Groq/OpenAI menggunakan API key milik pengguna sendiri. Catet AI tidak bertindak sebagai proxy. Setelah API mengembalikan hasil teks, hasilnya langsung disimpan ke database lokal dan audio tidak di-cache oleh API provider (sesuai kebijakan Groq dan OpenAI untuk Whisper API).
3. **Zero-Telemetry & Local Crash Logging:** Aplikasi sama sekali tidak menggunakan Firebase Crashlytics atau SDK analitik cloud pihak ketiga. Seluruh crash exception dan error dicatat ke tabel lokal `crash_logs` di dalam database terenkripsi. Pengguna memiliki hak penuh untuk memeriksa, menghapus, atau mengekspor log tersebut jika ingin melaporkan bug secara sukarela.

---

## 10. Strategi Penanganan Background Process

### 10.1 ForegroundService untuk Perekaman

```kotlin
// AndroidManifest.xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- Android 13+ -->

<service
    android:name=".core.audio.RecordingForegroundService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

### 10.2 Mitigasi Battery Optimization OEM

Banyak OEM (Xiaomi MIUI, Samsung One UI, Oppo ColorOS, Vivo Funtouch) secara agresif mematikan background service meskipun sudah menggunakan ForegroundService. Strategi mitigasi:

1. **Panduan Satu Kali (One-Time Guide):** Saat pertama kali pengguna memulai perekaman, tampilkan dialog dengan instruksi visual untuk menonaktifkan battery optimization khusus untuk Catet AI. Instruksi berbeda per OEM (referensi: dontkillmyapp.com).

2. **Partial WakeLock:** Selama perekaman aktif, aplikasi menahan `PARTIAL_WAKE_LOCK` agar CPU tidak tidur dan AudioRecord buffer tidak terhenti.

3. **Watchdog Timer:** Setiap 60 detik selama perekaman, service menulis timestamp terakhir ke SharedPreferences. Jika service di-restart oleh sistem, periksa apakah ada perekaman yang terpotong (`START_STICKY`).

---

## 11. Strategi Pengelolaan Memori & Performa

### 11.1 Profil Memori per Operasi

| Operasi | Komponen di Memori | Estimasi RSS | Strategi |
| :--- | :--- | :--- | :--- |
| **Idle (daftar rekaman)** | UI + Room query | ~80 MB | Normal |
| **Merekam** | AudioRecord buffer (160KB) + Service | ~120 MB | Ringan |
| **Transkripsi (base)** | Whisper base model loaded | ~350 MB | Load → proses → release |
| **Transkripsi (turbo)** | Whisper turbo model loaded | ~2200 MB | Load → proses → release. Cek RAM tersedia sebelum load. |
| **Perangkuman (Gemma 2B)** | LLM model loaded | ~2000 MB | Load → proses → release. Tidak boleh bersamaan dengan Whisper. |

### 11.2 Aturan Penting

1. **Tidak boleh ada 2 model AI di memori secara bersamaan.** Proses transkripsi harus selesai dan model Whisper di-release sebelum model LLM di-load untuk perangkuman.
2. **Cek memori sebelum load model.** Sebelum `WhisperEngine.load()`, periksa `ActivityManager.getMemoryInfo()`. Jika `availMem` kurang dari 1.5x ukuran model, tampilkan peringatan kepada pengguna.
3. **Chunk processing untuk audio panjang.** Audio lebih dari 30 menit diproses per chunk 30 menit (EC-09 di PRD) untuk menghindari akumulasi memori transkrip.

---

## 12. Dependency Map & Versi Library

### 12.1 Core Android

| Library | Versi Target | Fungsi |
| :--- | :--- | :--- |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.0+ | Bahasa Kotlin |
| `androidx.core:core-ktx` | 1.13+ | Ekstensi Kotlin untuk Android API |
| `androidx.activity:activity-compose` | 1.9+ | Integrasi Activity dengan Compose |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8+ | ViewModel dengan Compose |
| `androidx.navigation:navigation-compose` | 2.8+ | Navigasi antar screen |

### 12.2 UI

| Library | Versi Target | Fungsi |
| :--- | :--- | :--- |
| `androidx.compose.material3:material3` | 1.3+ | Komponen UI Material 3 |
| `androidx.compose.ui:ui` | 1.7+ | Framework UI Compose |

### 12.3 Data & Storage

| Library | Versi Target | Fungsi |
| :--- | :--- | :--- |
| `androidx.room:room-runtime` | 2.6+ | Database Room |
| `androidx.room:room-ktx` | 2.6+ | Ekstensi Coroutines untuk Room |
| `net.zetetic:android-database-sqlcipher` | 4.5+ | Enkripsi database SQLCipher |
| `androidx.security:security-crypto` | 1.1+ | EncryptedSharedPreferences |

### 12.4 DI & Concurrency

| Library | Versi Target | Fungsi |
| :--- | :--- | :--- |
| `com.google.dagger:hilt-android` | 2.51+ | Dependency Injection |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8+ | Coroutines untuk Android |

### 12.5 AI & Native

| Library | Versi Target | Fungsi |
| :--- | :--- | :--- |
| `whisper.cpp` (source, via CMake) | latest | STT engine (C++) |
| `llama.cpp` (source, via CMake) | latest | LLM engine (C++) |
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.18+ | ONNX Runtime untuk Silero VAD |

### 12.6 Networking (hanya untuk mode BYOK)

| Library | Versi Target | Fungsi |
| :--- | :--- | :--- |
| `com.squareup.okhttp3:okhttp` | 4.12+ | HTTP client untuk API calls |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7+ | JSON serialization/deserialization |

---

## 13. Strategi Pengujian (Testing Strategy)

### 13.1 Piramida Pengujian

```
            ▲
           / \
          / E2E \          ← 2–3 test: Alur lengkap rekam → transkrip → notulensi
         /───────\
        /  Integ  \        ← 5–10 test: Repository + Database + File System
       /───────────\
      /  Unit Test  \      ← 30+ test: UseCase, ViewModel, Utility, Converter
     /───────────────\
```

### 13.2 Target Pengujian per Modul

| Modul | Jenis Test | Contoh Test Case |
| :--- | :--- | :--- |
| **UseCase** | Unit Test (JVM) | `TranscribeAudioUseCase` memanggil repository dengan parameter yang benar; mengembalikan error jika recording tidak ditemukan. |
| **ViewModel** | Unit Test (JVM) | `RecordViewModel` mengubah state dari `Idle` → `Recording` → `Stopped` sesuai intent. |
| **Room DAO** | Instrumented Test (emulator) | Insert recording → query by id → verifikasi data sesuai. FTS search mengembalikan hasil yang mengandung kata kunci. |
| **WavHeaderUtil** | Unit Test (JVM) | Generate WAV header → parse ulang → verifikasi sample rate, bit depth, channel count benar. |
| **SummaryPromptTemplate** | Unit Test (JVM) | Template menghasilkan string prompt yang mengandung teks transkrip dan instruksi format yang benar. |
| **Audio Engine** | Instrumented Test (device) | Rekam 10 detik → stop → baca file WAV → verifikasi durasi ~10 detik, format PCM 16kHz mono. |

---

## 14. Keputusan Arsitektur yang Ditolak (Rejected Alternatives)

Bagian ini mencatat opsi yang dipertimbangkan namun **tidak dipilih**, beserta alasannya. Ini mencegah pertanyaan berulang ("kenapa tidak pakai X?") di kemudian hari.

### 14.1 Flutter (Ditolak)

| Alasan Pertimbangan | Alasan Penolakan |
| :--- | :--- |
| Cross-platform: satu codebase untuk Android dan iOS. | Catet AI bergantung pada akses langsung ke `AudioRecord`, `ForegroundService`, NDK/JNI, dan Android Keystore. Semua ini memerlukan bridge/channel di Flutter yang menambah kompleksitas, risiko bug, dan overhead performa. Background audio service di Flutter sangat rawan di-kill oleh OEM. |

### 14.2 Google On-Device Speech Recognizer (Ditolak untuk STT Utama)

| Alasan Pertimbangan | Alasan Penolakan |
| :--- | :--- |
| Sudah built-in di Android, sangat ringan, gratis. | Tidak menghasilkan word-level timestamp yang akurat. Tidak mendukung `initial_prompt` / vocabulary biasing. Tidak bisa memproses file audio offline (hanya streaming real-time). Kualitas Bahasa Indonesia jauh di bawah Whisper. |

### 14.3 MediaPipe LLM Inference (Dipertimbangkan sebagai Alternatif)

| Alasan Pertimbangan | Alasan Penolakan (untuk saat ini) |
| :--- | :--- |
| Framework resmi Google untuk inferensi LLM di Android. API Kotlin-first. | Pada saat penulisan dokumen ini, model yang didukung oleh MediaPipe LLM Inference masih terbatas dibandingkan llama.cpp yang mendukung hampir semua model GGUF. Jika MediaPipe LLM matang, bisa diadopsi sebagai pengganti llama.cpp di iterasi mendatang. |

### 14.4 ObjectBox / Realm (Ditolak untuk Database)

| Alasan Pertimbangan | Alasan Penolakan |
| :--- | :--- |
| Performa baca/tulis tinggi, API objek-langsung tanpa ORM boilerplate. | Room adalah rekomendasi resmi Google dengan dukungan jangka panjang terjamin. Room memiliki compile-time query verification (mendeteksi kesalahan SQL saat build, bukan saat runtime). Integrasi SQLCipher untuk enkripsi sudah mature dan terdokumentasi baik. FTS4 untuk full-text search sudah built-in. |

### 14.5 Menyertakan Model AI di Dalam APK (Ditolak)

| Alasan Pertimbangan | Alasan Penolakan |
| :--- | :--- |
| Pengguna langsung bisa pakai tanpa unduh tambahan. | Model Whisper base saja sudah 75MB. Jika disertakan di APK, ukuran APK membengkak menjadi 90MB+. Google Play Store memiliki batas 150MB untuk APK dan merekomendasikan APK sekecil mungkin. Pengguna di Indonesia banyak yang memiliki kuota internet terbatas dan enggan mengunduh aplikasi besar. |

---

> **Status Dokumen:** DRAFT v1.0.0
> **Langkah Selanjutnya:** Review → Approval → Lanjut ke `03_SPRINT_ROADMAP.md`
