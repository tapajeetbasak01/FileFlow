package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioConversionEngine(private val context: Context) {

    suspend fun convert(
        toolId: ToolId,
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (progress: Float, index: Int, message: String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()

        files.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            val base = FileHelper.sanitizeBaseName(item.displayName)
            val result = when (toolId) {
                ToolId.MP3_TO_WAV -> decodeUriToWav(
                    uri = item.uri,
                    sourceSizeBytes = item.sizeBytes,
                    baseName = base,
                    outputDir = outputDir,
                    onProgress = { p, msg ->
                        onProgress((idx + p) / files.size.toFloat(), idx + 1, msg)
                    }
                )
                ToolId.WAV_TO_MP3 -> encodeWavUriToMp3(
                    item = item,
                    bitrateKbps = options.audioBitrateKbps,
                    baseName = base,
                    outputDir = outputDir,
                    onProgress = { p, msg ->
                        onProgress((idx + p) / files.size.toFloat(), idx + 1, msg)
                    }
                )
                ToolId.AUDIO_COMPRESS -> compressAudio(
                    item = item,
                    bitrateKbps = options.audioBitrateKbps.coerceIn(64, 192),
                    baseName = "${base}_${options.audioBitrateKbps}k",
                    outputDir = outputDir,
                    onProgress = { p, msg ->
                        onProgress((idx + p) / files.size.toFloat(), idx + 1, msg)
                    }
                )
                ToolId.AUDIO_TO_M4A -> transcodeToM4a(
                    uri = item.uri,
                    sourceSizeBytes = item.sizeBytes,
                    baseName = base,
                    outputDir = outputDir,
                    clipStartMs = null,
                    clipEndMs = null,
                    onProgress = { p, msg ->
                        onProgress((idx + p) / files.size.toFloat(), idx + 1, msg)
                    }
                )
                ToolId.AUDIO_TRIM -> {
                    val startMs = (options.trimStartSec * 1000f).toLong().coerceAtLeast(0L)
                    val endMs = (options.trimEndSec * 1000f).toLong().coerceAtLeast(startMs + 500L)
                    transcodeToM4a(
                        uri = item.uri,
                        sourceSizeBytes = item.sizeBytes,
                        baseName = "${base}_trimmed",
                        outputDir = outputDir,
                        clipStartMs = startMs,
                        clipEndMs = endMs,
                        onProgress = { p, msg ->
                            onProgress((idx + p) / files.size.toFloat(), idx + 1, msg)
                        }
                    )
                }
                else -> throw IllegalArgumentException("Unsupported audio tool: $toolId")
            }
            outputs += result
        }

        return outputs
    }

    suspend fun decodeUriToWav(
        uri: Uri,
        sourceSizeBytes: Long,
        baseName: String,
        outputDir: File,
        onProgress: suspend (Float, String) -> Unit
    ): ConversionOutput {
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "wav")
        val pcmInfo = decodeAudioUriToWavFile(uri, outFile, onProgress)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "audio/wav",
            sizeBytes = outFile.length(),
            sourceSizeBytes = sourceSizeBytes,
            extraNote = "${pcmInfo.sampleRate} Hz • ${pcmInfo.channels}ch PCM WAV"
        )
    }

    private data class PcmSpec(val sampleRate: Int, val channels: Int, val dataBytes: Long)

    private suspend fun decodeAudioUriToWavFile(
        uri: Uri,
        wavOutFile: File,
        onProgress: suspend (Float, String) -> Unit
    ): PcmSpec {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalArgumentException("No audio track found in selected file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var totalPcmBytes = 0L
            FileOutputStream(wavOutFile).use { fos ->
                // Write placeholder 44-byte RIFF header; patched once PCM byte count is known
                fos.write(ByteArray(44))

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false
                var lastReportedProgress = -1f

                while (!sawOutputEos) {
                    coroutineContext.ensureActive()

                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inputBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()

                                if (durationUs > 0) {
                                    val p = (pts.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.95f)
                                    if (p - lastReportedProgress >= 0.05f) {
                                        lastReportedProgress = p
                                        onProgress(p, "Decoding audio to PCM (${(p * 100).toInt()}%)…")
                                    }
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = codec.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val chunk = ByteArray(bufferInfo.size)
                                outBuf.get(chunk)
                                fos.write(chunk)
                                totalPcmBytes += chunk.size
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }
            }

            if (totalPcmBytes <= 0L) {
                throw IllegalStateException("Decoded audio produced empty PCM stream.")
            }

            patchWavHeader(wavOutFile, sampleRate, channels, 16, totalPcmBytes)
            onProgress(1f, "Audio decoded")
            return PcmSpec(sampleRate, channels, totalPcmBytes)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private suspend fun encodeWavUriToMp3(
        item: SelectedFileItem,
        bitrateKbps: Int,
        baseName: String,
        outputDir: File,
        onProgress: suspend (Float, String) -> Unit
    ): ConversionOutput {
        val tempWav = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
        try {
            return encodeWavFileToMp3(
                wavFile = tempWav,
                sourceSizeBytes = item.sizeBytes,
                bitrateKbps = bitrateKbps,
                baseName = baseName,
                outputDir = outputDir,
                onProgress = onProgress
            )
        } finally {
            tempWav.delete()
        }
    }

    private suspend fun compressAudio(
        item: SelectedFileItem,
        bitrateKbps: Int,
        baseName: String,
        outputDir: File,
        onProgress: suspend (Float, String) -> Unit
    ): ConversionOutput {
        val ext = item.extension.lowercase()
        if (ext == "wav") {
            return encodeWavUriToMp3(item, bitrateKbps, baseName, outputDir, onProgress)
        }
        val tempWav = File.createTempFile("ff_dec_", ".wav", FileHelper.getTempWorkDir(context))
        try {
            decodeAudioUriToWavFile(item.uri, tempWav) { p, msg ->
                onProgress(p * 0.45f, msg)
            }
            return encodeWavFileToMp3(
                wavFile = tempWav,
                sourceSizeBytes = item.sizeBytes,
                bitrateKbps = bitrateKbps,
                baseName = baseName,
                outputDir = outputDir,
                onProgress = { p, msg ->
                    onProgress(0.45f + p * 0.55f, msg)
                }
            )
        } finally {
            tempWav.delete()
        }
    }

    suspend fun encodeWavFileToMp3(
        wavFile: File,
        sourceSizeBytes: Long,
        bitrateKbps: Int,
        baseName: String,
        outputDir: File,
        onProgress: suspend (Float, String) -> Unit
    ): ConversionOutput {
        val wavInfo = parseWavHeader(wavFile)
        if (wavInfo.audioFormat != 1) {
            throw IllegalArgumentException("Only uncompressed PCM WAV files are supported (format=${wavInfo.audioFormat}).")
        }
        if (wavInfo.bitsPerSample != 16) {
            throw IllegalArgumentException("Only 16-bit PCM WAV files are supported (got ${wavInfo.bitsPerSample}-bit).")
        }

        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "mp3")
        val channels = wavInfo.channels.coerceIn(1, 2)
        val lame = createWiredLame()
        val gf = lame.lame_init()
        gf.in_samplerate = wavInfo.sampleRate
        gf.out_samplerate = wavInfo.sampleRate
        gf.num_channels = channels
        gf.brate = bitrateKbps.coerceIn(64, 320)
        gf.quality = 2
        gf.write_id3tag_automatic = false
        gf.bWriteVbrTag = false
        gf.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO

        if (lame.lame_init_params(gf) < 0) {
            lame.lame_close(gf)
            throw IllegalStateException("Failed to initialize MP3 encoder parameters.")
        }

        try {
            val samplesPerChunk = 8192
            val bytesPerFrame = 2 * channels
            val readBuf = ByteArray(samplesPerChunk * bytesPerFrame)
            val left = IntArray(samplesPerChunk)
            val right = IntArray(samplesPerChunk)
            val mp3Buf = ByteArray((1.25 * samplesPerChunk + 7200).toInt())

            var processedDataBytes = 0L
            var lastReported = -1f

            FileInputStream(wavFile).use { fis ->
                fis.skip(wavInfo.dataOffset)
                FileOutputStream(outFile).use { fos ->
                    while (processedDataBytes < wavInfo.dataSize) {
                        coroutineContext.ensureActive()
                        val remaining = (wavInfo.dataSize - processedDataBytes).coerceAtMost(readBuf.size.toLong()).toInt()
                        val read = fis.read(readBuf, 0, remaining)
                        if (read <= 0) break
                        processedDataBytes += read

                        val sampleCount = read / bytesPerFrame
                        val bb = ByteBuffer.wrap(readBuf, 0, sampleCount * bytesPerFrame)
                            .order(ByteOrder.LITTLE_ENDIAN)

                        if (channels == 1) {
                            for (i in 0 until sampleCount) {
                                val s = bb.short.toInt() shl 16
                                left[i] = s
                                right[i] = s
                            }
                        } else {
                            for (i in 0 until sampleCount) {
                                left[i] = bb.short.toInt() shl 16
                                right[i] = bb.short.toInt() shl 16
                            }
                        }

                        val encoded = lame.lame_encode_buffer_int(gf, left, right, sampleCount, mp3Buf, 0, mp3Buf.size)
                        if (encoded > 0) {
                            fos.write(mp3Buf, 0, encoded)
                        }

                        val p = (processedDataBytes.toFloat() / wavInfo.dataSize.coerceAtLeast(1L).toFloat())
                            .coerceIn(0f, 0.98f)
                        if (p - lastReported >= 0.05f) {
                            lastReported = p
                            onProgress(p, "Encoding MP3 at ${bitrateKbps} kbps (${(p * 100).toInt()}%)…")
                        }
                    }

                    val flushed = lame.lame_encode_flush(gf, mp3Buf, 0, mp3Buf.size)
                    if (flushed > 0) {
                        fos.write(mp3Buf, 0, flushed)
                    }
                }
            }

            onProgress(1f, "MP3 encoded")
            return ConversionOutput(
                outputFilePath = outFile.absolutePath,
                fileName = outFile.name,
                mimeType = "audio/mpeg",
                sizeBytes = outFile.length(),
                sourceSizeBytes = sourceSizeBytes,
                extraNote = "${bitrateKbps} kbps MP3 • ${wavInfo.sampleRate} Hz"
            )
        } finally {
            lame.lame_close(gf)
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun transcodeToM4a(
        uri: Uri,
        sourceSizeBytes: Long,
        baseName: String,
        outputDir: File,
        clipStartMs: Long?,
        clipEndMs: Long?,
        onProgress: suspend (Float, String) -> Unit
    ): ConversionOutput {
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "m4a")
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
        if (clipStartMs != null && clipEndMs != null && clipEndMs > clipStartMs) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs)
                    .setEndPositionMs(clipEndMs)
                    .build()
            )
        }
        val editedItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveVideo(true)
            .build()

        runTransformerExport(
            context = context,
            editedMediaItem = editedItem,
            outputPath = outFile.absolutePath,
            audioMimeType = MimeTypes.AUDIO_AAC,
            videoMimeType = null,
            onProgress = { pct ->
                onProgress(pct, "Encoding AAC audio (${(pct * 100).toInt()}%)…")
            }
        )

        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "audio/mp4",
            sizeBytes = outFile.length(),
            sourceSizeBytes = sourceSizeBytes,
            extraNote = "AAC M4A"
        )
    }

    private data class WavHeaderInfo(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Long
    )

    private fun parseWavHeader(file: File): WavHeaderInfo {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 44) throw IllegalArgumentException("File is too small to be a valid WAV.")
            val header = ByteArray(12)
            raf.readFully(header)
            val riff = String(header, 0, 4, Charsets.US_ASCII)
            val wave = String(header, 8, 4, Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") {
                throw IllegalArgumentException("Invalid WAV header (missing RIFF/WAVE signature).")
            }

            var audioFormat = -1
            var channels = -1
            var sampleRate = -1
            var bitsPerSample = -1
            var dataOffset = -1L
            var dataSize = -1L

            val chunkHeader = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                raf.readFully(chunkHeader)
                val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

                when (chunkId) {
                    "fmt " -> {
                        val fmtBytes = ByteArray(chunkSize.toInt().coerceAtLeast(16))
                        raf.readFully(fmtBytes)
                        val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = bb.short.toInt() and 0xFFFF
                        channels = bb.short.toInt() and 0xFFFF
                        sampleRate = bb.int
                        bb.int // byteRate
                        bb.short // blockAlign
                        bitsPerSample = bb.short.toInt() and 0xFFFF
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = minOf(chunkSize, raf.length() - dataOffset)
                        break
                    }
                    else -> {
                        val skip = if (chunkSize % 2L == 1L) chunkSize + 1L else chunkSize
                        raf.seek((raf.filePointer + skip).coerceAtMost(raf.length()))
                    }
                }
            }

            if (audioFormat < 0 || dataOffset < 0) {
                throw IllegalArgumentException("Malformed WAV file (missing fmt or data chunk).")
            }
            return WavHeaderInfo(audioFormat, channels, sampleRate, bitsPerSample, dataOffset, dataSize)
        }
    }

    private fun patchWavHeader(
        file: File,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmDataBytes: Long
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val totalDataLen = (pcmDataBytes + 36).toInt()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1.toShort()) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataBytes.toInt())
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    private fun createWiredLame(): Lame {
        val lame = Lame()
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)
        return lame
    }

    companion object {
        @OptIn(UnstableApi::class)
        suspend fun runTransformerExport(
            context: Context,
            editedMediaItem: EditedMediaItem,
            outputPath: String,
            audioMimeType: String?,
            videoMimeType: String?,
            configureBuilder: (Transformer.Builder) -> Unit = {},
            onProgress: suspend (Float) -> Unit
        ): ExportResult = coroutineScope {
            val progressFlow = MutableStateFlow(0f)
            val progressCollector = launch {
                progressFlow.collect { pct ->
                    onProgress(pct)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.post {
                        val builder = Transformer.Builder(context)
                        if (audioMimeType != null) builder.setAudioMimeType(audioMimeType)
                        if (videoMimeType != null) builder.setVideoMimeType(videoMimeType)
                        configureBuilder(builder)

                        var transformerRef: Transformer? = null
                        var finished = false
                        val progressHolder = ProgressHolder()

                        val listener = object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                finished = true
                                progressFlow.value = 1f
                                if (cont.isActive) cont.resume(exportResult)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                finished = true
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        }

                        val transformer = builder.addListener(listener).build()
                        transformerRef = transformer

                        cont.invokeOnCancellation {
                            finished = true
                            mainHandler.post { runCatching { transformerRef?.cancel() } }
                        }

                        try {
                            transformer.start(editedMediaItem, outputPath)
                            val poll = object : Runnable {
                                override fun run() {
                                    if (finished || !cont.isActive) return
                                    val state = transformer.getProgress(progressHolder)
                                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                        progressFlow.value = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                                    }
                                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && !finished) {
                                        mainHandler.postDelayed(this, 250L)
                                    }
                                }
                            }
                            mainHandler.postDelayed(poll, 250L)
                        } catch (e: Exception) {
                            finished = true
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
            } finally {
                progressCollector.cancel()
            }
        }
    }
}
