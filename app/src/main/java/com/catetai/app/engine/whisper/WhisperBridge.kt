package com.catetai.app.engine.whisper

/**
 * Abstraksi jembatan JNI untuk memfasilitasi pengujian unit (Unit Test) di JVM murni tanpa memuat binary native.
 */
internal interface WhisperBridge {
    fun nativeInit(modelPath: String): Long
    fun nativeTranscribe(
        contextHandle: Long,
        pcmPath: String,
        language: String,
        initialPrompt: String,
        numThreads: Int,
        progressCallback: (Float) -> Unit
    ): Array<Array<Any>>?
    fun nativeCancel(contextHandle: Long)
    fun nativeRelease(contextHandle: Long)
}

/**
 * Implementasi jembatan JNI default yang memanggil langsung fungsi native C++ di [WhisperJni].
 */
internal object DefaultWhisperBridge : WhisperBridge {
    override fun nativeInit(modelPath: String): Long =
        WhisperJni.nativeInit(modelPath)

    override fun nativeTranscribe(
        contextHandle: Long,
        pcmPath: String,
        language: String,
        initialPrompt: String,
        numThreads: Int,
        progressCallback: (Float) -> Unit
    ): Array<Array<Any>>? =
        WhisperJni.nativeTranscribe(
            contextHandle,
            pcmPath,
            language,
            initialPrompt,
            numThreads,
            progressCallback
        )

    override fun nativeCancel(contextHandle: Long) =
        WhisperJni.nativeCancel(contextHandle)

    override fun nativeRelease(contextHandle: Long) =
        WhisperJni.nativeRelease(contextHandle)
}
