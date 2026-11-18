package com.xiaosi555.yovoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class WavJoinerTest {
    @Test
    fun joinsPcm16ClipsAndInsertsSilence() {
        val dir = Files.createTempDirectory("wav-join-test").toFile()
        try {
            val first = File(dir, "first.wav")
            val second = File(dir, "second.wav")
            val output = File(dir, "joined.wav")
            writeTinyWav(first, shortArrayOf(1000), 44_100, 1)
            writeTinyWav(second, shortArrayOf(-1000), 44_100, 1)

            WavJoiner.join(listOf(first, second), output, 100)

            val bytes = output.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals(44_100, littleInt(bytes, 24))
            assertEquals(1, littleShort(bytes, 22))
            val dataSize = littleInt(bytes, 40)
            assertEquals(2 + (44_100 / 10) * 2 + 2, dataSize)
            assertEquals(44 + dataSize, bytes.size)
            assertEquals(1000, littleShort(bytes, 44))
            assertEquals(0, littleShort(bytes, 46))
            assertEquals(-1000, littleShort(bytes, bytes.size - 2))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeTinyWav(file: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val dataBytes = samples.size * 2
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeBytes("RIFF"); writeIntLE(out, 36 + dataBytes)
            out.writeBytes("WAVE"); out.writeBytes("fmt "); writeIntLE(out, 16)
            writeShortLE(out, 1); writeShortLE(out, channels)
            writeIntLE(out, sampleRate); writeIntLE(out, sampleRate * channels * 2)
            writeShortLE(out, channels * 2); writeShortLE(out, 16)
            out.writeBytes("data"); writeIntLE(out, dataBytes)
            samples.forEach { writeShortLE(out, it.toInt()) }
        }
        file.writeBytes(buffer.toByteArray())
    }

    private fun writeIntLE(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xff)
        out.writeByte((value ushr 8) and 0xff)
        out.writeByte((value ushr 16) and 0xff)
        out.writeByte((value ushr 24) and 0xff)
    }

    private fun writeShortLE(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xff)
        out.writeByte((value ushr 8) and 0xff)
    }

    private fun littleInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun littleShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
}
