package com.xiaosi555.yovoice

/**
 * audio.cpp Android JNI 入口。只有集成原生库后才调用 probe；
 * 未加载原生库时会返回明确错误，不影响模型下载和应用启动。
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

    fun probe(): String = try {
        ensureLoaded()
        nativeProbe()
    } catch (error: Throwable) {
        """{"status":"unavailable","message":"${error.localizedMessage ?: error.javaClass.simpleName}"}"""
    }

    private external fun nativeProbe(): String
}
