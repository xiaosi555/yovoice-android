package com.xiaosi555.yovoice

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset

/** Joins PCM16 WAV clips with short silence; Audio8 output clips share a 44.1 kHz mono format. */
object WavJoiner {
    private data class PcmWav(val sampleRate: Int, val channels: Int, val data: ByteArray)

    fun join(inputs: List<File>, output: File, silenceMillis: Int = 220) {
        require(inputs.isNotEmpty()) { "没有可合并的配音片段" }
        require(silenceMillis in 0..2000) { "片段间隔超出范围" }
        val clips = inputs.map { readPcm16(it) }
        val first = clips.first()
        require(clips.all { it.sampleRate == first.sampleRate && it.channels == first.channels }) {
            "配音片段的采样率或声道数不一致"
        }
        val bytesPerSecond = first.sampleRate * first.channels * 2
        val silenceBytes = (bytesPerSecond.toLong() * silenceMillis / 1000L).toInt()
        val silence = ByteArray(silenceBytes - silenceBytes % (first.channels * 2))
        val total = clips.sumOf { it.data.size.toLong() } + silence.size.toLong() * (clips.size - 1)
        require(total <= Int.MAX_VALUE - 44L) { "合并后的故事配音文件过大" }
        val parent = output.parentFile ?: error("故事配音输出目录无效")
        if (!parent.exists() && !parent.mkdirs()) error("无法创建故事配音目录")
        FileOutputStream(output).use { stream ->
            stream.write("RIFF".toByteArray(Charset.forName("US-ASCII")))
            writeU32(stream, 36L + total)
            stream.write("WAVEfmt ".toByteArray(Charset.forName("US-ASCII")))
            writeU32(stream, 16)
            writeU16(stream, 1)
            writeU16(stream, first.channels)
            writeU32(stream, first.sampleRate.toLong())
            writeU32(stream, (first.sampleRate * first.channels * 2).toLong())
            writeU16(stream, first.channels * 2)
            writeU16(stream, 16)
            stream.write("data".toByteArray(Charset.forName("US-ASCII")))
            writeU32(stream, total)
            clips.forEachIndexed { index, clip ->
                stream.write(clip.data)
                if (index != clips.lastIndex) stream.write(silence)
            }
            stream.flush()
        }
    }

    private fun readPcm16(file: File): PcmWav {
        require(file.isFile && file.length() >= 44L) { "配音片段文件无效：" + file.name }
        val bytes = FileInputStream(file).use { it.readBytes() }
        require(ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WAVE") { "不是有效 WAV 文件：" + file.name }
        var offset = 12
        var sampleRate = 0
        var channels = 0
        var format = 0
        var bits = 0
        var pcm: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val size = readU32(bytes, offset + 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val start = offset + 8
            if (start + size > bytes.size) break
            when (id) {
                "fmt " -> if (size >= 16) {
                    format = readU16(bytes, start)
                    channels = readU16(bytes, start + 2)
                    sampleRate = readU32(bytes, start + 4).toInt()
                    bits = readU16(bytes, start + 14)
                }
                "data" -> pcm = bytes.copyOfRange(start, start + size)
            }
            offset = start + size + (size and 1)
        }
        require(format == 1 && bits == 16 && channels in 1..2 && sampleRate > 0) {
            "仅支持 PCM16 WAV 配音片段：" + file.name
        }
        return PcmWav(sampleRate, channels, pcm ?: error("WAV 中缺少音频数据：" + file.name))
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charset.forName("US-ASCII"))

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private fun writeU16(stream: FileOutputStream, value: Int) {
        stream.write(value and 0xff)
        stream.write((value ushr 8) and 0xff)
    }

    private fun writeU32(stream: FileOutputStream, value: Long) {
        repeat(4) { shift -> stream.write(((value ushr (shift * 8)) and 0xff).toInt()) }
    }
}
