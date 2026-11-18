cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

# ─────────────────────────────────────────────────────────────────────────────
# whisper.cpp — Pustaka mesin transkripsi AI (Whisper Large v3 Turbo Q5)
# Dikompilasi sebagai pustaka statis, kemudian di-link ke shared library JNI.
# ─────────────────────────────────────────────────────────────────────────────

set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_NO_METAL       ON  CACHE BOOL "" FORCE)  # Metal = khusus Apple macOS/iOS
set(WHISPER_NO_CUDA        ON  CACHE BOOL "" FORCE)  # CUDA = khusus NVIDIA GPU
set(WHISPER_NO_VULKAN      ON  CACHE BOOL "" FORCE)  # Nonaktifkan Vulkan untuk stabilitas Android

# Pastikan seluruh pustaka native (whisper, ggml, whisper_jni) selalu dikompilasi dengan optimasi maksimal (-O3)
# -fno-finite-math-only diperlukan karena ggml membutuhkan penanganan nilai NaN dan Infinity (non-finite math)
add_compile_options(-O3 -DNDEBUG -fno-finite-math-only)

add_subdirectory(whisper.cpp)

# ─────────────────────────────────────────────────────────────────────────────
# whisper_jni — Shared library JNI yang dimuat oleh System.loadLibrary("whisper_jni")
# ─────────────────────────────────────────────────────────────────────────────

add_library(whisper_jni SHARED whisper_jni.cpp)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include
)

target_link_libraries(whisper_jni
    whisper       # Pustaka statis whisper.cpp (otomatis menarik ggml)
    log           # Android logging (__android_log_print)
)

# Optimasi kompilasi C++ untuk inferensi
target_compile_options(whisper_jni PRIVATE
    -O3 -DNDEBUG -fno-finite-math-only
)
