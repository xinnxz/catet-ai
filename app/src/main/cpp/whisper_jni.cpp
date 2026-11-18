/**
 * whisper_jni.cpp — Jembatan JNI antara Kotlin/JVM dan pustaka C whisper.cpp.
 *
 * File ini mengimplementasikan fungsi-fungsi native yang dideklarasikan di
 * WhisperJni.kt (com.catetai.app.engine.whisper.WhisperJni).
 *
 * Alur pemanggilan:
 * ┌────────────────┐    JNI     ┌────────────────┐
 * │  WhisperJni.kt │ ────────▶  │ whisper_jni.cpp │ ──▶ whisper.cpp API
 * │  (Kotlin)      │  ◀────────  │ (C++)           │
 * └────────────────┘           └────────────────┘
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─────────────────────────────────────────────────────────────────────────────
// Data konteks untuk callback progress dan abort
// ─────────────────────────────────────────────────────────────────────────────

struct jni_callback_data {
    JNIEnv  *env;
    jobject  progress_callback;    // Referensi ke objek Kotlin (Float) -> Unit
    jmethodID invoke_method;       // Method ID dari Function1.invoke(Object)
    jclass   float_class;
    jmethodID float_init;
};

// Flag pembatalan global untuk nativeCancel()
static bool g_is_cancelled = false;

// ─────────────────────────────────────────────────────────────────────────────
// Callback: Dipanggil oleh whisper.cpp setiap kali ada kemajuan transkripsi
// ─────────────────────────────────────────────────────────────────────────────

static void whisper_progress_cb(
    struct whisper_context * /*ctx*/,
    struct whisper_state   * /*state*/,
    int progress,
    void *user_data
) {
    auto *cb_data = static_cast<jni_callback_data *>(user_data);
    if (cb_data == nullptr || cb_data->env == nullptr || cb_data->progress_callback == nullptr) return;

    // Normalisasi kemajuan (0-100 int) ke Float (0.0f - 1.0f)
    float normalized = static_cast<float>(progress) / 100.0f;

    JNIEnv *env = cb_data->env;
    jobject floatObj = env->NewObject(cb_data->float_class, cb_data->float_init, normalized);
    if (floatObj != nullptr) {
        // Panggil progressCallback.invoke(floatObj)
        jobject res = env->CallObjectMethod(cb_data->progress_callback, cb_data->invoke_method, floatObj);
        if (res != nullptr) {
            env->DeleteLocalRef(res); // Hapus referensi Unit Kotlin agar tidak bocor
        }
        env->DeleteLocalRef(floatObj);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Callback: Dipanggil oleh whisper.cpp untuk mengecek apakah inferensi dibatalkan
// ─────────────────────────────────────────────────────────────────────────────

static bool whisper_abort_cb(void * /*user_data*/) {
    return g_is_cancelled;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: Membaca file PCM 16-bit mono 16kHz ke vector<float>
// ─────────────────────────────────────────────────────────────────────────────

static bool read_pcm_file(const std::string &path, std::vector<float> &pcmf32) {
    FILE *f = fopen(path.c_str(), "rb");
    if (!f) {
        LOGE("Gagal membuka file PCM: %s", path.c_str());
        return false;
    }

    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    // PCM 16-bit = 2 byte per sampel
    long n_samples = file_size / 2;
    if (n_samples <= 0) {
        fclose(f);
        LOGE("File PCM kosong atau tidak valid: %ld byte", file_size);
        return false;
    }

    std::vector<int16_t> pcm16(n_samples);
    size_t bytes_read = fread(pcm16.data(), sizeof(int16_t), n_samples, f);
    fclose(f);

    if (static_cast<long>(bytes_read) != n_samples) {
        LOGE("Gagal membaca seluruh data PCM: dibaca %zu dari %ld sampel", bytes_read, n_samples);
        return false;
    }

    // Konversi int16 -> float32 (normalisasi rentang [-1.0f, 1.0f])
    pcmf32.resize(n_samples);
    for (long i = 0; i < n_samples; i++) {
        pcmf32[i] = static_cast<float>(pcm16[i]) / 32768.0f;
    }

    LOGI("PCM berhasil dimuat: %ld sampel (durasi: %.2f detik)",
         n_samples, static_cast<float>(n_samples) / 16000.0f);
    return true;
}

// =============================================================================
// FUNGSI-FUNGSI NATIVE JNI
// =============================================================================

extern "C" {

/**
 * nativeInit — Memuat model GGUF Whisper ke memori.
 *
 * @param modelPath Path absolut berkas model GGUF.
 * @return Handle pointer (jlong) konteks whisper, atau 0L jika gagal.
 */
JNIEXPORT jlong JNICALL
Java_com_catetai_app_engine_whisper_WhisperJni_nativeInit(
    JNIEnv *env,
    jobject /* this */,
    jstring modelPath
) {
    if (modelPath == nullptr) {
        LOGE("nativeInit: modelPath bernilai null");
        return 0L;
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Memuat model Whisper GGUF dari path: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Gagal memuat model Whisper (context bernilai null)");
        return 0L;
    }

    LOGI("Model Whisper berhasil dimuat ke memori. Handle: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

/**
 * nativeTranscribe — Menjalankan inferensi transkripsi pada data audio PCM 16kHz Mono.
 *
 * @param contextHandle Handle konteks dari nativeInit()
 * @param pcmPath Path berkas PCM
 * @param language Kode bahasa ISO (misal: "id", "en")
 * @param initialPrompt Kosakata kustom atau prompt biasing
 * @param numThreads Jumlah thread CPU
 * @param progressCallback Objek callback Kotlin (Float) -> Unit
 * @return Array 2 dimensi berformat Array<Array<Any>>: [[startMs, endMs, text], ...] atau null
 */
JNIEXPORT jobjectArray JNICALL
Java_com_catetai_app_engine_whisper_WhisperJni_nativeTranscribe(
    JNIEnv *env,
    jobject /* this */,
    jlong contextHandle,
    jstring pcmPath,
    jstring language,
    jstring initialPrompt,
    jint numThreads,
    jobject progressCallback
) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextHandle);
    if (ctx == nullptr) {
        LOGE("nativeTranscribe: contextHandle tidak valid (0 / null)");
        return nullptr;
    }

    // Reset flag pembatalan sebelum inferensi dimulai
    g_is_cancelled = false;

    // Baca berkas PCM
    const char *pcm_path_c = env->GetStringUTFChars(pcmPath, nullptr);
    std::vector<float> pcmf32;
    bool ok = read_pcm_file(pcm_path_c, pcmf32);
    env->ReleaseStringUTFChars(pcmPath, pcm_path_c);

    if (!ok) {
        LOGE("Gagal memproses berkas audio PCM untuk transkripsi");
        return nullptr;
    }

    // Inisialisasi parameter inferensi Greedy
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    const char *lang_c = env->GetStringUTFChars(language, nullptr);
    const char *prompt_c = (initialPrompt != nullptr) ? env->GetStringUTFChars(initialPrompt, nullptr) : nullptr;

    params.language         = lang_c;
    params.n_threads        = (numThreads > 0) ? numThreads : 4;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_special    = false;
    params.print_timestamps = false;
    params.single_segment   = false;

    if (prompt_c != nullptr && strlen(prompt_c) > 0) {
        params.initial_prompt = prompt_c;
    }

    // Setup callback progress
    jni_callback_data cb_data = {};
    cb_data.env = env;
    cb_data.progress_callback = progressCallback;

    jclass floatClass = nullptr;
    if (progressCallback != nullptr) {
        jclass funcClass = env->GetObjectClass(progressCallback);
        cb_data.invoke_method = env->GetMethodID(funcClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
        env->DeleteLocalRef(funcClass);

        floatClass = env->FindClass("java/lang/Float");
        cb_data.float_class = floatClass;
        cb_data.float_init = env->GetMethodID(floatClass, "<init>", "(F)V");

        params.progress_callback = whisper_progress_cb;
        params.progress_callback_user_data = &cb_data;
    }

    // Setup callback pembatalan (abort)
    params.abort_callback = whisper_abort_cb;
    params.abort_callback_user_data = nullptr;

    LOGI("Memulai inferensi transkripsi: sampel=%zu, bahasa=%s, threads=%d",
         pcmf32.size(), lang_c, params.n_threads);

    int result = whisper_full(ctx, params, pcmf32.data(), static_cast<int>(pcmf32.size()));

    env->ReleaseStringUTFChars(language, lang_c);
    if (prompt_c != nullptr) {
        env->ReleaseStringUTFChars(initialPrompt, prompt_c);
    }
    if (floatClass != nullptr) {
        env->DeleteLocalRef(floatClass);
    }

    if (result != 0) {
        LOGE("whisper_full menghasilkan kode error: %d (dibatalkan: %s)",
             result, g_is_cancelled ? "Ya" : "Tidak");
        return nullptr;
    }

    // Ekstraksi hasil segmen transkrip
    int n_segments = whisper_full_n_segments(ctx);
    LOGI("Inferensi transkripsi selesai. Ditemukan %d segmen.", n_segments);

    jclass objectClass = env->FindClass("java/lang/Object");
    jclass objectArrayClass = env->FindClass("[Ljava/lang/Object;");
    jobjectArray outerArray = env->NewObjectArray(n_segments, objectArrayClass, nullptr);

    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");

    for (int i = 0; i < n_segments; i++) {
        // whisper.cpp mengembalikan penanda waktu dalam satuan centisecond (10ms)
        int64_t t0_cs = whisper_full_get_segment_t0(ctx, i);
        int64_t t1_cs = whisper_full_get_segment_t1(ctx, i);
        const char *text = whisper_full_get_segment_text(ctx, i);

        // Konversi ke milidetik (ms)
        jlong startMs = static_cast<jlong>(t0_cs * 10);
        jlong endMs   = static_cast<jlong>(t1_cs * 10);

        jobjectArray innerArray = env->NewObjectArray(3, objectClass, nullptr);
        jobject startObj = env->NewObject(longClass, longInit, startMs);
        jobject endObj   = env->NewObject(longClass, longInit, endMs);
        jstring textObj  = env->NewStringUTF(text ? text : "");

        env->SetObjectArrayElement(innerArray, 0, startObj);
        env->SetObjectArrayElement(innerArray, 1, endObj);
        env->SetObjectArrayElement(innerArray, 2, textObj);

        env->DeleteLocalRef(startObj);
        env->DeleteLocalRef(endObj);
        env->DeleteLocalRef(textObj);

        env->SetObjectArrayElement(outerArray, i, innerArray);
        env->DeleteLocalRef(innerArray);
    }

    env->DeleteLocalRef(longClass);
    env->DeleteLocalRef(objectArrayClass);
    env->DeleteLocalRef(objectClass);

    return outerArray;
}

/**
 * nativeCancel — Menandai pembatalan transkripsi yang sedang berjalan.
 */
JNIEXPORT void JNICALL
Java_com_catetai_app_engine_whisper_WhisperJni_nativeCancel(
    JNIEnv * /*env*/,
    jobject /* this */,
    jlong /*contextHandle*/
) {
    LOGI("nativeCancel dipanggil. Inferensi akan segera dihentikan.");
    g_is_cancelled = true;
}

/**
 * nativeRelease — Membebaskan memori model C++ di heap.
 */
JNIEXPORT void JNICALL
Java_com_catetai_app_engine_whisper_WhisperJni_nativeRelease(
    JNIEnv * /*env*/,
    jobject /* this */,
    jlong contextHandle
) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextHandle);
    if (ctx != nullptr) {
        LOGI("Membebaskan memori model Whisper (handle: %p)", ctx);
        whisper_free(ctx);
    }
}

} // extern "C"
