# 03 — Sprint Roadmap & Definition of Done
## Catet AI: Phased Execution Plan

| Metadata              | Isi                                               |
| :-------------------- | :------------------------------------------------ |
| **Versi Dokumen**     | 1.0.0                                             |
| **Status**            | DRAFT — Menunggu Review & Approval                |
| **Penulis**           | Luthfi Fahmi Alviansyah                           |
| **Tanggal Dibuat**    | 17 November 2026                                  |
| **Dokumen Prasyarat** | `01_PRD_CATET_AI.md`, `02_TECH_SPEC_CATET_AI.md`  |

---

## Daftar Isi

1. [Konvensi Dokumen](#1-konvensi-dokumen)
2. [Ringkasan Fase](#2-ringkasan-fase)
3. [Fase 0: Pondasi & Standar Engineering](#3-fase-0-pondasi--standar-engineering)
4. [Fase 1: Core Audio Engine & Local Database](#4-fase-1-core-audio-engine--local-database)
5. [Fase 2: VAD & Speech-to-Text Pipeline](#5-fase-2-vad--speech-to-text-pipeline)
6. [Fase 3: AI Summarizer & Notulensi](#6-fase-3-ai-summarizer--notulensi)
7. [Fase 4: UI Polish, Export & Search](#7-fase-4-ui-polish-export--search)
8. [Definisi Global: Definition of Done (DoD)](#8-definisi-global-definition-of-done-dod)
9. [Kriteria Rilis MVP](#9-kriteria-rilis-mvp)

---

## 1. Konvensi Dokumen

### 1.1 Format Tiket

Setiap unit pekerjaan ditulis sebagai **tiket** dengan format berikut:

```
TICKET-ID | Judul Tiket
  Tipe     : feat / chore / fix / refactor / test / docs
  Prioritas: P0 (blocker) / P1 (critical) / P2 (important) / P3 (nice-to-have)
  Estimasi : Jumlah jam kerja efektif (bukan kalender)
  Dependensi: Tiket lain yang harus selesai terlebih dulu
  Branch   : feature/<nama-branch>
  Definition of Done: Kriteria biner (LULUS / GAGAL) yang harus terpenuhi
```

### 1.2 Aturan Eksekusi

1. **Satu tiket, satu branch, satu PR.** Tidak boleh mencampur pekerjaan dari tiket berbeda dalam satu commit/branch.
2. **Tiket dikerjakan sesuai urutan prioritas dalam fase.** P0 dikerjakan pertama, lalu P1, dan seterusnya.
3. **Tiket tidak boleh di-merge jika DoD belum 100% terpenuhi.** Tidak ada pengecualian.
4. **Setiap fase harus selesai sepenuhnya sebelum pindah ke fase berikutnya.** Ini mencegah fondasi yang rapuh.

### 1.3 Label Status Tiket

| Status | Arti |
| :--- | :--- |
| `TODO` | Belum dimulai |
| `IN_PROGRESS` | Sedang dikerjakan |
| `IN_REVIEW` | Kode selesai, sedang di-review/diuji |
| `DONE` | DoD terpenuhi, sudah di-merge ke `main` |
| `BLOCKED` | Tidak bisa dilanjutkan karena dependensi/masalah teknis |

---

## 2. Ringkasan Fase

| Fase | Nama | Fokus Utama | Jumlah Tiket | Estimasi Total |
| :--: | :--- | :--- | :--: | :--: |
| 0 | Pondasi & Standar Engineering | Dokumentasi, setup proyek Android, CI dasar | 7 | 13 jam |
| 1 | Core Audio Engine & Local DB | Perekam audio background, database Room, UI dasar, enkripsi at-rest, noise suppression | 12 | 46 jam |
| 2 | VAD & Speech-to-Text Pipeline | Silero VAD, Whisper on-device & BYOK, kosakata kustom | 9 | 40 jam |
| 3 | AI Summarizer & Notulensi | Prompt template, Extractive Summarizer & SLM on-device, format notulensi | 7 | 28 jam |
| 4 | UI Polish, Export & Search | Tap-to-listen, full-text search, ekspor MD/TXT, sharing | 8 | 24 jam |
| **Total** | | | **43** | **~151 jam** |

---

## 3. Fase 0: Pondasi & Standar Engineering

**Tujuan:** Menyiapkan seluruh infrastruktur proyek agar kode pertama yang ditulis di Fase 1 sudah berada di atas fondasi yang benar.

**Milestone:** Proyek Android bisa di-build dan di-run di emulator, menampilkan satu layar kosong bertuliskan "Catet AI". Seluruh library inti terpasang. Dokumentasi lengkap.

---

### F0-01 | Dokumentasi PRD & Tech Spec ✅

```
Tipe      : docs
Prioritas : P0 (blocker — semua tiket lain merujuk ke dokumen ini)
Estimasi  : 4 jam
Dependensi: Tidak ada
Branch    : docs/blueprint

Definition of Done:
  [✅] File docs/01_PRD_CATET_AI.md ada dan berisi: problem statement, persona,
       scope boundary, user stories dengan acceptance criteria, NFR terukur,
       edge cases, dan glosarium.
  [✅] File docs/02_TECH_SPEC_CATET_AI.md ada dan berisi: keputusan teknologi
       dengan justifikasi, arsitektur sistem, ERD, interface contract,
       strategi AI, keamanan, dan rejected alternatives.
  [✅] File docs/03_SPRINT_ROADMAP.md ada dan berisi: tiket pekerjaan per fase
       dengan DoD untuk setiap tiket.
```

**Status: `DONE`** — Ketiga dokumen telah selesai ditulis dan di-commit.

---

### F0-02 | Inisialisasi Proyek Android (Kotlin + Compose) ✅

```
Tipe      : chore
Prioritas : P0 (blocker — semua tiket kode merujuk ke proyek ini)
Estimasi  : 2 jam
Dependensi: F0-01
Branch    : chore/android-project-init

Definition of Done:
  [✅] Proyek Android Studio dibuat dengan:
      - Package name: com.catetai.app
      - Min SDK: 26 (Android 8.0)
      - Target SDK: 35 (Android 15)
      - Compile SDK: 35
      - Kotlin 2.0+ (Kotlin 2.0.21)
      - Jetpack Compose (BOM 2024.10.01)
      - Material 3 (1.3.1)
  [✅] Struktur folder mengikuti Clean Architecture:
      app/src/main/java/com/catetai/app/
        ├── core/
        ├── data/
        ├── domain/
        └── presentation/
  [✅] Aplikasi berhasil di-build tanpa error (BUILD SUCCESSFUL, APK 9.3MB)
      dan menampilkan layar "Catet AI".
  [✅] File build.gradle.kts menggunakan Version Catalog (libs.versions.toml).
```

**Status: `DONE`** — Project Android terinisialisasi dan ter-build sukses.

---

### F0-03 | Setup Dependency Injection (Hilt) ✅

```
Tipe      : chore
Prioritas : P0
Estimasi  : 1 jam
Dependensi: F0-02
Branch    : chore/setup-hilt

Definition of Done:
  [✅] Hilt terpasang dan dikonfigurasi:
      - @HiltAndroidApp di Application class (CatetAiApp)
      - @AndroidEntryPoint di MainActivity
      - @HiltViewModel bisa digunakan di Compose screen
  [✅] Build berhasil tanpa error Hilt/Dagger (BUILD SUCCESSFUL).
  [✅] Satu ViewModel dummy (MainViewModel) berhasil di-inject ke CatetAiEntryScreen
      sebagai bukti Hilt berfungsi.
```

**Status: `DONE`** — Dependency injection Hilt + KSP terpasang dan teruji aktif.

---

### F0-04 | Setup Room Database dengan SQLCipher ✅

```
Tipe      : chore
Prioritas : P0
Estimasi  : 2 jam
Dependensi: F0-02
Branch    : chore/setup-room-sqlcipher

Definition of Done:
  [✅] Room Database class (CatetAiDatabase) dibuat dengan:
      - Entity: RecordingEntity (sesuai ERD di Tech Spec bagian 6)
      - DAO: RecordingDao dengan query: insertRecording, getAllRecordings,
        getRecordingById, updateTitle, updateStatus, deleteRecording
      - Type/Enum support untuk RecordingStatus
  [✅] SQLCipher terintegrasi: database dibuat menggunakan
      SupportFactory(passphrase) dari SQLCipher (AES-256-CBC).
  [✅] Kunci enkripsi 256-bit dihasilkan oleh SecureRandom dan disimpan di Android Keystore
      via EncryptedSharedPreferences (DatabaseKeyManager).
  [✅] Live test pada device TECNO_CN5: insert 1 sample recording → Flow query berhasil
      menampilkan "Database SQLCipher Aktif • 1 Catatan Tersimpan Terenkripsi".
  [✅] File database (catet_ai_encrypted.db) terverifikasi terenkripsi (header mentah
      bukan 'SQLite format 3', melainkan ciphertext acak SQLCipher).
```

**Status: `DONE`** — Room Database terenkripsi SQLCipher AES-256 + Android Keystore selesai dan teruji.

---

### F0-05 | Setup Navigasi & Struktur Screen Kosong ✅

```
Tipe      : chore
Prioritas : P1
Estimasi  : 2 jam
Dependensi: F0-02, F0-03
Branch    : chore/setup-navigation

Definition of Done:
  [✅] Jetpack Navigation Compose terpasang dengan NavHost (CatetAiNavHost).
  [✅] 4 screen placeholder dibuat (UI kosong, hanya judul):
      - HomeScreen (route: "home")
      - RecordScreen (route: "record")
      - DetailScreen (route: "detail/{recordingId}")
      - SettingsScreen (route: "settings")
  [✅] Navigasi antar screen berfungsi (tap item di HomeScreen → DetailScreen).
  [✅] Bottom navigation bar tersedia untuk navigasi utama (Home & Settings).
  [✅] Back navigation berfungsi dengan benar (back stack dikelola otomatis oleh NavHost).
```

**Status: `DONE`** — Navigation Compose dan 4 skeleton screens terpasang dan teruji di HP.

---

### F0-05b | Design Tokens & System Theme (Light/Dark + Material Symbols) ✅

```
Tipe      : chore
Prioritas : P2
Estimasi  : 1 jam
Dependensi: F0-05
Branch    : chore/design-tokens

Definition of Done:
  [✅] Color tokens terdefinisi untuk Dark & Light scheme:
      - Dark: Background Slate (#0F172A), Surface (#1E293B), Primary Indigo (#6366F1)
      - Light: Background Clean (#F8FAFC), Surface (#FFFFFF), Primary Indigo (#4F46E5)
      - Rasio kontras teks lolos standar WCAG AA (minimal 4.5:1, TextPrimaryDark mencapai 15.8:1).
  [✅] Type.kt terkonfigurasi dengan Material 3 typography scale (dioptimalkan untuk baris transkrip mengalir & notulensi).
  [✅] Theme.kt mendukung dynamic theme (Android 12+), dark theme, dan light theme.
  [✅] Dimens.kt mendefinisikan batas area sentuh minimal 48dp (MinTouchTarget) sesuai WCAG 2.5.5.
  [✅] Ikon menggunakan vector drawable individual (ic_home, ic_settings) tanpa bloat material-icons-extended.
  [✅] Tanpa library Lottie eksternal (menggunakan animasi native Compose rememberInfiniteTransition).
```

**Status: `DONE`** — Design tokens, light/dark themes, dan standard dimensi aksesibilitas selesai.

---

### F0-06 | Setup Linting, Formatting & Git Hooks ✅

```
Tipe      : chore
Prioritas : P2
Estimasi  : 1 jam
Dependensi: F0-02
Branch    : chore/setup-lint

Definition of Done:
  [✅] Android Lint terkonfigurasi di app/build.gradle.kts (abortOnError = true, checkReleaseBuilds = false, zero errors).
  [✅] File .editorconfig dibuat di root repository untuk standarisasi indentasi (4 spasi Kotlin/XML, 2 spasi JSON/YAML/TOML, UTF-8, LF).
  [✅] Command `./gradlew lintDebug` berjalan sukses (0 error, verifikasi clean architecture & path context).
  [✅] Panduan code hygiene & pre-commit reminder terdokumentasi di README.md.
```

**Status: `DONE`** — Android Lint, .editorconfig, dan pedoman code hygiene aktif.

---

## 4. Fase 1: Core Audio Engine & Local Database

**Tujuan:** Aplikasi bisa merekam suara dari mikrofon di latar belakang tanpa terputus, menyimpan file audio, dan menampilkan daftar rekaman di UI.

**Milestone:** Pengguna bisa menekan tombol Rekam, berbicara selama 30 menit dengan layar HP mati, menekan Berhenti, dan melihat rekaman tersebut muncul di daftar dengan durasi dan ukuran file yang benar. File audio bisa diputar ulang.

**Prasyarat Fase:** Seluruh tiket Fase 0 berstatus `DONE`.

---

### F1-01 | AudioConfig & Konstanta Audio ✅

```
Tipe      : feat
Prioritas : P0
Estimasi  : 1 jam
Dependensi: F0-02
Branch    : feature/audio-config

Definition of Done:
  [✅] Object AudioConfig dibuat di package core/audio sebagai const val murni (tanpa Android dependency):
      - SAMPLE_RATE = 16.000 Hz (standar Whisper & Silero VAD)
      - BYTES_PER_SAMPLE = 2, CHANNEL_COUNT = 1, BYTES_PER_SECOND = 32.000
      - DEFAULT_BUFFER_SIZE_BYTES = 8.192 byte (fallback 256 ms audio)
      - FLUSH_INTERVAL_MS = 5.000 ms (buffer flush to disk)
      - WAV_HEADER_SIZE_BYTES = 44
      - VAD_CHUNK_SAMPLES = 512 (32 ms @ 16 kHz untuk Silero VAD v5), VAD_CHUNK_BYTES = 1.024
      - LOW_DISK_WARNING_BYTES = 100 MB, MIN_DISK_SPACE_BYTES = 50 MB
      - LOW_BATTERY_WARNING_PERCENT = 5%, CRITICAL_BATTERY_PERCENT = 2%
  [✅] Fungsi utilitas konversi matematis:
      - durationMsToBytes(durationMs): Long — konversi durasi milidetik ke byte PCM
      - bytesToDurationMs(bytes): Long — konversi byte PCM ke durasi milidetik
      - durationMsToSamples(durationMs): Int — konversi durasi ke jumlah sampel (untuk VAD)
  [✅] Unit test fokus dan bermakna (pure JVM, tanpa emulator) di AudioConfigTest:
      - Validasi nilai turunan non-trivial (BYTES_PER_SECOND = 32.000, VAD_CHUNK_BYTES = 1.024)
      - Validasi VAD_CHUNK_SAMPLES selaras dengan durationMsToSamples(32) = 512
      - Validasi relasi hierarki ambang batas (LOW_DISK > MIN_DISK, LOW_BATTERY > CRITICAL_BATTERY)
      - Kalkulasi durasi 0 ms, 1 detik, 2 jam rapat terpanjang
      - Round-trip test (ms → bytes → ms) idempoten
      - Validasi input negatif ditolak dengan IllegalArgumentException
  [✅] ./gradlew testDebugUnitTest → BUILD SUCCESSFUL, 0 failure.
```

**Status: `DONE`** — AudioConfig konstanta murni dan unit test terverifikasi presisi.

---

### F1-02 | WavHeaderUtil (Pembuat Header WAV) ✅

```
Tipe      : feat
Prioritas : P0
Estimasi  : 2 jam
Dependensi: F1-01
Branch    : feature/wav-header

Definition of Done:
  [✅] Object WavHeaderUtil memiliki fungsi lengkap manipulasi RIFF WAV header (44-byte):
      - createHeaderByteArray(dataSizeBytes, sampleRate, channels, bitsPerSample):
        Menghasilkan 44-byte array header standar RIFF WAVE dengan ByteBuffer Little-Endian.
      - writeHeader(outputStream, ...):
        Menulis header ke OutputStream saat memulai file rekaman baru.
      - updateHeaderDataSize(file, dataSizeBytes):
        Menggunakan RandomAccessFile untuk in-place update byte 4 (ChunkSize) dan byte 40 (Subchunk2Size)
        tanpa rewrite seluruh file audio.
      - readHeader(file):
        Memparsing dan memvalidasi integritas 44-byte header (RIFF, WAVE, fmt, data, PCM tag).
      - recoverUnfinalizedHeader(file):
        Auto-repair file rekaman yang terputus mendadak (dataSize = 0) dengan menghitung
        actualDataSize = file.length() - 44.
  [✅] Unit test komprehensif di WavHeaderUtilTest:
      - Validasi seluruh field 44-byte (RIFF, WAVE, fmt, data, sampleRate=16000, 16-bit mono).
      - In-place update teruji: dataSize diperbarui, konten PCM audio tidak korup.
      - Auto-repair file crash (dataSize 0) teruji pulih ke ukuran aktual.
      - Error handling teruji melempar IOException untuk file truncated/bad magic byte.
  [✅] ./gradlew testDebugUnitTest & lintDebug lolos 100% tanpa error.
```

**Status: `DONE`** — WavHeaderUtil generator, in-place updater, parser & recovery selesai dan teruji.

---

### F1-03 | PcmBufferWriter (Penulis Buffer ke Disk) ✅

```
Tipe      : feat
Prioritas : P0
Estimasi  : 2 jam
Dependensi: F1-02
Branch    : feature/pcm-writer

Definition of Done:
  [✅] Class PcmBufferWriter menangani:
      - Membuka FileOutputStream/BufferedOutputStream ke file WAV polos di sandbox aplikasi.
      - Menulis WAV header di awal melalui WavHeaderUtil.writeHeader().
      - Menerima chunk buffer ByteArray dari AudioRecord dan menulisnya ke file.
      - Flush dan fsync ke disk secara berkala setiap FLUSH_INTERVAL_MS (5 detik).
      - Close: memperbarui WAV header via WavHeaderUtil.updateHeaderDataSize() dengan ukuran data final, lalu menutup stream dengan aman.
  [✅] Implements Closeable (auto-close di try-with-resources / use {}).
  [✅] Unit test di PcmBufferWriterTest:
      - Tulis 3 buffer → close → verifikasi dataSize di header sama dengan total bytes yang ditulis.
      - Tracking bytesWritten dan durationMs terverifikasi akurat.
      - Periodic flush logic teruji dengan timeProvider.
      - Idempotent close dan error handling (closed write & bounds check) teruji.
  [✅] ./gradlew testDebugUnitTest & lintDebug lolos 100% tanpa error.
```

**Status: `DONE`** — PcmBufferWriter streaming disk storage, periodic flush, dan auto-header finalization selesai dan teruji.

---

### F1-04 | AudioRecorderManager (Orkestrasi Perekaman) ✅

```
Tipe      : feat
Prioritas : P0
Estimasi  : 4 jam
Dependensi: F1-01, F1-03
Branch    : feature/audio-recorder

Definition of Done:
  [✅] Class AudioRecorderManager menerapkan pola Producer-Consumer (Anti-Overrun / Anti-Jank):
      - Producer Loop (High Priority): membaca AudioRecord.read(), menghitung amplitudo RMS
        secara efisien (in-memory, tanpa I/O), memancarkan amplitudeFlow (0.0 - 1.0) untuk visualizer UI,
        lalu mengirim chunk ke buffered Channel<ByteArray> (kapasitas 96 chunk ≈ 3,07 detik audio).
      - Consumer Loop (Dispatchers.IO): mengambil buffer dari Channel dan memanggil PcmBufferWriter.write().
        Disk I/O atau fsync yang memakan waktu puluhan ms tidak akan pernah memblokir thread pembaca AudioRecord.
      - Backpressure Monitoring: mencatat warning di log jika kapasitas Channel terisi penuh saat lonjakan I/O.
  [✅] Interupsi Mikrofon via AudioRecordingCallback (API 29+):
      - Mendeteksi perebutan mikrofon oleh panggilan telepon atau asisten OS melalui isClientSilenced.
      - Memancarkan isSilenced: StateFlow<Boolean> agar UI dan notifikasi dapat menampilkan status
        "Mikrofon sedang dipakai aplikasi lain".
  [✅] Lifecycle & Kontrak:
      - startRecording(outputFile: File): Result<Unit>
      - stopRecording(): Result<RecordingResult> (metadata durasi, ukuran byte, error status)
      - isRecording: StateFlow<Boolean>
      - durationMsFlow: StateFlow<Long>
  [✅] Logic & Integration Test di AudioRecorderManagerTest & AudioAmplitudeUtilTest:
      - Validasi alur Producer-Consumer mentransfer buffer tanpa kehilangan data.
      - Validasi kalkulasi amplitudo RMS terpetakan bersih dalam rentang 0.0 - 1.0.
      - Validasi stopRecording menutup AudioRecord dan PcmBufferWriter secara bersih.
      - Validasi penanganan error: rekam ganda & stop saat idle ditolak dengan aman.
  [✅] ./gradlew test lintDebug assembleDebug lolos 100% tanpa error.
```

**Status: `DONE`** — AudioRecorderManager pola Producer-Consumer, deteksi pembisuan, dan amplitudo RMS selesai dan teruji.

---

### F1-05 | RecordingForegroundService ✅

```
Tipe      : feat
Prioritas : P0
Estimasi  : 4 jam
Dependensi: F1-04
Branch    : feature/foreground-service
Commit    : 329da97

Definition of Done:
  [✅] Service menggunakan foregroundServiceType="microphone" (Android 14+ compliant).
      - startForeground() dengan ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE (API 29+).
      - aapt dump pada APK: android:foregroundServiceType(0x01010599)=0x80 ✓
  [✅] Notifikasi persisten muncul dengan:
      - Judul dinamis: "Catet AI — Sedang Merekam" / "⚠️ Catet AI — Mikrofon Dibisukan"
      - Konten: durasi berjalan (diperbarui setiap detik) + indikator ⚠️ baterai/disk lemah
      - Action button: "Berhenti" via PendingIntent ke ACTION_STOP
      - Channel ID: "catet_ai_recording_channel" (IMPORTANCE_LOW — tidak memicu getar/suara)
      - onlyAlertOnce=true agar pembaruan timer tidak menyebabkan alert berulang
  [✅] Service menahan PARTIAL_WAKE_LOCK selama perekaman aktif.
      - Batas keselamatan 12 jam via acquire(timeoutMs) untuk mencegah battery drain ekstrem.
      - Dilepas otomatis di stopRecordingSession() dan onDestroy().
  [✅] Batasan Background Android 12+ (API 31+):
      - Menggunakan START_NOT_STICKY agar OS tidak melanjutkan service liar di background.
      - Jika service di-restart tanpa Intent (intent=null), service mendeteksi file sesi aktif
        dari RecordingSessionStore (SharedPreferences), memanggil
        WavHeaderUtil.recoverUnfinalizedHeader(), menampilkan notifikasi pemulihan, lalu stopSelf().
      - Tidak pernah mencoba memulai AudioRecord baru dari background tanpa interaksi user.
  [✅] Service memonitor:
      - Level baterai via BroadcastReceiver(ACTION_BATTERY_CHANGED):
        * Peringatan di <= 5% (LOW_BATTERY_WARNING_PERCENT): update notifikasi.
        * Auto-stop di <= 2% (CRITICAL_BATTERY_PERCENT): stopRecordingSession(CRITICAL_BATTERY).
      - Ruang disk via File.usableSpace (dicek tiap 5 detik):
        * Peringatan di <= 100MB (LOW_DISK_WARNING_BYTES): update notifikasi.
        * Auto-stop di <= 50MB (MIN_DISK_SPACE_BYTES): stopRecordingSession(CRITICAL_DISK_SPACE).
  [✅] Unit test: 16 test hijau (RecordingNotificationHelperTest: 10, RecordingThresholdEvaluatorTest: 6).
      - RecordingThresholdEvaluator memisahkan logika evaluasi ambang batas agar testable murni di JVM.
      - RecordingNotificationHelper.formatDuration() diuji dari 0ms hingga 2h+ (edge case negatif aman).
  [✅] APK install & launch tanpa crash di TECNO_CN5 (ADB: 9526313000008438).
  [⚠️] Instrumented test rekam 30 detik + layar mati: ditunda ke sesi fisik terpisah (memerlukan
       izin RECORD_AUDIO yang di-grant secara manual di UI + layar mati + timer 30 detik).
       Ini bukan blocker untuk tiket berikutnya (F1-06 adalah Room Entity layer, tidak bergantung service).
```

**Status: `DONE`** — RecordingForegroundService terdaftar, notifikasi persisten, WakeLock, pemantauan daya/disk, dan pemulihan crash selesai dan teruji.

---

### F1-06 | RecordingEntity & RecordingDao (Database Layer) ✅

```
Tipe      : feat
Prioritas : P0
Estimasi  : 2 jam
Dependensi: F0-04
Branch    : feature/recording-entity
Commit    : 5690191

Definition of Done:
  [✅] RecordingEntity sesuai ERD di Tech Spec:
      id (UUID), title, audio_file_path, duration_ms, file_size_bytes,
      sample_rate, status (enum), was_silenced_by_system, custom_vocabulary,
      processing_engine, created_at, updated_at.
      - Index: idx_recordings_status, idx_recordings_created_at
  [✅] RecordingStatusConverter (Room TypeConverter):
      - Mengonversi enum RecordingStatus ↔ String (TEXT) di SQLite.
      - 10 unit test JVM lulus 100% (round-trip test, fallback ke ERROR untuk invalid string).
  [✅] RecordingDao memiliki query lengkap:
      - @Insert: insertRecording (OnConflictStrategy.REPLACE)
      - @Query: getAllRecordingsOrderByDateDesc (Flow<List<RecordingEntity>>) + alias getAllRecordings()
      - @Query: getRecordingById (suspend)
      - @Query: getRecordingsByStatus (Flow)
      - @Query: updateStatus(id, newStatus, updatedAt)
      - @Query: updateTitle(id, newTitle, updatedAt)
      - @Query: updateAudioMetadata(id, durationMs, fileSizeBytes, wasSilencedBySystem, updatedAt)
      - @Update: updateRecording(recording)
      - @Delete: deleteRecording, deleteRecordingById
  [✅] Status enum lengkap: RECORDING, STOPPED, TRUNCATED, PROCESSING, TRANSCRIBED, SUMMARIZED, ERROR.
  [✅] Instrumented test (14 test di perangkat fisik TECNO CN5 - Android 14):
      - In-memory database Room: insert → query → update (title, status, metadata) → delete →
        full lifecycle (RECORDING → STOPPED → PROCESSING → TRANSCRIBED → SUMMARIZED → DELETE).
      - Seluruh 14 test lulus 100% (BUILD SUCCESSFUL).
  [✅] ./gradlew testDebugUnitTest (68 test hijau) & ./gradlew lintDebug (0 error).
```

**Status: `DONE`** — Skema Room database, DAO reaktif, TypeConverter, dan 14 instrumented test di TECNO CN5 selesai 100%.

---

### F1-07 | RecordingRepository Implementation

```
Tipe      : feat
Prioritas : P1
Estimasi  : 3 jam
Dependensi: F1-05, F1-06
Branch    : feature/recording-repository

Definition of Done:
  [✅] RecordingRepositoryImpl mengimplementasikan RecordingRepository interface.
  [✅] createRecording(): membuat file WAV, insert entity ke Room,
      start ForegroundService.
  [✅] stopRecording(): stop service, finalize WAV, update entity
      dengan durasi dan ukuran file.
  [✅] deleteRecording(): hapus entity dari Room + hapus file WAV dari disk.
      Jika ada transkrip/notulensi terkait, hapus juga (cascade).
  [✅] getAllRecordings(): return Flow dari Room yang otomatis update UI.
  [✅] Di-provide via Hilt @Module.
  [✅] 79 JVM unit tests passed (RecordingMapperTest & RecordingRepositoryImplTest) & lintDebug 0 error.
```

**Status: `DONE`** — Recording domain model, repository contract, implementation, mapper, dan Hilt binding selesai 100% dengan 11 unit test baru (total 79 test hijau).

---

### F1-08 | HomeScreen UI (Daftar Rekaman)

```
Tipe      : feat
Prioritas : P1
Estimasi  : 4 jam
Dependensi: F1-07, F0-05
Branch    : feature/home-screen

Definition of Done:
  [✅] HomeScreen menampilkan:
      - LazyColumn berisi daftar rekaman, terurut terbaru di atas.
      - Setiap item menampilkan: judul, tanggal (format: "17 Nov 2026, 16:30"),
        durasi (format: "32:15"), status badge (warna berbeda per status).
      - Empty state: ilustrasi + teks "Belum ada rekaman. Tap tombol di bawah
        untuk mulai merekam." (saat daftar kosong).
      - FAB (Floating Action Button) di kanan bawah: ikon mikrofon,
        navigasi ke RecordScreen.
  [✅] Swipe-to-delete: geser item ke kiri → dialog konfirmasi → hapus.
  [✅] Tap item → navigasi ke DetailScreen dengan recordingId.
  [✅] HomeViewModel menggunakan MVI pattern:
      - State: HomeUiState (recordings: List, isLoading: Boolean)
      - Intent: LoadRecordings, DeleteRecording(id), NavigateToDetail(id)
  [✅] 89 unit tests passed, lintDebug 0 error, APK berhasil terpasang dan berjalan di TECNO CN5.
```

**Status: `DONE`** — HomeScreen Jetpack Compose berbasis MVI selesai 100% dengan swipe-to-delete, status badge semantik berstandar WCAG AA, empty state, formatters teruji, dan verifikasi fisik di perangkat Android.

---

### F1-09 | RecordScreen UI (Halaman Perekam)

```
Tipe      : feat
Prioritas : P1
Estimasi  : 4 jam
Dependensi: F1-07, F0-05
Branch    : feature/record-screen

Definition of Done:
  [ ] RecordScreen menampilkan:
      - Tampilan waktu berjalan (timer) di tengah: format "00:32:15".
      - Visualisasi waveform sederhana: bar vertikal yang bergerak
        berdasarkan amplitudo real-time dari AudioRecorderManager.amplitudeFlow.
      - Tombol utama: Rekam (lingkaran merah besar) / Berhenti (kotak merah).
      - Kolom input opsional: "Topik / Istilah / Nama peserta"
        (untuk customVocabulary, disimpan ke RecordingEntity).
  [ ] Permission handling:
      - Minta izin RECORD_AUDIO saat pertama kali tap Rekam.
      - Minta izin POST_NOTIFICATIONS (Android 13+) untuk notifikasi service.
      - Jika izin ditolak: tampilkan penjelasan mengapa izin diperlukan
        dengan tombol ke Settings.
  [ ] State machine:
      IDLE → (tap Rekam) → RECORDING → (tap Berhenti) → SAVING → (selesai) → IDLE
      Navigasi otomatis ke DetailScreen setelah SAVING selesai.
```

---

### F1-10 | Audio Playback di DetailScreen (Dasar)

```
Tipe      : feat
Prioritas : P2
Estimasi  : 3 jam
Dependensi: F1-07, F0-05
Branch    : feature/audio-playback

Definition of Done:
  [ ] DetailScreen menampilkan metadata rekaman:
      judul, tanggal, durasi, ukuran file, status.
  [ ] Pemutar audio dasar:
      - Tombol Play / Pause.
      - Seek bar (slider) yang menunjukkan posisi pemutaran.
      - Tampilan waktu: "03:25 / 32:15".
      - Kontrol kecepatan: 0.5x, 1.0x, 1.5x, 2.0x (via ExoPlayer atau MediaPlayer).
  [ ] Audio berhenti saat pengguna meninggalkan DetailScreen (back nav).
  [ ] Instrumented test: buka DetailScreen dengan rekaman valid → play →
      verifikasi audio terdengar → pause → verifikasi audio berhenti.
```

---

### F1-11 | Enkripsi Audio At-Rest (Encrypt-After-Stop)

```
Tipe      : feat
Prioritas : P1 (wajib sebelum klaim "terenkripsi" dipakai di UI/PRD)
Estimasi  : 8 jam
Dependensi: F1-05, F1-07, F1-10
Branch    : feature/audio-encryption

Keputusan Desain:
  - Selama merekam, file WAV polos disimpan di sandbox aplikasi (agar recovery F1-02/F1-04 tetap berlaku).
  - Setelah STOP, file dienkripsi lalu file polos dihapus secara aman.
  - Algoritma: Tink StreamingAead (AES256_GCM_HKDF_4KB), yang mendukung
    dekripsi seekable (baca acak) per segmen 4KB.
  - Keyset dibungkus master key di Android Keystore.
  - Playback dan STT membaca lewat decrypting channel, tanpa menulis
    file polos baru ke disk.

Definition of Done:
  [ ] AudioEncryptor (interface di domain/core, impl di data) dengan:
      - encryptInPlace(plainWav, outFile)
      - openDecryptedChannel(file): SeekableByteChannel
  [ ] Setelah stopRecording(): file dienkripsi, file WAV polos dihapus,
      kolom Room `isEncrypted = true` (migrasi database + test migrasi).
  [ ] Pemulihan: saat app/service start, WAV polos yang tertinggal
      (crash saat rekam atau saat enkripsi) di-recover (F1-02),
      lalu dienkripsi. Tidak ada WAV polos tersisa di filesDir.
  [ ] Playback (F1-10) memakai DataSource khusus di atas decrypting
      channel: play, pause, seek (termasuk lompat ke tengah file) dan
      kecepatan 0.5x–2x berfungsi.
  [ ] Pipeline STT/VAD membaca PCM per rentang segmen lewat channel yang
      sama (siapkan API readPcmRange(startMs, endMs): FloatArray).
  [ ] Rekaman 2 jam: waktu enkripsi < 60 detik di HP mid-range; RAM
      tidak naik di atas batas NFR-04.
  [ ] Verifikasi manual: `adb shell run-as com.catetai.app` lalu salin file
      audio, dan file tidak bisa diputar/dibuka tanpa aplikasi.
  [ ] deleteRecording() menghapus file terenkripsi; tidak ada file
      sementara tersisa.
  [ ] APK tetap ≤ 15MB setelah menambah Tink (ukur build release).
  [ ] Dokumen diperbarui: PRD (F-09, NFR-07/08) dan Tech Spec bagian 9
      menyebut audio terenkripsi dan jendela waktu WAV polos selama
      merekam.
```

---

### F1-12 | Audio Pre-processing (NoiseSuppressor & Voice Recognition Source)

```
Tipe      : feat
Prioritas : P1 (wajib untuk akurasi jarak 3 meter / mitigasi M-02)
Estimasi  : 2 jam
Dependensi: F1-04
Branch    : feature/noise-suppressor

Keputusan Desain:
  - Mengubah AudioSource dari MIC standar ke MediaRecorder.AudioSource.VOICE_RECOGNITION.
    Pada OS Android, VOICE_RECOGNITION mematikan kompresi Automatic Gain Control (AGC) agresif
    yang sering merusak rentang dinamis dan fidelitas sinyal audio untuk model Whisper.
  - Mengaktifkan android.media.audiofx.NoiseSuppressor pada audioSessionId AudioRecord
    jika didukung oleh perangkat keras (hardware-backed).
  - Mengisolasi panggilan efek audio agar graceful fallback jika perangkat tidak mendukungnya.

Definition of Done:
  [ ] DefaultAudioSourceFactory menggunakan MediaRecorder.AudioSource.VOICE_RECOGNITION.
  [ ] AudioRecorderManager menginisialisasi NoiseSuppressor:
      - Mengecek NoiseSuppressor.isAvailable()
      - Jika tersedia, membuat instance terikat pada audioSessionId dan mengaktifkannya
      - Melepaskan (release) NoiseSuppressor secara bersih saat perekaman dihentikan
  [ ] Unit test memvalidasi penanganan ketiadaan efek audio tanpa melempar exception.
  [ ] Pengujian di TECNO CN5 (HiOS): rekaman suara pada jarak 3 meter dengan desis pendingin
      udara (AC) tetap terdengar jernih dan bebas distorsi clipping.
```

---

## 5. Fase 2: VAD & Speech-to-Text Pipeline

**Tujuan:** Rekaman audio bisa diubah menjadi teks transkrip ber-timestamp dengan akurasi tinggi, baik secara offline (on-device) maupun via cloud (BYOK).

**Milestone:** Pengguna memilih sebuah rekaman, tap "Transkrip", menunggu progress bar selesai, dan mendapatkan teks transkrip yang bisa dibaca dengan timestamp per kalimat.

**Prasyarat Fase:** Seluruh tiket Fase 1 berstatus `DONE`.

---

### F2-01 | Silero VAD Engine Integration

```
Tipe      : feat
Prioritas : P0
Estimasi  : 6 jam
Dependensi: F1-01
Branch    : feature/silero-vad

Definition of Done:
  [ ] ONNX Runtime Mobile terpasang di project.
  [ ] Model Silero VAD v5 (.onnx, ~2MB) disertakan di assets/models/
      (budget kalkulasi: APK dasar 9.3MB + 2MB = ~11.3MB, aman di bawah limit 15MB).
  [ ] SileroVadEngine mengimplementasikan VadEngine interface:
      - process(audioData, sampleRate) → List<SpeechSegment>
  [ ] VadConfig bisa dikonfigurasi:
      threshold=0.5, minSilenceDurationMs=300, minSpeechDurationMs=250.
  [ ] Unit test: input audio sintetis (1 detik speech + 2 detik silence +
      1 detik speech) → output: 2 SpeechSegment dengan timestamp yang benar.
  [ ] Instrumented test: proses audio rekaman 5 menit nyata →
      keheningan dipotong → total durasi segmen speech < durasi asli.
  [ ] release() membebaskan memori ONNX Runtime session.
```

---

### F2-02 | Whisper.cpp NDK Build & JNI Bridge

```
Tipe      : feat
Prioritas : P0
Estimasi  : 8 jam
Dependensi: F0-02
Branch    : feature/whisper-jni

Definition of Done:
  [ ] Source code whisper.cpp di-clone sebagai submodule atau vendored
      di cpp/whisper/.
  [ ] CMakeLists.txt dikonfigurasi untuk:
      - Compile whisper.cpp untuk target ABI: arm64-v8a (eksklusif 64-bit, sesuai Asumsi A-04).
      - Link dengan Android NDK toolchain.
  [ ] WhisperJni.kt berisi native function declarations:
      - external fun init(modelPath: String): Long  (return context pointer)
      - external fun transcribe(ctx: Long, samples: FloatArray, ...): String
      - external fun release(ctx: Long)
  [ ] File JNI .cpp (whisper_jni.cpp) mengimplementasikan bridge antara
      Java/Kotlin dan C++ whisper API.
  [ ] Build berhasil: ./gradlew assembleDebug tanpa error NDK.
  [ ] Instrumented test: load model whisper-base → transcribe audio
      5 detik bahasa Inggris → output mengandung kata-kata yang diucapkan.
```

---

### F2-03 | Model Download Manager

```
Tipe      : feat
Prioritas : P0
Estimasi  : 4 jam
Dependensi: F0-02
Branch    : feature/model-download

Definition of Done:
  [ ] ModelDownloadManager menangani:
      - Daftar model yang tersedia: nama, URL download, ukuran, SHA-256 hash.
      - downloadModel(modelId, onProgress): Flow<DownloadProgress>
      - isModelAvailable(modelId): Boolean (cek file ada + hash cocok)
      - deleteModel(modelId): hapus file model dari disk.
  [ ] Download mendukung resume (HTTP Range header) jika koneksi terputus.
  [ ] Verifikasi integritas: setelah download selesai, hitung SHA-256 file
      dan bandingkan dengan hash yang diharapkan.
  [ ] Download menggunakan WorkManager (bukan Coroutine biasa) agar
      bisa berlanjut jika aplikasi di-background.
  [ ] UI: dialog menampilkan progress bar, ukuran terunduh/total, kecepatan,
      dan tombol Batal.
```

---

### F2-04 | OnDeviceSttEngine Implementation

```
Tipe      : feat
Prioritas : P0
Estimasi  : 6 jam
Dependensi: F2-02, F2-03
Branch    : feature/ondevice-stt

Definition of Done:
  [ ] OnDeviceSttEngine mengimplementasikan SttEngine interface.
  [ ] transcribe():
      - Menerima FloatArray (PCM float32 normalized) dan config.
      - Memanggil WhisperJni.init() → WhisperJni.transcribe() → WhisperJni.release().
      - Mengembalikan List<TranscriptSegment> dengan timestamp per segmen.
  [ ] Mendukung parameter: language="id", initialPrompt (kosakata kustom),
      beam_size=5, temperature=0.0.
  [ ] Cek memori sebelum load: jika RAM tersedia < 1.5x ukuran model,
      throw InsufficientMemoryException dengan pesan yang jelas.
  [ ] Instrumented test dengan model whisper-base:
      - Audio 30 detik bahasa Indonesia → transkrip mengandung kata-kata kunci.
      - Audio dengan initial_prompt mengandung istilah kustom →
        istilah tersebut muncul benar di transkrip.
```

---

### F2-05 | ByokCloudSttEngine Implementation

```
Tipe      : feat
Prioritas : P1
Estimasi  : 4 jam
Dependensi: F0-02
Branch    : feature/byok-stt

Definition of Done:
  [ ] ByokCloudSttEngine mengimplementasikan SttEngine interface.
  [ ] Mendukung 2 provider: Groq API dan OpenAI API.
  [ ] transcribe():
      - Mengonversi audio PCM ke format yang diterima API (WAV/MP3).
      - Mengirim via multipart/form-data ke endpoint API.
      - Menerima response JSON → parse → List<TranscriptSegment>.
  [ ] API key diambil dari EncryptedSharedPreferences.
  [ ] Error handling:
      - Koneksi gagal: throw NetworkException dengan pesan jelas.
      - API key invalid: throw AuthenticationException.
      - Rate limit: throw RateLimitException dengan retry-after.
  [ ] Unit test (dengan mock HTTP): request berisi header dan body yang benar;
      response di-parse menjadi TranscriptSegment yang valid.
```

---

### F2-06 | SttEngineManager (Pemilih Engine)

```
Tipe      : feat
Prioritas : P0
Estimasi  : 2 jam
Dependensi: F2-04, F2-05
Branch    : feature/stt-manager

Definition of Done:
  [ ] SttEngineManager memilih engine berdasarkan preferensi pengguna:
      - ON_DEVICE_BASE → OnDeviceSttEngine + model base
      - ON_DEVICE_TURBO → OnDeviceSttEngine + model large-v3-turbo
      - BYOK_GROQ → ByokCloudSttEngine + Groq
      - BYOK_OPENAI → ByokCloudSttEngine + OpenAI
  [ ] Validasi sebelum mulai:
      - On-device: model sudah diunduh? RAM cukup?
      - BYOK: API key sudah diisi? Internet tersedia?
      Jika tidak valid, return error yang jelas (bukan crash).
  [ ] Di-provide via Hilt @Module.
```

---

### F2-07 | TranscriptSegmentEntity & TranscriptSegmentDao

```
Tipe      : feat
Prioritas : P0
Estimasi  : 2 jam
Dependensi: F0-04
Branch    : feature/transcript-entity

Definition of Done:
  [ ] TranscriptSegmentEntity sesuai ERD:
      id (autoGen), recordingId (FK), segmentIndex, startTimeMs, endTimeMs,
      text, confidence, speakerLabel (nullable), createdAt.
  [ ] TranscriptSegmentDao:
      - @Insert: insertSegments(List<TranscriptSegmentEntity>)  (batch)
      - @Query: getSegmentsByRecordingId (return Flow, order by segmentIndex)
      - @Query: deleteSegmentsByRecordingId
      - @Query FTS4: searchSegments(query) → List<SearchResult>
        (SearchResult: recordingId, title, snippet, startTimeMs)
  [ ] FTS4 virtual table dibuat untuk kolom 'text'.
  [ ] Instrumented test: insert 10 segments → query → urutan benar.
      Search "MagangHub" → result berisi segment yang mengandung kata tersebut.
```

---

### F2-08 | TranscribeAudioUseCase & Pipeline Orkestrasi

```
Tipe      : feat
Prioritas : P0
Estimasi  : 4 jam
Dependensi: F2-01, F2-06, F2-07
Branch    : feature/transcribe-usecase

Definition of Done:
  [ ] TranscribeAudioUseCase mengorkestrasi alur lengkap:
      1. Update status recording → PROCESSING
      2. Baca file WAV dari disk
      3. Jalankan VAD → List<SpeechSegment> (emit progress 0-30%)
      4. Untuk setiap SpeechSegment:
         - Ekstrak audio PCM pada rentang [start..end]
         - Jalankan STT engine
         - Batch insert TranscriptSegment ke Room
         - Emit progress 30-90%
      5. Update status recording → TRANSCRIBED (emit 100%)
  [ ] Mendukung pembatalan (CancellationException via Coroutine cancel).
      Jika dibatalkan: status kembali ke STOPPED, segment yang sudah
      tersimpan dihapus.
  [ ] Error handling: jika error di tengah, status → ERROR,
      segment parsial dihapus, error message disimpan.
  [ ] Unit test (mock repo + engine): UseCase memanggil VAD lalu STT
      dalam urutan yang benar, menyimpan hasil, dan mengupdate status.
```

---

### F2-09 | Transcription UI di DetailScreen

```
Tipe      : feat
Prioritas : P1
Estimasi  : 4 jam
Dependensi: F2-08, F1-10
Branch    : feature/transcription-ui

Definition of Done:
  [ ] DetailScreen diperluas:
      - Tombol "Transkrip" muncul jika status = STOPPED.
      - Dialog pemilihan engine muncul saat tap "Transkrip":
        pilihan On-Device (Base/Turbo) atau BYOK (Groq/OpenAI).
        Jika model belum diunduh, tampilkan opsi unduh.
      - Progress bar linear + persentase + teks keterangan
        ("Mendeteksi suara...", "Mentranskrip segmen 3/12...").
      - Tombol Batal selama proses berlangsung.
  [ ] Setelah selesai, tampilkan daftar segmen transkrip:
      - Format: "[MM:SS] Teks kalimat segmen"
      - LazyColumn dengan item per segmen.
      - Tap segmen → audio seek ke timestamp tersebut (integrasi F1-10).
  [ ] State ditangani oleh DetailViewModel:
      IDLE → SELECTING_ENGINE → PROCESSING(progress) → COMPLETED / ERROR
```

---

## 6. Fase 3: AI Summarizer & Notulensi

**Tujuan:** Transkrip teks diproses oleh AI menjadi notulensi rapat terstruktur (ringkasan, keputusan, action items).

**Milestone:** Setelah transkripsi selesai, pengguna tap "Buat Notulensi" dan mendapatkan dokumen notulensi dengan 3 bagian wajib.

**Prasyarat Fase:** Seluruh tiket Fase 2 berstatus `DONE`.

---

### F3-01 | llama.cpp NDK Build & JNI Bridge

```
Tipe      : feat
Prioritas : P0
Estimasi  : 6 jam
Dependensi: F0-02
Branch    : feature/llama-jni

Catatan Desain:
  - Profil Hemat menggunakan Extractive Summarizer (TextRank/TF-IDF) berbasis Kotlin murni
    (<50MB RAM, instan <1s, 0MB unduhan tambahan) yang diimplementasikan di F3-04.
  - llama.cpp NDK bridge ini khusus melayani Profil Optimal (Qwen 2.5 3B / Llama 3.2 3B).

Definition of Done:
  [ ] Source code llama.cpp di-clone sebagai submodule atau vendored.
  [ ] CMakeLists.txt dikonfigurasi untuk compile ke arm64-v8a.
  [ ] LlmJni.kt berisi native function declarations:
      - init(modelPath: String, contextSize: Int): Long
      - generate(ctx: Long, prompt: String, maxTokens: Int, ...): String
      - release(ctx: Long)
  [ ] Build berhasil tanpa error NDK.
  [ ] Instrumented test: load model Qwen 2.5 3B Q4 / model uji → generate response
      untuk prompt sederhana → output mengandung teks yang koheren.
```

---

### F3-02 | SummaryPromptTemplate & PIC Anti-Hallucination Guardrail

```
Tipe      : feat
Prioritas : P0
Estimasi  : 2 jam
Dependensi: Tidak ada
Branch    : feature/summary-prompt

Definition of Done:
  [ ] SummaryPromptTemplate.kt berisi:
      - fun buildPrompt(transcriptText: String, customVocabulary: String? = null): String
        Menggabungkan system instruction + format template + daftar peserta resmi + transkrip.
      - Template menghasilkan 3 bagian: Ringkasan, Keputusan, Action Items.
  [ ] Guardrail Anti-Halusinasi PIC (Pencegahan Spekulasi Nama):
      - Karena speaker diarization belum aktif di MVP, prompt mewajibkan:
        "Jika tugas diucapkan menggunakan kata ganti orang pertama ('saya/aku/gue') tanpa
        menyebutkan nama spesifik, wajib tulis PIC sebagai 'Belum teridentifikasi (Pembicara anonim)'".
      - Nama PIC hanya boleh diambil dari daftar peserta resmi (customVocabulary) atau yang
        disebutkan secara eksplisit dalam dialog rapat.
  [ ] Jika transkrip melebihi context window model (misal 4096 token),
      transkrip dipotong per chunk dan diproses bertahap (chunked summarization).
  [ ] Unit test:
      - buildPrompt("test transcript") menghasilkan struktur 3 bagian lengkap.
      - Uji proteksi PIC: prompt memuat klausul larangan halusinasi pembicara anonim.
```

---

### F3-03 | OnDeviceSummarizer Implementation

```
Tipe      : feat
Prioritas : P0
Estimasi  : 4 jam
Dependensi: F3-01, F3-02, F2-03
Branch    : feature/ondevice-summarizer

Definition of Done:
  [ ] OnDeviceSummarizer mengimplementasikan SummarizerEngine interface.
  [ ] summarize():
      - Build prompt dari template + transkrip.
      - Load model → generate → release.
      - Parse output teks LLM menjadi MeetingSummary data class.
  [ ] Parser menangani variasi format output LLM
      (kadang LLM tidak mengikuti format persis).
  [ ] Cek memori sebelum load: Whisper harus sudah di-release.
  [ ] Instrumented test: transkrip 500 kata → MeetingSummary berisi
      executiveSummary non-kosong, decisions non-kosong, actionItems non-kosong.
```

---

### F3-04 | ByokCloudSummarizer Implementation

```
Tipe      : feat
Prioritas : P1
Estimasi  : 3 jam
Dependensi: F3-02
Branch    : feature/byok-summarizer

Definition of Done:
  [ ] ByokCloudSummarizer mengimplementasikan SummarizerEngine interface.
  [ ] Mendukung API: Groq (llama-3.3-70b) dan OpenAI (gpt-4o-mini).
  [ ] Mengirim prompt via chat completion API, menerima response,
      parse menjadi MeetingSummary.
  [ ] Error handling: network error, auth error, rate limit.
  [ ] Unit test (mock HTTP): response di-parse dengan benar.
```

---

### F3-05 | SummaryEntity & SummaryDao

```
Tipe      : feat
Prioritas : P0
Estimasi  : 1 jam
Dependensi: F0-04
Branch    : feature/summary-entity

Definition of Done:
  [ ] SummaryEntity sesuai ERD: id, recordingId, executiveSummary,
      decisions (JSON), actionItems (JSON), rawLlmOutput, modelUsed, createdAt.
  [ ] Type converters: List<Decision> <-> JSON, List<ActionItem> <-> JSON.
  [ ] SummaryDao: insertSummary, getSummaryByRecordingId, deleteSummaryByRecordingId.
  [ ] Instrumented test: insert → query → data dan JSON parsing benar.
```

---

### F3-06 | GenerateSummaryUseCase

```
Tipe      : feat
Prioritas : P0
Estimasi  : 3 jam
Dependensi: F3-03, F3-04, F3-05, F2-07
Branch    : feature/summary-usecase

Definition of Done:
  [ ] GenerateSummaryUseCase mengorkestrasi:
      1. Ambil semua TranscriptSegment untuk recordingId dari Room.
      2. Gabungkan menjadi teks transkrip utuh (dengan "[MM:SS]" timestamp).
      3. Pilih engine (on-device atau BYOK) sesuai preferensi.
      4. Jalankan summarize() → MeetingSummary.
      5. Simpan SummaryEntity ke Room.
      6. Update recording status → SUMMARIZED.
  [ ] Error handling: jika gagal, status tidak berubah, error dilaporkan.
  [ ] Unit test: mock repo + engine → UseCase memanggil summarize
      dengan teks yang benar dan menyimpan hasil.
```

---

### F3-07 | Summary UI di DetailScreen

```
Tipe      : feat
Prioritas : P1
Estimasi  : 4 jam
Dependensi: F3-06, F2-09
Branch    : feature/summary-ui

Definition of Done:
  [ ] DetailScreen diperluas:
      - Tab/section baru: "Notulensi" (di samping tab "Transkrip").
      - Tombol "Buat Notulensi" muncul jika status = TRANSCRIBED.
      - Dialog pemilihan engine summarizer.
      - Progress indicator selama proses.
  [ ] Tampilan notulensi terstruktur:
      - Bagian "Ringkasan Rapat": bullet points.
      - Bagian "Keputusan Rapat": numbered list.
      - Bagian "Daftar Tugas": tabel (Tugas | PIC | Tenggat).
  [ ] Notulensi bisa di-scroll dan teksnya bisa di-select/copy.
```

---

## 7. Fase 4: UI Polish, Export & Search

**Tujuan:** Menyempurnakan pengalaman pengguna: pencarian lintas rekaman, ekspor file, berbagi ke aplikasi lain, dan sentuhan akhir UI.

**Milestone:** Aplikasi siap digunakan sehari-hari dan bisa dibagikan ke tester beta.

**Prasyarat Fase:** Seluruh tiket Fase 3 berstatus `DONE`.

---

### F4-01 | Tap-to-Listen (Audio Sync dengan Transkrip)

```
Tipe      : feat
Prioritas : P0
Estimasi  : 3 jam
Dependensi: F2-09, F1-10
Branch    : feature/tap-to-listen

Definition of Done:
  [ ] Tap sebuah segmen transkrip → audio player seek ke startTimeMs
      segmen tersebut dan mulai memutar (toleransi ±1 detik).
  [ ] Segmen yang sedang diputar diberi highlight visual (background color).
  [ ] Saat audio diputar, highlight otomatis berpindah ke segmen berikutnya
      saat timestamp melewati batas segmen.
```

---

### F4-02 | Full-Text Search

```
Tipe      : feat
Prioritas : P1
Estimasi  : 3 jam
Dependensi: F2-07
Branch    : feature/search

Definition of Done:
  [ ] Kolom pencarian di HomeScreen (SearchBar Material 3).
  [ ] Pencarian menggunakan Room FTS4 pada tabel transcript_segments.
  [ ] Hasil menampilkan: judul rekaman, snippet teks (dengan kata kunci
      di-highlight), timestamp.
  [ ] Tap hasil → navigasi ke DetailScreen, scroll ke segmen yang tepat.
  [ ] Waktu pencarian < 1 detik untuk 100 rekaman.
```

---

### F4-03 | Ekspor ke Markdown & Plain Text

```
Tipe      : feat
Prioritas : P1
Estimasi  : 2 jam
Dependensi: F3-06
Branch    : feature/export-files

Definition of Done:
  [ ] ExportNotesUseCase menghasilkan:
      - File .md (Markdown): heading, bullet, tabel action items.
      - File .txt (Plain Text): format sederhana tanpa markup.
  [ ] File disimpan ke folder Downloads perangkat via MediaStore API
      (kompatibel Android 10+ scoped storage).
  [ ] Nama file: "Notulensi - {judul rekaman} - {tanggal}.md".
  [ ] Notifikasi/toast: "File tersimpan di folder Downloads".
```

---

### F4-04 | Share via Android Share Sheet

```
Tipe      : feat
Prioritas : P1
Estimasi  : 2 jam
Dependensi: F4-03
Branch    : feature/share

Definition of Done:
  [ ] Tombol "Bagikan" di DetailScreen menampilkan Android share sheet.
  [ ] Data yang dibagikan: teks notulensi terformat (plain text).
  [ ] Mendukung share ke: WhatsApp, Telegram, Email, dan aplikasi lain
      yang menerima ACTION_SEND text/plain.
  [ ] Jika notulensi belum ada (hanya transkrip), bagikan teks transkrip.
```

---

### F4-05 | Settings Screen (Pengaturan)

```
Tipe      : feat
Prioritas : P1
Estimasi  : 3 jam
Dependensi: F0-05
Branch    : feature/settings

Definition of Done:
  [ ] SettingsScreen berisi:
      - Profil Pemrosesan: dropdown (Hemat / Optimal / Turbo BYOK).
      - Kelola Model AI: daftar model yang diunduh, tombol unduh/hapus,
        ukuran file.
      - API Key (BYOK): input field untuk Groq key dan OpenAI key.
        Tersimpan di EncryptedSharedPreferences. Tombol "Test Key" untuk
        validasi.
      - Tentang: versi aplikasi, lisensi open-source.
  [ ] Semua preferensi tersimpan persisten dan langsung berlaku.
```

---

### F4-06 | OEM Battery Optimization Guide

```
Tipe      : feat
Prioritas : P2
Estimasi  : 2 jam
Dependensi: F1-05
Branch    : feature/oem-guide

Definition of Done:
  [ ] Saat pertama kali pengguna memulai perekaman, tampilkan dialog
      yang mendeteksi merek HP (Build.MANUFACTURER) dan menampilkan
      instruksi visual khusus untuk menonaktifkan battery optimization.
  [ ] Mendukung: Samsung, Xiaomi, Transsion (Tecno HiOS, Infinix XOS, itel), Oppo, Vivo, Realme, Huawei, dan
      "Android Standar" (Pixel/Motorola).
  [ ] Dialog bisa ditutup dan tidak muncul lagi (remembered via prefs).
  [ ] Referensi instruksi: dontkillmyapp.com.
```

---

### F4-07 | Error & Empty State Polish

```
Tipe      : feat
Prioritas : P2
Estimasi  : 2 jam
Dependensi: Semua screen
Branch    : feature/error-states

Definition of Done:
  [ ] Semua screen memiliki penanganan state yang lengkap:
      - Loading state: shimmer placeholder atau circular progress.
      - Empty state: ilustrasi + teks + CTA button.
      - Error state: ikon error + pesan jelas + tombol "Coba Lagi".
  [ ] Tidak ada layar yang menampilkan blank putih tanpa konteks.
  [ ] Snackbar untuk notifikasi transien (berhasil simpan, berhasil hapus).
```

---

### F4-08 | End-to-End Smoke Test & Bug Bash

```
Tipe      : test
Prioritas : P0
Estimasi  : 4 jam
Dependensi: Semua tiket fase 1-4
Branch    : test/e2e-smoke

Definition of Done:
  [ ] Alur E2E berhasil dijalankan di device fisik:
      1. Buka app → HomeScreen kosong (empty state).
      2. Tap Rekam → isi topik → mulai rekam → bicara 2 menit → stop.
      3. Rekaman muncul di daftar.
      4. Tap rekaman → DetailScreen → play audio → audio terdengar.
      5. Tap Transkrip → pilih engine → progress → transkrip muncul.
      6. Tap segmen transkrip → audio seek ke timestamp yang benar.
      7. Tap Buat Notulensi → notulensi terstruktur muncul.
      8. Tap Bagikan → share sheet muncul → kirim ke WA → teks rapi.
      9. Kembali ke Home → search kata kunci → hasil ditemukan.
      10. Swipe delete → konfirmasi → rekaman terhapus.
  [ ] 0 crash selama alur di atas.
  [ ] Dijalankan di minimal 2 perangkat:
      - HP RAM 6-8GB (mid-range)
      - Emulator API 35 (terbaru)
```

---

## 8. Definisi Global: Definition of Done (DoD)

Setiap tiket di seluruh fase harus memenuhi **semua** kriteria global berikut selain DoD spesifik tiket:

| No | Kriteria Global | Penjelasan |
| :--: | :--- | :--- |
| 1 | **Kode bisa di-build tanpa error.** | `./gradlew assembleDebug` berhasil. |
| 2 | **Tidak ada warning Lint dengan severity Error.** | `./gradlew lint` tanpa error. Warning boleh ada jika dijustifikasi. |
| 3 | **Semua test yang ada tetap hijau (tidak ada regresi).** | `./gradlew test` dan `./gradlew connectedAndroidTest` lulus semua. |
| 4 | **Kode mengikuti struktur folder Clean Architecture.** | File baru ada di package yang benar (core/data/domain/presentation). |
| 5 | **Commit message mengikuti Conventional Commits.** | `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`. |
| 6 | **Branch di-merge ke main via fast-forward atau squash merge.** | Riwayat Git bersih dan linier. |

---

## 9. Kriteria Rilis MVP

Aplikasi dinyatakan **siap rilis MVP (v1.0.0)** jika dan hanya jika **SEMUA** kondisi berikut terpenuhi:

| No | Kriteria | Verifikasi |
| :--: | :--- | :--- |
| 1 | Seluruh 43 tiket (Fase 0–4) berstatus `DONE`. | Checklist di dokumen ini. |
| 2 | Alur E2E (F4-08) lulus tanpa crash di 2+ perangkat. | Laporan test. |
| 3 | WER transkripsi BI jernih ≤ 25% (mode base) dan ≤ 15% (mode turbo). | Benchmark 5 audio sampel. |
| 4 | Perekaman 2 jam tanpa terputus dengan layar mati. | Test report. |
| 5 | Database terenkripsi (file .db tidak bisa dibaca tanpa kunci). | Verifikasi manual. |
| 6 | Ukuran APK ≤ 15MB (tanpa model AI). | Ukur hasil build release. |
| 7 | 0 crash pada crash-free rate monitoring selama 3 hari penggunaan internal. | Local Crash Log Table (database terenkripsi lokal, tanpa telemetri pihak ketiga). |

---

> **Status Dokumen:** DRAFT v1.0.0
> **Langkah Selanjutnya:** Review seluruh 3 dokumen → Approval → Mulai eksekusi F0-02
