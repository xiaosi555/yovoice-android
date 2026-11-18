package com.xiaosi555.yovoice

import java.net.HttpURLConnection
import java.net.URL

data class DownloadSourceSpeed(
    val source: String,
    val label: String,
    val bytesPerSecond: Double
) {
    fun formattedSpeed(): String {
        val mb = bytesPerSecond / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.2f MB/s", mb)
        else String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
    }
}

/** Measures a short byte range from the actual model asset, not the provider home page. */
object ModelDownloadSourceProbe {
    private const val PROBE_BYTES = 192 * 1024

    fun select(model: ModelEntry, revision: String): DownloadSourceSpeed? {
        val firstFile = model.packageFiles().firstOrNull() ?: return null
        val candidates = listOf(
            "huggingface" to "Hugging Face",
            "mirror" to "HF 镜像",
            "modelscope" to "ModelScope"
        ).map { (source, label) ->
            Triple(source, label, model.fileUrl(firstFile, source, revision))
        }.distinctBy { it.third }

        return candidates.mapNotNull { (source, label, uri) ->
            probe(source, label, uri, firstFile.size)
        }.maxByOrNull { it.bytesPerSecond }
    }

    private fun probe(source: String, label: String, uri: String, expectedSize: Long): DownloadSourceSpeed? {
        val countLimit = minOf(PROBE_BYTES.toLong(), expectedSize).coerceAtLeast(1L)
        val connection = runCatching { URL(uri).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Yovoice-Android/0.1")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=0-${countLimit - 1}")
            val started = System.nanoTime()
            val status = connection.responseCode
            if (status !in 200..299) return null
            val input = connection.inputStream
            val buffer = ByteArray(32 * 1024)
            var received = 0L
            input.use {
                while (received < countLimit) {
                    val read = it.read(buffer, 0, minOf(buffer.size.toLong(), countLimit - received).toInt())
                    if (read < 0) break
                    received += read
                }
            }
            val elapsed = ((System.nanoTime() - started).coerceAtLeast(1L)) / 1_000_000_000.0
            if (received <= 0L) null else DownloadSourceSpeed(source, label, received / elapsed)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
