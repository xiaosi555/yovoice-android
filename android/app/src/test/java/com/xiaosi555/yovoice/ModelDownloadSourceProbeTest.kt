package com.xiaosi555.yovoice

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/model.bin") { exchange ->
            if (contentRange != null) exchange.responseHeaders.add("Content-Range", contentRange)
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/model.bin")
        } finally {
            server.stop(0)
        }
    }
}
