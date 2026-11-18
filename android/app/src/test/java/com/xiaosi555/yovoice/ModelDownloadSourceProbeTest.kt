package com.xiaosi555.yovoice

import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDownloadSourceProbeTest {
    @Test
    fun acceptsAValidFirstByteRange() {
        val body = ByteArray(16) { it.toByte() }
        withServer(206, "bytes 0-15/16", body) { url ->
            val result = ModelDownloadSourceProbe.probe("test", "Local test", url, body.size.toLong())
            assertNotNull(result)
        }
    }

    @Test
    fun rejectsAContentRangeThatDoesNotStartAtZero() {
        val body = ByteArray(16) { it.toByte() }
        withServer(206, "bytes 100-115/1000", body) { url ->
            val result = ModelDownloadSourceProbe.probe("test", "Invalid range", url, 1000L)
            assertNull(result)
        }
    }

    @Test
    fun rejectsATruncatedProbeBody() {
        val body = ByteArray(8) { it.toByte() }
        withServer(200, null, body) { url ->
            val result = ModelDownloadSourceProbe.probe("test", "Truncated response", url, 16L)
            assertNull(result)
        }
    }

    private fun withServer(
        status: Int,
        contentRange: String?,
        body: ByteArray,
        block: (String) -> Unit
    ) {
        ServerSocket(0).use { server ->
            val worker = thread(name = "probe-test-http-server", isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val reason = if (status == 206) "Partial Content" else "OK"
                    val headers = buildString {
                        append("HTTP/1.1 $status $reason\r\n")
                        append("Content-Length: ${body.size}\r\n")
                        append("Connection: close\r\n")
                        if (contentRange != null) append("Content-Range: $contentRange\r\n")
                        append("\r\n")
                    }
                    val output = socket.getOutputStream()
                    output.write(headers.toByteArray(Charsets.US_ASCII))
                    output.write(body)
                    output.flush()
                }
            }
            try {
                block("http://127.0.0.1:${server.localPort}/model.bin")
            } finally {
                worker.join(2_000)
            }
        }
    }
}
