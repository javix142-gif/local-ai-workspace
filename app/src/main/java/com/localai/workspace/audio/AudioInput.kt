package com.localai.workspace.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Bounded PCM WAV only at the worker boundary. The SDK's miniaudio decoder handles resampling. */
object WavInput {
    const val MAX_BYTES = 12 * 1024 * 1024
    data class Info(val sampleRate: Int, val channels: Int, val bits: Int, val dataBytes: Int) {
        val durationMs: Long get() = dataBytes.toLong() * 1000 / (sampleRate * channels * (bits / 8))
    }
    fun inspect(file: File): Info {
        require(file.isFile && file.length() in 44..MAX_BYTES.toLong()) { "Invalid or oversized prepared audio" }
        RandomAccessFile(file, "r").use { f ->
            fun tag(): String { val b = ByteArray(4); f.readFully(b); return b.toString(Charsets.US_ASCII) }
            fun int() = Integer.reverseBytes(f.readInt())
            fun short() = java.lang.Short.reverseBytes(f.readShort()).toInt() and 65535
            require(tag() == "RIFF"); val declared = int().toLong() + 8; require(declared == file.length()); require(tag() == "WAVE")
            var format: Info? = null; var data = 0
            while (f.filePointer + 8 <= file.length()) {
                val id = tag(); val n = int(); val start = f.filePointer
                require(n >= 0 && start + n <= file.length()) { "Truncated WAV" }
                if (id == "fmt ") {
                    require(n >= 16); val encoding = short(); val channels = short(); val rate = int(); val bytesPerSecond = int(); val align = short(); val bits = short()
                    require(encoding == 1 && channels in 1..2 && rate in 8000..48000 && bits == 16 && align == channels * 2 && bytesPerSecond == rate * align) { "Audio must decode to mono/stereo 16-bit PCM WAV" }
                    format = Info(rate, channels, bits, 0)
                } else if (id == "data") { require(data == 0); data = n }
                f.seek(start + n + (n % 2))
            }
            return requireNotNull(format) { "WAV format missing" }.copy(dataBytes = data).also { require(data > 0 && data % (it.channels * 2) == 0 && it.durationMs in 1..30000) { "Audio must be between 1 ms and 30 seconds" } }
        }
    }
    fun validate(filesDir: File, path: String): Pair<File, Info> {
        val root = File(filesDir, "audio-attachments").canonicalFile; val file = File(path).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Audio outside private storage" }
        return file to inspect(file)
    }
    fun header(rate: Int, channels: Int, bytes: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort()); putInt(rate); putInt(rate * channels * 2); putShort((channels * 2).toShort()); putShort(16); put("data".toByteArray()); putInt(bytes)
    }.array()
}

class AudioPreprocessor(private val context: Context, private val outputDirectory: File? = null) {
    suspend fun prepare(uri: Uri): File = withTimeout(25000) { runInterruptible(Dispatchers.IO) { decode(uri) } }
    private fun decode(uri: Uri): File {
        val root = (outputDirectory ?: File(context.filesDir, "audio-attachments")).apply { mkdirs() }
        val input = File(root, "${UUID.randomUUID()}.input"); val output = File(root, "${UUID.randomUUID()}.wav")
        val deadline = System.nanoTime() + 20_000_000_000L
        fun checkTime() { if (Thread.currentThread().isInterrupted) throw InterruptedException(); require(System.nanoTime() < deadline) { "Audio preparation timed out" } }
        try {
            context.contentResolver.openInputStream(uri)?.use { src -> input.outputStream().use { dst -> val buffer = ByteArray(16384); var total = 0; while(true) { checkTime(); val n = src.read(buffer); if(n < 0) break; total += n; require(total <= 20 * 1024 * 1024) { "Audio input exceeds 20 MiB" }; dst.write(buffer, 0, n) } } } ?: error("Cannot read audio")
            // Direct validated WAV avoids decoder overhead. Other formats use Android codecs locally.
            if (runCatching { WavInput.inspect(input) }.isSuccess) { check(input.renameTo(output)); return output }
            val extractor = MediaExtractor(); var codec: MediaCodec? = null
            try {
                extractor.setDataSource(input.path)
                val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: error("No audio track")
                val format = extractor.getTrackFormat(track); val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                extractor.selectTrack(track)
                codec = MediaCodec.createDecoderByType(mime); codec.configure(format, null, null, 0); codec.start()
                var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE); var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT; var sentEnd = false; var ended = false; var bytes = 0
                val info = MediaCodec.BufferInfo()
                RandomAccessFile(output, "rw").use { dst ->
                    dst.write(ByteArray(44))
                    while (!ended) {
                        checkTime()
                        if (!sentEnd) { val index = codec.dequeueInputBuffer(10000); if (index >= 0) {
                            val b = requireNotNull(codec.getInputBuffer(index)); val n = extractor.readSampleData(b, 0)
                            if (n < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); sentEnd = true }
                            else { codec.queueInputBuffer(index, 0, n, extractor.sampleTime, 0); extractor.advance() }
                        } }
                        val index = codec.dequeueOutputBuffer(info, 10000)
                        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val f = codec.outputFormat; rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        } else if (index >= 0) {
                            require(rate in 8000..48000 && channels in 1..2 && encoding == AudioFormat.ENCODING_PCM_16BIT) { "Unsupported decoded audio format" }
                            val b = requireNotNull(codec.getOutputBuffer(index)); b.position(info.offset); b.limit(info.offset + info.size)
                            bytes += info.size; require(bytes <= WavInput.MAX_BYTES - 44 && bytes.toLong() * 1000 <= 30000L * rate * channels * 2) { "Audio exceeds 30 seconds" }
                            val buffer = ByteArray(minOf(16384, info.size)); while (b.hasRemaining()) { val n = minOf(buffer.size, b.remaining()); b.get(buffer, 0, n); dst.write(buffer, 0, n) }
                            ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0; codec.releaseOutputBuffer(index, false)
                        }
                    }
                    dst.seek(0); dst.write(WavInput.header(rate, channels, bytes))
                }
                WavInput.inspect(output); return output
            } finally { runCatching { codec?.stop() }; codec?.release(); extractor.release() }
        } catch (failure: Throwable) { output.delete(); throw failure } finally { input.delete() }
    }
}
