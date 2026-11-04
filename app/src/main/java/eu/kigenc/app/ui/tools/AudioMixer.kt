package eu.kigenc.app.ui.tools

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fügt einem Video Hintergrundmusik hinzu (komplett auf dem Gerät).
 * AAC-Musik (z. B. .m4a) wird direkt übernommen, andere Formate (z. B. MP3) werden nach AAC umgewandelt.
 * Die Musik wird auf die Videolänge gekürzt und am Ende ausgeblendet (bei Umwandlung).
 */
object AudioMixer {

    suspend fun addMusic(context: Context, video: File, music: Uri): File = withContext(Dispatchers.Default) {
        val out = File(video.parentFile, video.nameWithoutExtension + "_musik.mp4")
        val videoEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val audioEx = MediaExtractor().apply { setDataSource(context, music, null) }
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            val vTrack = (0 until videoEx.trackCount).first { videoEx.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
            val vFormat = videoEx.getTrackFormat(vTrack)
            val durationUs = vFormat.getLong(MediaFormat.KEY_DURATION)
            val aTrack = (0 until audioEx.trackCount).firstOrNull {
                audioEx.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: error("Keine Tonspur in der Musikdatei gefunden")
            val aFormat = audioEx.getTrackFormat(aTrack)
            val aMime = aFormat.getString(MediaFormat.KEY_MIME).orEmpty()

            // Audio vorbereiten: entweder direkt kopieren (AAC) oder umwandeln
            val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>
            val outAudioFormat: MediaFormat
            audioEx.selectTrack(aTrack)
            if (aMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                outAudioFormat = aFormat
                samples = copySamples(audioEx, durationUs)
            } else {
                val (fmt, list) = transcodeToAac(audioEx, aFormat, durationUs)
                outAudioFormat = fmt
                samples = list
            }

            val dstVideo = muxer.addTrack(vFormat)
            val dstAudio = muxer.addTrack(outAudioFormat)
            muxer.start()
            muxerStarted = true

            // Video unverändert kopieren
            videoEx.selectTrack(vTrack)
            val buf = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = videoEx.readSampleData(buf, 0)
                if (size < 0) break
                val flags = if (videoEx.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, videoEx.sampleTime, flags)
                muxer.writeSampleData(dstVideo, buf, info)
                videoEx.advance()
            }
            samples.forEach { (bytes, i) -> muxer.writeSampleData(dstAudio, ByteBuffer.wrap(bytes), i) }
        } finally {
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
            videoEx.release()
            audioEx.release()
        }
        video.delete()
        out
    }

    private fun copySamples(ex: MediaExtractor, maxUs: Long): List<Pair<ByteArray, MediaCodec.BufferInfo>> {
        val list = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val buf = ByteBuffer.allocate(1024 * 1024)
        while (true) {
            val size = ex.readSampleData(buf, 0)
            if (size < 0 || ex.sampleTime > maxUs) break
            val bytes = ByteArray(size)
            buf.position(0)
            buf.get(bytes, 0, size)
            val info = MediaCodec.BufferInfo().apply { set(0, size, ex.sampleTime, MediaCodec.BUFFER_FLAG_KEY_FRAME) }
            list += bytes to info
            ex.advance()
        }
        return list
    }

    /** Dekodiert beliebiges Audio zu PCM und kodiert es als AAC (mit Ausblenden in der letzten Sekunde). */
    private fun transcodeToAac(
        ex: MediaExtractor,
        format: MediaFormat,
        maxUs: Long,
    ): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>> {
        // 1) Dekodieren
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()
        val pcm = ByteArrayOutputStream()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)!!
                        val size = ex.readSampleData(inBuf, 0)
                        if (size < 0 || ex.sampleTime > maxUs) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk)
                        pcm.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
        }
        val pcmBytes = pcm.toByteArray()
        applyFadeOut(pcmBytes, sampleRate, channels)

        // 2) Kodieren als AAC
        val outFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val result = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var encodedFormat: MediaFormat = outFormat
        val bytesPerSecond = sampleRate.toLong() * channels * 2
        var offset = 0
        var eosSent = false
        try {
            while (true) {
                if (!eosSent) {
                    val inIdx = encoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val n = minOf(inBuf.remaining(), pcmBytes.size - offset)
                        val pts = offset * 1_000_000L / bytesPerSecond
                        if (n <= 0) {
                            encoder.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSent = true
                        } else {
                            inBuf.put(pcmBytes, offset, n)
                            encoder.queueInputBuffer(inIdx, 0, n, pts, 0)
                            offset += n
                        }
                    }
                }
                val outIdx = encoder.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encodedFormat = encoder.outputFormat
                } else if (outIdx >= 0) {
                    val outBuf = encoder.getOutputBuffer(outIdx)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (outBuf != null && info.size > 0 && !isConfig) {
                        val bytes = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(bytes)
                        result += bytes to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, 0) }
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
        }
        return encodedFormat to result
    }

    private fun applyFadeOut(pcm: ByteArray, sampleRate: Int, channels: Int) {
        val frameBytes = 2 * channels
        val fadeFrames = sampleRate // 1 Sekunde
        val totalFrames = pcm.size / frameBytes
        if (totalFrames <= fadeFrames) return
        val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val start = totalFrames - fadeFrames
        for (f in start until totalFrames) {
            val gain = (totalFrames - f).toFloat() / fadeFrames
            for (c in 0 until channels) {
                val pos = f * frameBytes + c * 2
                val v = bb.getShort(pos)
                bb.putShort(pos, (v * gain).toInt().toShort())
            }
        }
    }
}
