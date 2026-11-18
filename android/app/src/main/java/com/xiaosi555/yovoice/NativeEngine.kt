package com.xiaosi555.yovoice

/**
 * audio.cpp Android JNI 入口。原生库加载失败时返回明确状态，不影响应用启动。
 */
object NativeEngine {
    @Volatile private var libraryLoaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (!libraryLoaded) {
            System.loadLibrary("yovoice_jni")
            libraryLoaded = true
        }
    }

    fun probe(): String = runCatching {
        ensureLoaded()
        nativeProbe()
    }.getOrElse { errorJson(it) }

    fun loadModel(
        path: String,
        family: String,
        threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    ): String = loadTaskModel(path, family, "tts", "offline", threads)

    fun loadTaskModel(
        path: String,
        family: String,
        task: String,
        mode: String = "offline",
        threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    ): String = runCatching {
        ensureLoaded()
        nativeLoadModel(path, family, task, mode, threads)
    }.getOrElse { errorJson(it) }

    fun synthesize(
        text: String,
        language: String,
        outputPath: String,
        speakingRate: Float = 1.0f,
        pitchMultiplier: Float = 1.0f
    ): String = runCatching {
        ensureLoaded()
        nativeSynthesize(text, language, outputPath, speakingRate, pitchMultiplier)
    }.getOrElse { errorJson(it) }

    /**
     * PCM 必须是交错 float32，建议 16 kHz 单声道；音频解码/重采样由 Android 层负责。
     */
    fun cloneVoice(
        text: String,
        referenceText: String,
        referencePcm: FloatArray,
        sampleRate: Int,
        channels: Int,
        outputPath: String
    ): String = runCatching {
        ensureLoaded()
        nativeClone(text, referenceText, referencePcm, sampleRate, channels, outputPath)
    }.getOrElse { errorJson(it) }

    fun transcribe(
        pcm: FloatArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        language: String = ""
    ): String = runCatching {
        ensureLoaded()
        nativeTranscribe(pcm, sampleRate, channels, language)
    }.getOrElse { errorJson(it) }

    fun unload() {
        runCatching {
            ensureLoaded()
            nativeUnload()
        }
    }

    private fun errorJson(error: Throwable): String {
        val message = (error.localizedMessage ?: error.javaClass.simpleName)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """{"ok":false,"status":"unavailable","error":"$message"}"""
    }

    private external fun nativeProbe(): String
    private external fun nativeLoadModel(path: String, family: String, task: String, mode: String, threads: Int): String
    private external fun nativeSynthesize(text: String, language: String, outputPath: String, speakingRate: Float, pitchMultiplier: Float): String
    private external fun nativeTranscribe(pcm: FloatArray, sampleRate: Int, channels: Int, language: String): String
    private external fun nativeClone(text: String, referenceText: String, referencePcm: FloatArray, sampleRate: Int, channels: Int, outputPath: String): String
    private external fun nativeUnload()
}
