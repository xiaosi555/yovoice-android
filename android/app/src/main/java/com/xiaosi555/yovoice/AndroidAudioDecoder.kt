package com.xiaosi555.yovoice

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decode the first audio track, downmix to mono and resample to 16 kHz float PCM. */
object AndroidAudioDecoder {
    private const val TARGET_RATE = 16_000

    fun decodeTo16kMono(context: Context, uri: Uri, maxDurationSeconds: Int = 600): FloatArray {
        require(maxDurationSeconds in 1..600) { "音频时长限制必须在 1 到 600 秒之间" }
        val maxOutputSamples = TARGET_RATE * maxDurationSeconds
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    audioTrack = index
                    audioFormat = format
                    break
                }
            }
            check(audioTrack >= 0 && audioFormat != null) { "文件中没有可读取的音轨" }
            val duration = runCatching { audioFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            require(duration <= maxDurationSeconds.toLong() * 1_000_000L || duration == 0L) { "音频超过 ${maxDurationSeconds} 秒限制，请先裁剪文件" }
            extractor.selectTrack(audioTrack)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: error("音频格式缺少 MIME")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            var inputEnded = false
            var outputEnded = false
            var outputRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            val collector = ResampledMonoCollector(outputRate, maxOutputSamples)
            var idleLoops = 0

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: error("无法读取解码输入缓冲区")
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleLoops++
                        if (inputEnded && idleLoops > 100) error("音频解码超时")
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        outputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        outputEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        collector.updateSourceRate(outputRate)
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        idleLoops = 0
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val pcm = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                            val bytesPerSample = when (outputEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> 4
                                AudioFormat.ENCODING_PCM_16BIT -> 2
                                AudioFormat.ENCODING_PCM_8BIT -> 1
                                else -> error("暂不支持此设备输出的 PCM 编码：$outputEncoding")
                            }
                            val frames = pcm.remaining() / (bytesPerSample * outputChannels)
                            for (frame in 0 until frames) {
                                var mixed = 0f
                                repeat(outputChannels) {
                                    val sample = when (outputEncoding) {
                                        AudioFormat.ENCODING_PCM_FLOAT -> pcm.float.coerceIn(-1f, 1f)
                                        AudioFormat.ENCODING_PCM_16BIT -> pcm.short / 32768f
                                        else -> ((pcm.get().toInt() and 0xff) - 128) / 128f
                                    }
                                    mixed += sample
                                }
                                collector.add(mixed / outputChannels.coerceAtLeast(1))
                            }
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            val result = collector.toArray()
            require(result.isNotEmpty()) { "没有从文件中解码出有效音频" }
            return result
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private class ResampledMonoCollector(initialRate: Int, private val maxOutputSamples: Int) {
        private var ratio = initialRate.coerceAtLeast(1).toDouble() / TARGET_RATE
        private var nextOutputAt = 0.0
        private var sourceIndex = 0L
        private var previous: Float? = null
        private var values = FloatArray(16_384)
        private var count = 0

        fun updateSourceRate(rate: Int) {
            require(rate in 8_000..192_000) { "音频采样率异常：$rate Hz" }
            ratio = rate.toDouble() / TARGET_RATE
        }

        fun add(sample: Float) {
            val prev = previous
            if (prev == null) {
                append(sample)
                previous = sample
                sourceIndex = 0
                nextOutputAt = ratio
                return
            }
            while (nextOutputAt <= sourceIndex.toDouble()) {
                val fraction = (nextOutputAt - (sourceIndex - 1).toDouble()).coerceIn(0.0, 1.0).toFloat()
                append((prev + (sample - prev) * fraction).coerceIn(-1f, 1f))
                nextOutputAt += ratio
            }
            previous = sample
            sourceIndex++
        }

        private fun append(value: Float) {
            require(count < maxOutputSamples) { "解码后的音频超过时长限制" }
            if (count == values.size) values = values.copyOf((values.size * 2).coerceAtMost(maxOutputSamples))
            values[count++] = value
        }

        fun toArray(): FloatArray = values.copyOf(count)
    }
}
