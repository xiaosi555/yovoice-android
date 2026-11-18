package com.xiaosi555.yovoice

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decode a media audio track, downmix/upmix to one or two channels, and resample to float PCM. */
object AndroidAudioDecoder {
    fun decodeTo16kMono(context: Context, uri: Uri, maxDurationSeconds: Int = 600): FloatArray =
        decodeToPcm(context, uri, 16_000, 1, maxDurationSeconds)

    fun decodeToPcm(
        context: Context,
        uri: Uri,
        targetSampleRate: Int,
        targetChannels: Int,
        maxDurationSeconds: Int = 600
    ): FloatArray {
        require(targetSampleRate in 8_000..96_000) { "目标采样率超出支持范围" }
        require(targetChannels in 1..2) { "目标声道数仅支持单声道或立体声" }
        require(maxDurationSeconds in 1..600) { "音频时长限制必须在 1 到 600 秒之间" }
        val maxOutputSamples = targetSampleRate * targetChannels * maxDurationSeconds
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
            val selectedFormat = audioFormat ?: error("无法读取音轨格式")
            val duration = runCatching { selectedFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            require(duration <= maxDurationSeconds.toLong() * 1_000_000L || duration == 0L) {
                "音频超过 " + maxDurationSeconds + " 秒限制，请先裁剪文件"
            }
            extractor.selectTrack(audioTrack)
            val mime = selectedFormat.getString(MediaFormat.KEY_MIME) ?: error("音频格式缺少 MIME")
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(selectedFormat, null, null, 0)
            decoder.start()

            var inputEnded = false
            var outputEnded = false
            var outputRate = selectedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannels = selectedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            val collector = ResampledPcmCollector(outputRate, targetSampleRate, targetChannels, maxOutputSamples)
            // Reuse frame buffers: allocating arrays for every PCM frame causes heavy GC on long recordings.
            val sourceFrame = FloatArray(8)
            val mappedFrame = FloatArray(targetChannels)
            var idleLoops = 0

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: error("无法读取解码输入缓冲区")
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleLoops++
                        if (inputEnded && idleLoops > 500) error("音频解码超时")
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
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
                        val buffer = decoder.getOutputBuffer(outputIndex)
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
                            require(outputChannels in 1..8) { "音频声道数异常：$outputChannels" }
                            val frames = pcm.remaining() / (bytesPerSample * outputChannels)
                            for (frame in 0 until frames) {
                                for (channel in 0 until outputChannels) {
                                    sourceFrame[channel] = when (outputEncoding) {
                                        AudioFormat.ENCODING_PCM_FLOAT -> pcm.float.coerceIn(-1f, 1f)
                                        AudioFormat.ENCODING_PCM_16BIT -> pcm.short / 32768f
                                        else -> ((pcm.get().toInt() and 0xff) - 128) / 128f
                                    }
                                }
                                if (targetChannels == 1) {
                                    var sum = 0f
                                    for (channel in 0 until outputChannels) sum += sourceFrame[channel]
                                    mappedFrame[0] = sum / outputChannels
                                } else if (outputChannels == 1) {
                                    mappedFrame[0] = sourceFrame[0]
                                    mappedFrame[1] = sourceFrame[0]
                                } else {
                                    mappedFrame[0] = sourceFrame[0]
                                    mappedFrame[1] = sourceFrame[1]
                                }
                                collector.add(mappedFrame)
                            }
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                        decoder.releaseOutputBuffer(outputIndex, false)
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

    private class ResampledPcmCollector(
        initialRate: Int,
        private val targetRate: Int,
        private val targetChannels: Int,
        private val maxOutputSamples: Int
    ) {
        private var ratio = initialRate.coerceAtLeast(1).toDouble() / targetRate
        private var nextOutputAt = 0.0
        private var sourceIndex = 0L
        private val previous = FloatArray(targetChannels)
        private var hasPrevious = false
        private var values = FloatArray((targetRate * targetChannels).coerceAtMost(maxOutputSamples))
        private var count = 0

        fun updateSourceRate(rate: Int) {
            require(rate in 8_000..192_000) { "音频采样率异常：$rate Hz" }
            ratio = rate.toDouble() / targetRate
        }

        fun add(frame: FloatArray) {
            require(frame.size == targetChannels) { "PCM 声道布局错误" }
            if (!hasPrevious) {
                append(frame)
                for (channel in 0 until targetChannels) previous[channel] = frame[channel]
                hasPrevious = true
                sourceIndex = 0
                nextOutputAt = ratio
                return
            }
            while (nextOutputAt <= sourceIndex.toDouble()) {
                val fraction = (nextOutputAt - (sourceIndex - 1).toDouble()).coerceIn(0.0, 1.0).toFloat()
                appendInterpolated(previous, frame, fraction)
                nextOutputAt += ratio
            }
            for (channel in 0 until targetChannels) previous[channel] = frame[channel]
            sourceIndex++
        }

        private fun ensureCapacity() {
            require(count + targetChannels <= maxOutputSamples) { "解码后的音频超过时长限制" }
            val required = count + targetChannels
            if (required > values.size) {
                val nextSize = (values.size * 2).coerceAtLeast(required).coerceAtMost(maxOutputSamples)
                values = values.copyOf(nextSize)
            }
        }

        private fun append(frame: FloatArray) {
            ensureCapacity()
            for (channel in 0 until targetChannels) values[count++] = frame[channel]
        }

        private fun appendInterpolated(previousFrame: FloatArray, currentFrame: FloatArray, fraction: Float) {
            ensureCapacity()
            for (channel in 0 until targetChannels) {
                values[count++] = (previousFrame[channel] +
                    (currentFrame[channel] - previousFrame[channel]) * fraction).coerceIn(-1f, 1f)
            }
        }

        fun toArray(): FloatArray = values.copyOf(count)
    }
}
