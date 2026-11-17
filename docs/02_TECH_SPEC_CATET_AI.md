# 02 — Technical Specification (Tech Spec)
## Catet AI v2: Arsitektur Teknis Aplikasi Transkripsi Berbasis Impor

| Metadata            | Isi                                        |
| :------------------ | :----------------------------------------- |
| **Versi Dokumen**   | 2.0.0                                      |
| **Status**          | DRAFT                                      |
| **Penulis**         | Luthfi Fahmi Alviansyah                    |
| **Tanggal Dibuat**  | 18 November 2026                           |
| **Revisi Terakhir** | 18 November 2026                           |
| **Referensi PRD**   | `01_PRD_CATET_AI.md` v2.0.0               |

---

## Daftar Isi

1. [Arsitektur Tingkat Tinggi](#1-arsitektur-tingkat-tinggi)
2. [Tumpukan Teknologi](#2-tumpukan-teknologi)
3. [Arsitektur Modul](#3-arsitektur-modul)
4. [Skema Basis Data](#4-skema-basis-data)
5. [Pipeline Pemrosesan Audio](#5-pipeline-pemrosesan-audio)
6. [Integrasi whisper.cpp (JNI/NDK)](#6-integrasi-whispercpp-jindk)
7. [Perangkum Notulensi](#7-perangkum-notulensi)
8. [Pembuatan PDF](#8-pembuatan-pdf)
9. [Pengelolaan Model](#9-pengelolaan-model)
10. [Keamanan & Enkripsi](#10-keamanan--enkripsi)
11. [Strategi Pengujian](#11-strategi-pengujian)
12. [Konvensi Proyek](#12-konvensi-proyek)

---

## 1. Arsitektur Tingkat Tinggi

### 1.1 Diagram Arsitektur

```
┌────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │HomeScreen│  │ImportFlow│  │DetailScr │  │  SettingsScreen  │  │
│  │(Daftar)  │  │(SAF Pick)│  │(2 Tab)   │  │  (Model/BYOK)   │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────────┬─────────┘  │
│       │              │             │                  │            │
│  ┌────▼──────────────▼─────────────▼──────────────────▼─────────┐ │
│  │                    ViewModels (MVI)                           │ │
│  │  HomeViewModel · ImportViewModel · DetailViewModel · etc.    │ │
│  └──────────────────────────┬───────────────────────────────────┘ │
├─────────────────────────────┼────────────────────────────────────-┤
│                        DOMAIN LAYER                                │
│  ┌──────────────────────────▼───────────────────────────────────┐ │
│  │                      Use Cases                               │ │
│  │  ImportAudioUseCase · TranscribeUseCase · SummarizeUseCase  │ │
│  │  ExportPdfUseCase · SearchTranscriptUseCase · etc.          │ │
│  └──────────────────────────┬───────────────────────────────────┘ │
│  ┌──────────────────────────▼───────────────────────────────────┐ │
│  │                   Repository Interfaces                      │ │
│  │  RecordingRepository · TranscriptRepository · ModelRepo     │ │
│  └──────────────────────────┬───────────────────────────────────┘ │
├─────────────────────────────┼────────────────────────────────────-┤
│                         DATA LAYER                                 │
│  ┌──────────────────────────▼───────────────────────────────────┐ │
│  │                 Repository Implementations                   │ │
│  └───┬──────────┬───────────┬──────────┬──────────┬────────────┘ │
│      │          │           │          │          │               │
│  ┌───▼───┐ ┌───▼────┐ ┌────▼───┐ ┌────▼───┐ ┌───▼─────┐       │
│  │Room DB│ │File I/O│ │whisper │ │Summary │ │BYOK API │       │
│  │+SQLCip│ │+Tink   │ │.cpp   │ │Engine  │ │(Groq/   │       │
│  │       │ │        │ │(JNI)  │ │(Kotlin)│ │OpenAI)  │       │
│  └───────┘ └────────┘ └────────┘ └────────┘ └─────────┘       │
│                              │                                    │
│                     ┌────────▼────────┐                           │
│                     │  Native Layer   │                           │
│                     │  (C/C++ NDK)    │                           │
│                     │  whisper.cpp    │                           │
│                     │  + FFmpeg libs  │                           │
│                     └─────────────────┘                           │
└────────────────────────────────────────────────────────────────────┘
```

### 1.2 Pola Arsitektur

| Aspek | Pilihan | Alasan |
| :--- | :--- | :--- |
| **Arsitektur** | Clean Architecture (3 lapisan) | Pemisahan jelas antara UI, logika bisnis, dan sumber data. Mudah diuji. |
| **Pola UI** | MVI (Model-View-Intent) | Aliran data satu arah. State dapat diprediksi dan di-*debug*. |
| **Injeksi Dependensi** | Hilt (Dagger) | Standar resmi Android. Sudah terpasang di kode sumber saat ini. |
| **Pemrograman Asinkron** | Kotlin Coroutines + Flow | Ringan, terstruktur, terintegrasi baik dengan Jetpack. |
| **Navigasi** | Jetpack Navigation Compose | Navigasi deklaratif, mendukung *deep link* dan *safe args*. |
| **Basis Data** | Room + SQLCipher | ORM resmi Android dengan enkripsi transparan. |

---

## 2. Tumpukan Teknologi

### 2.1 Dependensi Utama

| Kategori | Pustaka | Versi (Target) | Lisensi | Fungsi |
| :--- | :--- | :--- | :--- | :--- |
| **UI** | Jetpack Compose (BOM) | 2024.xx | Apache 2.0 | Toolkit UI deklaratif |
| **UI** | Material 3 | Ikut BOM | Apache 2.0 | Komponen desain material |
| **DI** | Hilt | 2.51+ | Apache 2.0 | Injeksi dependensi |
| **DB** | Room | 2.6+ | Apache 2.0 | ORM basis data lokal |
| **DB** | SQLCipher Android | 4.6+ | BSD | Enkripsi basis data |
| **Enkripsi** | Google Tink | 1.14+ | Apache 2.0 | Enkripsi berkas audio (StreamingAead) |
| **Jaringan** | OkHttp + Retrofit | 4.12+ / 2.11+ | Apache 2.0 | Unduh model & API BYOK |
| **STT** | whisper.cpp (JNI) | Terbaru dari `main` | MIT | Mesin transkripsi lokal |
| **Audio** | Android MediaCodec | Bawaan SDK | — | Dekoder audio perangkat keras |
| **Audio (cadangan)** | FFmpeg (mobile-ffmpeg / build kustom) | 6.x | LGPL 2.1 | Dekoder audio perangkat lunak |
| **PDF** | Android PdfDocument | Bawaan SDK (API 19+) | — | Pembuatan berkas PDF |
| **Serialisasi** | Kotlinx Serialization | 1.7+ | Apache 2.0 | JSON untuk API BYOK & konfigurasi |
| **Pengujian** | JUnit 5 + Turbine + MockK | Terbaru | — | *Unit test* & *Flow test* |

### 2.2 Konfigurasi Build

```kotlin
// build.gradle.kts (app)
android {
    namespace = "com.catetai.app"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 26          // Android 8.0
        targetSdk = 35       // Android 15
        
        ndk {
            // whisper.cpp hanya mendukung ARM64
            abiFilters += listOf("arm64-v8a")
        }
    }
    
    buildFeatures {
        compose = true
    }
    
    // Lokasi keluaran skema Room untuk migrasi
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// Modul native (whisper.cpp)
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}
```

**Penjelasan `abiFilters`:** Kita hanya menargetkan `arm64-v8a` karena:
1. Model Whisper LV3T Q5 membutuhkan instruksi NEON yang optimal di ARM64.
2. Perangkat ARMv7 32-bit umumnya memiliki RAM < 4 GB — tidak cukup untuk model ini.
3. Mengurangi ukuran APK (~50% lebih kecil dibanding menyertakan semua ABI).

---

## 3. Arsitektur Modul

### 3.1 Struktur Paket

```
com.catetai.app/
├── core/                           # Utilitas lintas-modul
│   ├── crypto/                     # Enkripsi (Tink, Keystore)
│   │   ├── TinkEncryptionManager.kt
│   │   └── KeystoreManager.kt
│   ├── di/                         # Modul Hilt global
│   │   ├── DispatcherQualifiers.kt # @IoDispatcher, @DefaultDispatcher
│   │   ├── DispatchersModule.kt
│   │   ├── DatabaseModule.kt
│   │   └── NetworkModule.kt
│   └── util/                       # Ekstensi & utilitas umum
│       ├── FileUtil.kt
│       └── FormatUtil.kt
│
├── data/                           # DATA LAYER
│   ├── local/                      # Sumber data lokal
│   │   ├── CatetAiDatabase.kt      # Room DB + SQLCipher
│   │   ├── entity/                 # Entitas Room
│   │   │   ├── RecordingEntity.kt
│   │   │   ├── TranscriptSegmentEntity.kt
│   │   │   └── SummaryEntity.kt
│   │   ├── dao/                    # Data Access Objects
│   │   │   ├── RecordingDao.kt
│   │   │   ├── TranscriptDao.kt
│   │   │   └── SummaryDao.kt
│   │   └── converter/              # TypeConverters Room
│   │       └── Converters.kt
│   ├── remote/                     # Sumber data jarak jauh (BYOK)
│   │   ├── ByokApiService.kt       # Retrofit interface
│   │   └── ByokApiMapper.kt
│   ├── repository/                 # Implementasi Repository
│   │   ├── RecordingRepositoryImpl.kt
│   │   ├── TranscriptRepositoryImpl.kt
│   │   ├── SummaryRepositoryImpl.kt
│   │   └── ModelRepositoryImpl.kt
│   └── di/                         # Modul Hilt data layer
│       └── RepositoryModule.kt
│
├── domain/                         # DOMAIN LAYER
│   ├── model/                      # Model domain (POJO murni)
│   │   ├── Recording.kt
│   │   ├── TranscriptSegment.kt
│   │   ├── Summary.kt
│   │   └── ProcessingStatus.kt
│   ├── repository/                 # Interface Repository
│   │   ├── RecordingRepository.kt
│   │   ├── TranscriptRepository.kt
│   │   ├── SummaryRepository.kt
│   │   └── ModelRepository.kt
│   └── usecase/                    # Use Cases
│       ├── ImportAudioUseCase.kt
│       ├── DecodeAudioUseCase.kt
│       ├── TranscribeAudioUseCase.kt
│       ├── SummarizeTranscriptUseCase.kt
│       ├── ExportPdfUseCase.kt
│       ├── SearchTranscriptUseCase.kt
│       └── DownloadModelUseCase.kt
│
├── engine/                         # MESIN AI
│   ├── whisper/                    # Ikatan whisper.cpp
│   │   ├── WhisperJni.kt           # Deklarasi native method
│   │   ├── WhisperEngine.kt        # Wrapper Kotlin tingkat tinggi
│   │   └── WhisperConfig.kt        # Konfigurasi (bahasa, prompt, dsb.)
│   ├── decoder/                    # Dekoder audio
│   │   ├── AudioDecoder.kt         # Interface
│   │   ├── MediaCodecDecoder.kt    # Implementasi MediaCodec
│   │   └── FfmpegDecoder.kt        # Implementasi FFmpeg (cadangan)
│   └── summarizer/                 # Perangkum
│       ├── Summarizer.kt           # Interface
│       ├── ExtractSummarizer.kt    # TextRank/TF-IDF (Kotlin murni)
│       └── ByokSummarizer.kt       # Groq/OpenAI API
│
├── presentation/                   # PRESENTATION LAYER
│   ├── home/                       # Layar utama (daftar rekaman)
│   │   ├── HomeScreen.kt
│   │   ├── HomeViewModel.kt
│   │   └── components/
│   │       └── RecordingItemCard.kt
│   ├── import_flow/                # Alur impor berkas
│   │   ├── ImportScreen.kt
│   │   └── ImportViewModel.kt
│   ├── detail/                     # Layar detail (2 tab)
│   │   ├── DetailScreen.kt
│   │   ├── DetailViewModel.kt
│   │   ├── TranscriptTab.kt        # Tab transkrip asli
│   │   ├── SummaryTab.kt           # Tab notulensi
│   │   └── components/
│   │       ├── SegmentItem.kt       # Segmen teks + tanda waktu
│   │       └── MiniPlayer.kt       # Pemutar audio mini
│   ├── settings/                   # Pengaturan
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   ├── export/                     # Ekspor PDF
│   │   └── PdfGenerator.kt
│   ├── navigation/                 # Navigasi
│   │   └── NavGraph.kt
│   └── theme/                      # Tema & token desain
│       ├── Theme.kt
│       ├── Color.kt
│       ├── Type.kt
│       └── Dimens.kt
│
└── src/main/cpp/                   # NATIVE LAYER (C/C++)
    ├── CMakeLists.txt              # Build system NDK
    ├── whisper_jni.cpp             # Ikatan JNI untuk whisper.cpp
    └── whisper.cpp/                # Submodule: pustaka whisper.cpp
```

### 3.2 Diagram Aliran Data (Proses Transkripsi)

```
ImportAudioUseCase         DecodeAudioUseCase         TranscribeAudioUseCase
      │                          │                           │
      ▼                          ▼                           ▼
┌───────────┐            ┌──────────────┐            ┌──────────────┐
│ SAF Intent│            │ MediaCodec / │            │ WhisperEngine│
│ → salin   │───berkas──▶│ FFmpeg       │───PCM────▶│ (JNI)        │
│   berkas  │  audio     │ → PCM 16kHz  │  16kHz    │ → segmen     │
│   ke app  │  asli      │   16-bit Mono│  Mono     │   teks       │
│   storage │            │              │           │   + waktu    │
└───────────┘            └──────────────┘            └──────┬───────┘
                                                            │
                                                            ▼
                                                   ┌──────────────┐
                                                   │ Room DB      │
                                                   │ (simpan      │
                                                   │  segmen)     │
                                                   └──────┬───────┘
                                                          │
                                              ┌───────────▼──────────┐
                                              │ SummarizeUseCase     │
                                              │ ExtractSummarizer /  │
                                              │ ByokSummarizer       │
                                              └───────────┬──────────┘
                                                          │
                                                          ▼
                                                   ┌──────────────┐
                                                   │ Room DB      │
                                                   │ (simpan      │
                                                   │  ringkasan)  │
                                                   └──────────────┘
```

---

## 4. Skema Basis Data

### 4.1 Diagram Relasi Entitas

```
┌─────────────────────────┐       ┌──────────────────────────┐
│      recordings         │       │   transcript_segments    │
├─────────────────────────┤       ├──────────────────────────┤
│ id         (PK, Long)   │──1:N─▶│ id          (PK, Long)  │
│ title      (String)     │       │ recording_id (FK, Long)  │
│ original_file_name (Str)│       │ start_ms    (Long)       │
│ audio_file_name (String)│       │ end_ms      (Long)       │
│ duration_ms (Long)      │       │ text        (String)     │
│ file_size_bytes (Long)  │       │ created_at  (Long)       │
│ mime_type   (String)    │       └──────────────────────────┘
│ status      (String)    │
│ imported_at (Long)      │       ┌──────────────────────────┐
│ created_at  (Long)      │       │       summaries          │
│ updated_at  (Long)      │       ├──────────────────────────┤
│                         │──1:1─▶│ id          (PK, Long)  │
└─────────────────────────┘       │ recording_id (FK, Long)  │
                                  │ summary_text (String)    │
                                  │ decisions    (String)    │
                                  │ action_items (String)    │
                                  │ mode         (String)    │
                                  │ created_at   (Long)      │
                                  └──────────────────────────┘
```

### 4.2 Entitas `RecordingEntity`

```kotlin
@Entity(
    tableName = "recordings",
    indices = [Index(value = ["imported_at"])]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Judul yang bisa diubah pengguna (default: nama berkas asli)
    val title: String,
    
    // Nama berkas asli yang diimpor (untuk referensi, tidak berubah)
    @ColumnInfo(name = "original_file_name")
    val originalFileName: String,
    
    // Nama berkas di penyimpanan internal app (relatif, bukan path absolut)
    // Contoh: "audio_1700000000000.enc" (terenkripsi)
    @ColumnInfo(name = "audio_file_name")
    val audioFileName: String,
    
    // Durasi audio dalam milidetik
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    
    // Ukuran berkas asli dalam byte
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    
    // MIME type berkas asli (audio/mp4, audio/mpeg, dsb.)
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    // Status pemrosesan: IMPORTED, DECODING, TRANSCRIBING,
    // SUMMARIZING, COMPLETED, FAILED, INTERRUPTED
    val status: String,
    
    // Kapan berkas diimpor (epoch ms)
    @ColumnInfo(name = "imported_at")
    val importedAt: Long,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Penjelasan kolom `audio_file_name`:** Menyimpan **nama berkas relatif** (bukan *path* absolut) karena:
1. *Path* absolut berubah jika pengguna melakukan *backup-restore* ke perangkat lain.
2. Direktori penyimpanan internal (`context.filesDir`) sudah diketahui aplikasi — cukup gabungkan: `File(context.filesDir, "audio/$audioFileName")`.

### 4.3 Entitas `TranscriptSegmentEntity`

```kotlin
@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE  // Hapus segmen jika rekaman dihapus
        )
    ],
    indices = [Index(value = ["recording_id"])]
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "recording_id")
    val recordingId: Long,
    
    // Waktu mulai segmen dalam milidetik dari awal audio
    @ColumnInfo(name = "start_ms")
    val startMs: Long,
    
    // Waktu akhir segmen dalam milidetik
    @ColumnInfo(name = "end_ms")
    val endMs: Long,
    
    // Teks hasil transkripsi (verbatim — persis apa yang diucapkan)
    val text: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

**Penjelasan `CASCADE`:** Ketika sebuah rekaman dihapus (`DELETE FROM recordings WHERE id = X`), semua segmen transkrip yang merujuk ke rekaman tersebut otomatis ikut terhapus oleh Room. Ini mencegah data "yatim" (*orphan*) yang tidak ada induknya.

### 4.4 Entitas `SummaryEntity`

```kotlin
@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recording_id"])]
)
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "recording_id")
    val recordingId: Long,
    
    // Teks ringkasan rapat (Markdown)
    @ColumnInfo(name = "summary_text")
    val summaryText: String,
    
    // Daftar keputusan rapat (Markdown, bernomor)
    val decisions: String,
    
    // Daftar tugas / tindak lanjut (JSON array)
    // Format: [{"task":"...", "pic":"...", "deadline":"..."}]
    @ColumnInfo(name = "action_items")
    val actionItems: String,
    
    // Mode yang digunakan: "extractive" atau "byok_cloud"
    val mode: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

### 4.5 FTS (Full-Text Search)

```kotlin
// Tabel virtual FTS4 untuk pencarian cepat di teks transkrip
@Fts4(contentEntity = TranscriptSegmentEntity::class)
@Entity(tableName = "transcript_segments_fts")
data class TranscriptSegmentFts(
    @ColumnInfo(name = "text")
    val text: String
)
```

**Penjelasan FTS4:** Room FTS4 membuat indeks teks penuh di belakang layar. Ketika pengguna mengetik "API deployment" di kolom pencarian, Room mencocokkan kata-kata tersebut di seluruh segmen transkrip dari semua rekaman dalam waktu < 1 detik, bahkan untuk ratusan rekaman.

---

## 5. Pipeline Pemrosesan Audio

### 5.1 Alur Dekoding

```
Berkas Audio Impor                  MediaCodec API
(M4A/MP3/OGG/AAC/...)              (Dekoder Perangkat Keras)
        │                                  │
        ▼                                  ▼
┌───────────────┐    didukung?    ┌──────────────────┐
│ Deteksi MIME  │───── YA ──────▶│ MediaCodecDecoder │
│ type & format │                │ → PCM 16kHz Mono  │
└───────┬───────┘                └────────┬──────────┘
        │                                 │
        │ TIDAK                           │
        ▼                                 ▼
┌───────────────┐                ┌──────────────────┐
│ FfmpegDecoder │                │ Berkas PCM temp   │
│ (cadangan)    │───────────────▶│ siap untuk        │
│ → PCM 16kHz   │                │ Whisper           │
└───────────────┘                └──────────────────┘
```

### 5.2 Implementasi `AudioDecoder`

```kotlin
/**
 * Interface dekoder audio.
 * Mengubah berkas audio dari format apa pun ke PCM 16kHz 16-bit Mono
 * yang dibutuhkan oleh Whisper.
 */
interface AudioDecoder {
    
    /**
     * Mendekode berkas audio ke format PCM.
     * @param inputFile Berkas audio masukan (M4A, MP3, dsb.)
     * @param outputFile Berkas PCM keluaran (16kHz, 16-bit, Mono)
     * @param onProgress Callback kemajuan (0.0f - 1.0f)
     * @return Result berisi path berkas PCM, atau error.
     */
    suspend fun decode(
        inputFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File>
    
    /**
     * Memeriksa apakah dekoder ini mendukung format berkas tertentu.
     */
    fun supportsFormat(mimeType: String): Boolean
}
```

**Penjelasan mengapa 16kHz Mono:**
- Model Whisper dilatih secara eksklusif pada audio 16kHz. Memberikan audio dengan *sample rate* berbeda akan menurunkan akurasi drastis.
- Mono karena Whisper tidak memanfaatkan informasi stereo — kanal ganda hanya membuang memori.
- 16-bit adalah kedalaman bit minimum untuk kualitas ucapan yang layak.

### 5.3 Strategi Dekoder

| Prioritas | Dekoder | Format yang Didukung | Kelebihan | Kekurangan |
| :---: | :--- | :--- | :--- | :--- |
| 1 (utama) | `MediaCodecDecoder` | M4A, AAC, MP3, OGG, FLAC, WAV, OPUS | Memanfaatkan perangkat keras (cepat, hemat baterai). Tanpa pustaka tambahan. | Dukungan format bervariasi antar perangkat & versi Android. |
| 2 (cadangan) | `FfmpegDecoder` | Semua format audio umum + WMA, AMR, dsb. | Konsisten di semua perangkat. | Menambah ukuran APK (~5–10 MB). Pustaka pihak ketiga (LGPL). |

---

## 6. Integrasi whisper.cpp (JNI/NDK)

### 6.1 Arsitektur JNI

```
┌─────────────────────────────────────────────────┐
│              Kotlin / JVM                        │
│                                                  │
│  WhisperEngine.kt                                │
│  ├─ loadModel(modelPath: String)                │
│  ├─ transcribe(pcmFile, config, onProgress)     │
│  ├─ cancel()                                     │
│  └─ release()                                    │
│         │                                        │
│         │ memanggil                               │
│         ▼                                        │
│  WhisperJni.kt  (deklarasi native method)       │
│  ├─ external fun nativeInit(modelPath: String)  │
│  ├─ external fun nativeTranscribe(...)          │
│  ├─ external fun nativeCancel()                 │
│  └─ external fun nativeRelease()                │
│         │                                        │
├─────────┼────────── JNI Boundary ───────────────┤
│         ▼                                        │
│  whisper_jni.cpp  (C++ implementation)          │
│  ├─ Java_..._nativeInit()                       │
│  ├─ Java_..._nativeTranscribe()                 │
│  │   ├─ whisper_full_params_by_ref()            │
│  │   ├─ whisper_full()                           │
│  │   └─ callback ke Kotlin (onProgress)         │
│  ├─ Java_..._nativeCancel()                     │
│  └─ Java_..._nativeRelease()                    │
│         │                                        │
│         ▼                                        │
│  whisper.cpp (pustaka C, git submodule)          │
│  └─ whisper.h                                    │
└─────────────────────────────────────────────────┘
```

### 6.2 Deklarasi JNI (Kotlin)

```kotlin
/**
 * Ikatan JNI ke pustaka whisper.cpp.
 * Kelas ini tidak boleh diinstansiasi secara langsung — 
 * gunakan WhisperEngine sebagai wrapper tingkat tinggi.
 */
internal object WhisperJni {
    
    init {
        System.loadLibrary("whisper_jni")
    }
    
    /** Memuat model GGUF ke memori. Mengembalikan handle (pointer). */
    external fun nativeInit(modelPath: String): Long
    
    /**
     * Menjalankan transkripsi pada berkas PCM.
     * @param contextHandle Handle dari nativeInit()
     * @param pcmPath Path berkas PCM 16kHz 16-bit Mono
     * @param language Kode bahasa ("id" untuk Indonesia, "en" untuk Inggris)
     * @param initialPrompt Kosakata kustom untuk prompt biasing
     * @param numThreads Jumlah thread CPU (disarankan: jumlah core besar)
     * @param progressCallback Callback (progress: Float) dipanggil tiap chunk
     * @return Array of [startMs, endMs, text] per segmen
     */
    external fun nativeTranscribe(
        contextHandle: Long,
        pcmPath: String,
        language: String,
        initialPrompt: String,
        numThreads: Int,
        progressCallback: Any  // (Float) -> Unit
    ): Array<Array<Any>>  // [[startMs, endMs, "text"], ...]
    
    /** Membatalkan transkripsi yang sedang berjalan. */
    external fun nativeCancel(contextHandle: Long)
    
    /** Membebaskan memori model. WAJIB dipanggil saat selesai. */
    external fun nativeRelease(contextHandle: Long)
}
```

### 6.3 Wrapper Tingkat Tinggi

```kotlin
/**
 * Wrapper Kotlin tingkat tinggi untuk mesin Whisper.
 * Mengelola siklus hidup model (muat → transkripsi → lepas)
 * dan mengubah hasil mentah JNI menjadi domain model.
 */
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private var contextHandle: Long = 0L
    
    suspend fun loadModel(modelFile: File): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                contextHandle = WhisperJni.nativeInit(modelFile.absolutePath)
                if (contextHandle == 0L) error("Gagal memuat model Whisper")
            }
        }
    
    suspend fun transcribe(
        pcmFile: File,
        config: WhisperConfig,
        onProgress: (Float) -> Unit
    ): Result<List<TranscriptSegment>> = withContext(ioDispatcher) {
        runCatching {
            val rawResult = WhisperJni.nativeTranscribe(
                contextHandle = contextHandle,
                pcmPath = pcmFile.absolutePath,
                language = config.language,
                initialPrompt = config.customVocabulary.joinToString(", "),
                numThreads = config.numThreads,
                progressCallback = onProgress
            )
            rawResult.map { segment ->
                TranscriptSegment(
                    startMs = (segment[0] as Number).toLong(),
                    endMs = (segment[1] as Number).toLong(),
                    text = segment[2] as String
                )
            }
        }
    }
    
    fun cancel() {
        if (contextHandle != 0L) WhisperJni.nativeCancel(contextHandle)
    }
    
    fun release() {
        if (contextHandle != 0L) {
            WhisperJni.nativeRelease(contextHandle)
            contextHandle = 0L
        }
    }
}
```

### 6.4 Konfigurasi Whisper

```kotlin
data class WhisperConfig(
    val language: String = "id",           // Bahasa Indonesia
    val customVocabulary: List<String> = emptyList(),
    val numThreads: Int = 4,               // Disarankan: jumlah core besar
    val translateToEnglish: Boolean = false // Terjemahkan ke Inggris?
)
```

### 6.5 CMakeLists.txt (NDK Build)

```cmake
cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

# Kompilasi whisper.cpp sebagai pustaka statis
add_subdirectory(whisper.cpp)

# Pustaka JNI (shared library yang dimuat oleh Kotlin)
add_library(whisper_jni SHARED whisper_jni.cpp)

target_link_libraries(whisper_jni
    whisper           # Dari subdirectory whisper.cpp
    log               # Android logging (__android_log_print)
)

# Optimasi ARM NEON untuk performa terbaik
target_compile_options(whisper_jni PRIVATE
    -O3 -march=armv8.2-a+fp16 -ffast-math
)
```

**Penjelasan *flag* kompilasi:**
- `-O3`: Optimasi tingkat tertinggi (kecepatan maksimum).
- `-march=armv8.2-a+fp16`: Aktifkan instruksi *half-precision floating point* yang mempercepat perhitungan model terkuantisasi.
- `-ffast-math`: Izinkan penyederhanaan kalkulasi matematika untuk kecepatan (aman untuk inferensi model, tidak untuk kalkulasi keuangan).

---

## 7. Perangkum Notulensi

### 7.1 Interface

```kotlin
/**
 * Interface perangkum notulensi.
 * Menerima daftar segmen transkrip dan menghasilkan ringkasan terstruktur.
 */
interface Summarizer {
    suspend fun summarize(
        segments: List<TranscriptSegment>,
        config: SummarizerConfig = SummarizerConfig()
    ): Result<Summary>
}

data class SummarizerConfig(
    val maxSummaryPoints: Int = 5,
    val language: String = "id"
)

data class Summary(
    val summaryText: String,     // Ringkasan (maks 5 poin utama)
    val decisions: List<String>, // Daftar keputusan
    val actionItems: List<ActionItem>,
    val mode: String             // "extractive" atau "byok_cloud"
)

data class ActionItem(
    val task: String,
    val pic: String,       // "Belum teridentifikasi" jika tidak disebutkan
    val deadline: String?  // null jika tidak disebutkan
)
```

### 7.2 Mode Ekstraktif (TextRank)

```kotlin
/**
 * Perangkum Ekstraktif berbasis TextRank/TF-IDF.
 * Murni Kotlin — tanpa model AI tambahan, tanpa internet.
 * 
 * Cara kerja (sederhana):
 * 1. Gabungkan semua segmen transkrip menjadi satu teks.
 * 2. Pecah teks menjadi kalimat-kalimat.
 * 3. Hitung kesamaan antar kalimat menggunakan TF-IDF cosine similarity.
 * 4. Bangun graf kesamaan dan jalankan algoritma PageRank.
 * 5. Pilih N kalimat dengan skor tertinggi sebagai ringkasan.
 * 6. Deteksi kandidat action item via filter kata kunci.
 */
class ExtractSummarizer @Inject constructor() : Summarizer {
    
    // Kata kunci penanda tugas/tindakan dalam Bahasa Indonesia
    private val actionKeywords = listOf(
        "tolong", "harus", "wajib", "segera",
        "paling lambat", "deadline", "tenggat",
        "besok", "minggu depan", "hari ini",
        "tanggung jawab", "PIC", "yang handle"
    )
    
    override suspend fun summarize(
        segments: List<TranscriptSegment>,
        config: SummarizerConfig
    ): Result<Summary> = runCatching {
        val fullText = segments.joinToString(" ") { it.text }
        val sentences = splitIntoSentences(fullText)
        
        // TextRank
        val rankedSentences = textRank(sentences, topN = config.maxSummaryPoints)
        
        // Deteksi action items
        val candidates = detectActionItems(sentences)
        
        Summary(
            summaryText = rankedSentences.joinToString("\n") { "• $it" },
            decisions = emptyList(), // Ekstraktif tidak bisa mendeteksi keputusan implisit
            actionItems = candidates,
            mode = "extractive"
        )
    }
    // ... implementasi TextRank & TF-IDF
}
```

### 7.3 Mode BYOK Cloud

```kotlin
/**
 * Perangkum via API cloud (Groq/OpenAI) menggunakan kunci API pengguna.
 * Mengirim TEKS transkrip (bukan audio) ke API LLM.
 */
class ByokSummarizer @Inject constructor(
    private val apiService: ByokApiService,
    private val keystoreManager: KeystoreManager
) : Summarizer {
    
    private val systemPrompt = """
        Kamu adalah asisten notulis rapat profesional berbahasa Indonesia.
        Dari transkrip rapat berikut, buatkan:
        1. RINGKASAN RAPAT (maksimal 5 poin utama)
        2. KEPUTUSAN RAPAT (daftar bernomor)
        3. DAFTAR TUGAS (format: Tugas | PIC | Tenggat)
        
        ATURAN KETAT:
        - Jangan menambahkan fakta yang TIDAK ada di transkrip.
        - Jika pembicara menggunakan kata ganti "saya/aku/kami" tanpa 
          menyebutkan nama, tulis PIC sebagai "Belum teridentifikasi (Pembicara)".
        - Hanya cantumkan nama jika disebutkan eksplisit dalam dialog.
    """.trimIndent()
    
    override suspend fun summarize(
        segments: List<TranscriptSegment>,
        config: SummarizerConfig
    ): Result<Summary> = runCatching {
        val apiKey = keystoreManager.getByokApiKey()
            ?: error("Kunci API belum dikonfigurasi")
        
        val transcript = segments.joinToString("\n") {
            "[${formatTimestamp(it.startMs)}] ${it.text}"
        }
        
        val response = apiService.chatCompletion(
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            userMessage = transcript
        )
        
        parseSummaryResponse(response)
    }
}
```

---

## 8. Pembuatan PDF

### 8.1 Struktur Dokumen PDF

```
┌──────────────────────────────────────┐
│          NOTULENSI RAPAT             │  ← Judul
│  Tanggal: 18 Nov 2026               │
│  Durasi: 32 menit                    │
│  Sumber: Rekaman_Rapat_Sprint.m4a   │
├──────────────────────────────────────┤
│                                      │
│  ■ RINGKASAN RAPAT                   │  ← Bagian B: Notulensi
│  1. xxxxxxxxxxxxxxxxxxxxxxx          │
│  2. xxxxxxxxxxxxxxxxxxxxxxx          │
│  3. xxxxxxxxxxxxxxxxxxxxxxx          │
│                                      │
│  ■ KEPUTUSAN                         │
│  1. xxxxxxxxxxxxxxxxxxxxxxx          │
│  2. xxxxxxxxxxxxxxxxxxxxxxx          │
│                                      │
│  ■ DAFTAR TUGAS                      │
│  ┌──────┬──────┬────────┐           │
│  │Tugas │ PIC  │Tenggat │           │
│  ├──────┼──────┼────────┤           │
│  │ ...  │ ...  │  ...   │           │
│  └──────┴──────┴────────┘           │
│                                      │
├──────────────────────────────────────┤
│                                      │
│  ■ TRANSKRIP ASLI LENGKAP            │  ← Bagian A: Transkrip Verbatim
│                                      │
│  [00:00] xxxxxxxxxxxxxxxxxxxxxxxx    │
│  [00:15] xxxxxxxxxxxxxxxxxxxxxxxx    │
│  [00:32] xxxxxxxxxxxxxxxxxxxxxxxx    │
│  [01:05] xxxxxxxxxxxxxxxxxxxxxxxx    │
│  ...                                 │
│                                      │
├──────────────────────────────────────┤
│  Dibuat oleh Catet AI · Halaman 1/3 │  ← Footer
└──────────────────────────────────────┘
```

### 8.2 Implementasi (Android PdfDocument API)

```kotlin
/**
 * Membuat berkas PDF dari data notulensi dan transkrip.
 * Menggunakan android.graphics.pdf.PdfDocument (API bawaan, tanpa pustaka tambahan).
 */
class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Ukuran halaman A4 dalam PostScript points (1 point = 1/72 inch)
        private const val PAGE_WIDTH = 595   // 210mm
        private const val PAGE_HEIGHT = 842  // 297mm
        private const val MARGIN = 48        // ~17mm
    }
    
    suspend fun generate(
        recording: Recording,
        segments: List<TranscriptSegment>,
        summary: Summary,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            var pageNumber = 1
            var currentPage = startNewPage(document, pageNumber++)
            var yPosition = MARGIN.toFloat()
            
            // Header
            yPosition = drawHeader(currentPage.canvas, recording, yPosition)
            
            // Ringkasan
            yPosition = drawSection(currentPage.canvas, "RINGKASAN RAPAT",
                summary.summaryText, yPosition)
            
            // Keputusan
            if (summary.decisions.isNotEmpty()) {
                yPosition = drawSection(currentPage.canvas, "KEPUTUSAN",
                    summary.decisions.joinToString("\n"), yPosition)
            }
            
            // Daftar Tugas (tabel)
            if (summary.actionItems.isNotEmpty()) {
                yPosition = drawActionItemsTable(
                    currentPage.canvas, summary.actionItems, yPosition)
            }
            
            // Transkrip Asli Lengkap
            yPosition = drawSectionTitle(currentPage.canvas,
                "TRANSKRIP ASLI LENGKAP", yPosition)
            
            for (segment in segments) {
                if (yPosition > PAGE_HEIGHT - MARGIN * 2) {
                    document.finishPage(currentPage)
                    currentPage = startNewPage(document, pageNumber++)
                    yPosition = MARGIN.toFloat()
                }
                yPosition = drawTranscriptLine(
                    currentPage.canvas, segment, yPosition)
            }
            
            document.finishPage(currentPage)
            document.writeTo(outputFile.outputStream())
            document.close()
            
            outputFile
        }
    }
}
```

**Penjelasan `PdfDocument` API:**
- API bawaan Android sejak API 19 — tidak memerlukan pustaka pihak ketiga.
- Bekerja menggunakan `Canvas` yang sama seperti menggambar UI Android biasa.
- Keterbatasan: tidak mendukung *hyperlink* atau tabel kompleks secara bawaan (harus digambar manual). Untuk MVP, ini cukup.

---

## 9. Pengelolaan Model

### 9.1 Alur Unduh Model

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Cek apakah   │    │ Tampilkan    │    │ Unduh dari   │    │ Validasi     │
│ model sudah  │───▶│ dialog       │───▶│ Hugging Face │───▶│ SHA-256      │
│ ada di lokal │    │ konfirmasi   │    │ (~574 MB)    │    │ integritas   │
│              │    │ (ukuran)     │    │ + resume     │    │ berkas       │
└──────┬───────┘    └──────────────┘    └──────┬───────┘    └──────┬───────┘
       │                                       │                   │
   Ada ▼                                       ▼                   ▼
 ┌─────────┐                           ┌──────────────┐    ┌──────────────┐
 │ Langsung│                           │ Bilah        │    │ Model siap   │
 │ pakai   │                           │ kemajuan     │    │ digunakan    │
 └─────────┘                           │ unduhan      │    └──────────────┘
                                       └──────────────┘
```

### 9.2 Penyimpanan Model

```
/data/data/com.catetai.app/files/
├── models/
│   ├── whisper-large-v3-turbo-q5.gguf    # Model Whisper (~574 MB)
│   └── whisper-large-v3-turbo-q5.sha256  # Hash untuk validasi
├── audio/
│   ├── audio_1700000000000.enc           # Audio terenkripsi
│   └── audio_1700000060000.enc
└── temp/
    └── decode_buffer.pcm                  # Berkas PCM sementara (dihapus setelah selesai)
```

---

## 10. Keamanan & Enkripsi

### 10.1 Lapisan Enkripsi

| Lapisan | Apa yang Dilindungi | Algoritma | Pustaka | Kapan Diterapkan |
| :--- | :--- | :--- | :--- | :--- |
| **Basis Data** | Seluruh tabel Room (rekaman, transkrip, ringkasan) | AES-256-CBC | SQLCipher | Saat Room dibuka (transparan) |
| **Berkas Audio** | Berkas audio hasil impor | AES-256-GCM (StreamingAead) | Google Tink | Segera setelah impor berhasil |
| **Kunci API** | Kunci BYOK pengguna | RSA / AES (Keystore) | Android Keystore | Saat pengguna memasukkan kunci |
| **Berkas PCM Sementara** | Audio yang sudah didekode (format PCM) | Tidak dienkripsi | — | Dihapus segera setelah transkripsi selesai |

### 10.2 Alur Enkripsi Audio Impor

```
Berkas dari SAF ──salin──▶ /temp/import_buffer ──enkripsi──▶ /audio/xxx.enc
                           (tidak terenkripsi)   Tink AEAD   (terenkripsi)
                           (dihapus segera)                  (permanen)
```

**Penjelasan:** Berkas PCM sementara (`decode_buffer.pcm`) **tidak dienkripsi** karena:
1. Berkas ini hanya ada selama proses transkripsi berlangsung (beberapa menit).
2. Mengenkripsi/mendekripsi berkas PCM besar akan memperlambat proses.
3. Berkas ini **dihapus secara permanen** segera setelah transkripsi selesai.
4. Berkas tetap berada di *sandbox* aplikasi (`/data/data/com.catetai/`) yang dilindungi oleh izin Linux.

---

## 11. Strategi Pengujian

### 11.1 Piramida Pengujian

| Tingkat | Apa yang Diuji | Alat | Target Jumlah |
| :--- | :--- | :--- | :--- |
| **Unit Test** | Use case, repository, perangkum, utilitas, ViewModel | JUnit 5 + MockK + Turbine | ≥ 80% cakupan kode |
| **Integration Test** | Room DAO + SQLCipher, dekoder audio, JNI bindings | AndroidJUnit (instrumen) | Setiap DAO dan dekoder |
| **UI Test** | Alur utama: impor → proses → lihat hasil → ekspor | Compose UI Test | 3 alur inti |
| **Manual Test** | Perangkat fisik (TECNO CN5), performa, termal | Checklist manual | Setiap rilis |

### 11.2 Tes yang Sudah Ada (Dipertahankan)

Dari kode sumber saat ini, **101 tes unit JVM** yang masih relevan:

| Kelas Tes | Jumlah | Relevansi v2 |
| :--- | :--- | :--- |
| RecordingStatusConverterTest | 10 | ✅ Tetap (perlu tambah status baru: IMPORTED, DECODING, dsb.) |
| RecordingMapperTest | 3 | ✅ Tetap (perlu sesuaikan mapper) |
| RecordingRepositoryImplTest | 8 | ✅ Tetap (perlu sesuaikan untuk impor) |
| HomeViewModelTest | 4 | ✅ Tetap |
| RecordingFormattersTest | 6 | ✅ Tetap |
| WcagContrastTest | 12 | ✅ Tetap |
| AudioConfigTest | 17 | ⚠️ Sebagian tidak relevan (milik fitur perekam) |
| AudioAmplitudeUtilTest | 5 | ❌ Tidak relevan (fitur perekam) |
| AudioRecorderManagerTest | 5 | ❌ Tidak relevan (fitur perekam) |
| PcmBufferWriterTest | 6 | ❌ Tidak relevan (fitur perekam) |
| WavHeaderUtilTest | 9 | ❌ Tidak relevan (fitur perekam) |
| RecordingNotificationHelperTest | 10 | ❌ Tidak relevan (fitur perekam, ForegroundService) |
| RecordingThresholdEvaluatorTest | 6 | ❌ Tidak relevan (fitur perekam) |

**Rencana:** Tes yang tidak relevan **tidak dihapus** dari kode sumber — hanya dipindahkan ke *source set* terpisah (`src/test-legacy/`) agar tidak mengganggu *build* utama tetapi tetap tersedia jika fitur perekam ditambahkan di v1.1.

---

## 12. Konvensi Proyek

### 12.1 Git

| Aspek | Konvensi |
| :--- | :--- |
| **Commit** | Conventional Commits: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `perf:` |
| **Branch** | `feature/<nama>`, `fix/<nama>`, `docs/<nama>`, `chore/<nama>` |
| **Merge** | Fast-forward only ke `main`. Riwayat linier. |
| **Pesan** | Bahasa Inggris untuk pesan commit. Bahasa Indonesia untuk dokumentasi. |

### 12.2 Kode

| Aspek | Konvensi |
| :--- | :--- |
| **Bahasa** | Kotlin (semua kode baru). C++ hanya untuk ikatan JNI. |
| **Formatter** | ktlint (aturan default) |
| **Linter** | Android Lint (0 error wajib, warning ditinjau per sprint) |
| **Dokumentasi kode** | KDoc untuk semua kelas dan fungsi publik |
| **Penamaan** | `camelCase` untuk variabel/fungsi, `PascalCase` untuk kelas, `SCREAMING_SNAKE` untuk konstanta |

### 12.3 Definition of Done (DoD)

Sebuah fitur dianggap **selesai** jika dan hanya jika:

1. ✅ Kode ter-*compile* tanpa error (`assembleDebug` berhasil)
2. ✅ 0 error lint (`lintDebug` bersih)
3. ✅ Semua tes unit lulus (`testDebugUnitTest` 0 failure)
4. ✅ Tes instrumen lulus (jika ada) (`connectedDebugAndroidTest` 0 failure)
5. ✅ Kode sudah di-*review* (self-review minimum)
6. ✅ Commit mengikuti Conventional Commits
7. ✅ Dokumentasi KDoc untuk API publik
8. ✅ Diuji di perangkat fisik (TECNO CN5) untuk fitur yang melibatkan perangkat keras

---

> **Status Dokumen:** DRAFT v2.0.0
> **Langkah Selanjutnya:** Review → Approval → Lanjut ke `03_SPRINT_ROADMAP.md`
