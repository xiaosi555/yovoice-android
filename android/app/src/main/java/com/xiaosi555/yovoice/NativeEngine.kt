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

    fun loadModel(path: String, family: String, threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)): String =
        runCatching {
            ensureLoaded()
            nativeLoadModel(path, family, threads)
        }.getOrElse { errorJson(it) }

    fun synthesize(text: String, language: String, outputPath: String): String =
        runCatching {
            ensureLoaded()
            nativeSynthesize(text, language, outputPath)
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
    private external fun nativeLoadModel(path: String, family: String, threads: Int): String
    private external fun nativeSynthesize(text: String, language: String, outputPath: String): String
    private external fun nativeUnload()
}
