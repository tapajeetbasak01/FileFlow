package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.muxer.WebmMuxer
import com.google.common.collect.ImmutableList
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoConversionEngine(
    private val context: Context,
    private val audioEngine: AudioConversionEngine
) {

    suspend fun convert(
        toolId: ToolId,
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (progress: Float, index: Int, message: String) -> Unit
    ): List<ConversionOutput> {
        return when (toolId) {
            ToolId.MP4_TO_WEBM -> convertBatchToWebm(files, options, outputDir, onProgress)
            ToolId.VIDEO_COMPRESS -> compressBatchVideos(files, options, outputDir, onProgress)
            ToolId.EXTRACT_AUDIO -> extractAudioBatch(files, options, outputDir, onProgress)
            ToolId.VIDEO_TRIM -> listOf(trimVideo(files.first(), options, outputDir, onProgress))
            ToolId.VIDEO_TO_FRAMES -> extractFrames(files.first(), options, outputDir, onProgress)
            else -> throw IllegalArgumentException("Unsupported video tool: $toolId")
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun convertBatchToWebm(
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()
        val videoCodec = pickSupportedWebmVideoMime()
        val audioCodec = pickSupportedWebmAudioMime()

        files.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            val base = FileHelper.sanitizeBaseName(item.displayName)
            val outFile = FileHelper.uniqueOutputFile(outputDir, base, "webm")

            val mediaItem = MediaItem.fromUri(item.uri)
            val edited = EditedMediaItem.Builder(mediaItem).build()

            val videoSettings = VideoEncoderSettings.Builder()
                .setBitrate(options.videoQualityPreset.bitrateBps)
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setEnableFallback(true)
                .build()

            runTransformerWithProgress(
                editedMediaItem = edited,
                outputPath = outFile.absolutePath,
                configureBuilder = { builder ->
                    builder.setMuxerFactory(WebmMuxerFactory())
                    builder.setVideoMimeType(videoCodec)
                    builder.setAudioMimeType(audioCodec)
                    builder.setEncoderFactory(encoderFactory)
                },
                onProgress = { pct ->
                    onProgress(
                        (idx + pct) / files.size.toFloat(),
                        idx + 1,
                        "Transcoding ${item.displayName} → WEBM (${(pct * 100).toInt()}%)…"
                    )
                }
            )

            outputs += ConversionOutput(
                outputFilePath = outFile.absolutePath,
                fileName = outFile.name,
                mimeType = "video/webm",
                sizeBytes = outFile.length(),
                sourceSizeBytes = item.sizeBytes,
                extraNote = "WEBM (${videoCodec.substringAfter('/')})"
            )
        }
        return outputs
    }

    @OptIn(UnstableApi::class)
    private suspend fun compressBatchVideos(
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()
        val preset = options.videoQualityPreset

        files.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            val base = FileHelper.sanitizeBaseName(item.displayName)
            val outFile = FileHelper.uniqueOutputFile(outputDir, "${base}_compressed", "mp4")

            val videoEffects = mutableListOf<Effect>()
            preset.targetHeight?.let { h ->
                videoEffects += Presentation.createForHeight(h)
            }

            val edited = EditedMediaItem.Builder(MediaItem.fromUri(item.uri))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(preset.bitrateBps)
                        .build()
                )
                .setEnableFallback(true)
                .build()

            runTransformerWithProgress(
                editedMediaItem = edited,
                outputPath = outFile.absolutePath,
                configureBuilder = { builder ->
                    builder.setVideoMimeType(MimeTypes.VIDEO_H264)
                    builder.setAudioMimeType(MimeTypes.AUDIO_AAC)
                    builder.setEncoderFactory(encoderFactory)
                },
                onProgress = { pct ->
                    onProgress(
                        (idx + pct) / files.size.toFloat(),
                        idx + 1,
                        "Compressing ${item.displayName} (${(pct * 100).toInt()}%)…"
                    )
                }
            )

            outputs += ConversionOutput(
                outputFilePath = outFile.absolutePath,
                fileName = outFile.name,
                mimeType = "video/mp4",
                sizeBytes = outFile.length(),
                sourceSizeBytes = item.sizeBytes,
                extraNote = preset.label
            )
        }
        return outputs
    }

    private suspend fun extractAudioBatch(
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()
        val format = options.extractedAudioFormat.uppercase()

        files.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            val base = FileHelper.sanitizeBaseName(item.displayName) + "_audio"
            val result = when (format) {
                "WAV" -> audioEngine.decodeUriToWav(
                    uri = item.uri,
                    sourceSizeBytes = item.sizeBytes,
                    baseName = base,
                    outputDir = outputDir,
                    onProgress = { p, msg ->
                        onProgress((idx + p) / files.size.toFloat(), idx + 1, msg)
                    }
                )
                "MP3" -> {
                    val tempWav = File.createTempFile("ff_v_aud_", ".wav", FileHelper.getTempWorkDir(context))
                    try {
                        audioEngine.decodeUriToWav(
                            uri = item.uri,
                            sourceSizeBytes = item.sizeBytes,
                            baseName = base,
                            outputDir = tempWav.parentFile!!,
                            onProgress = { p, msg ->
                                onProgress((idx + p * 0.45f) / files.size.toFloat(), idx + 1, msg)
                            }
                        ).let { wavOut ->
                            val actualWav = File(wavOut.outputFilePath)
                            try {
                                audioEngine.encodeWavFileToMp3(
                                    wavFile = actualWav,
                                    sourceSizeBytes = item.sizeBytes,
                                    bitrateKbps = options.audioBitrateKbps,
                                    baseName = base,
                                    outputDir = outputDir,
                                    onProgress = { p, msg ->
                                        onProgress((idx + 0.45f + p * 0.55f) / files.size.toFloat(), idx + 1, msg)
                                    }
                                )
                            } finally {
                                actualWav.delete()
                            }
                        }
                    } finally {
                        tempWav.delete()
                    }
                }
                else -> audioEngine.transcodeToM4a(
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
            }
            outputs += result
        }
        return outputs
    }

    @OptIn(UnstableApi::class)
    private suspend fun trimVideo(
        item: SelectedFileItem,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): ConversionOutput {
        val startMs = (options.trimStartSec * 1000f).toLong().coerceAtLeast(0L)
        val endMs = (options.trimEndSec * 1000f).toLong().coerceAtLeast(startMs + 500L)
        val base = FileHelper.sanitizeBaseName(item.displayName)
        val outFile = FileHelper.uniqueOutputFile(outputDir, "${base}_trimmed", "mp4")

        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val edited = EditedMediaItem.Builder(mediaItem).build()

        runTransformerWithProgress(
            editedMediaItem = edited,
            outputPath = outFile.absolutePath,
            configureBuilder = {},
            onProgress = { pct ->
                onProgress(pct, 1, "Trimming video (${(pct * 100).toInt()}%)…")
            }
        )

        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "video/mp4",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = "${options.trimStartSec.toInt()}s – ${options.trimEndSec.toInt()}s clip"
        )
    }

    private suspend fun extractFrames(
        item: SelectedFileItem,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val retriever = MediaMetadataRetriever()
        val outputs = mutableListOf<ConversionOutput>()
        val count = options.frameCount.coerceIn(1, 20)
        val useJpg = options.imageOutputFormat.equals("JPG", ignoreCase = true)
        val ext = if (useJpg) "jpg" else "png"
        val mime = if (useJpg) "image/jpeg" else "image/png"
        val format = if (useJpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val base = FileHelper.sanitizeBaseName(item.displayName)

        try {
            retriever.setDataSource(context, item.uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 1000L

            for (i in 0 until count) {
                coroutineContext.ensureActive()
                val timeUs = if (count == 1) {
                    (durationMs * 1000L) / 2L
                } else {
                    ((durationMs * i) / (count - 1).coerceAtLeast(1)) * 1000L
                }
                onProgress(i.toFloat() / count.toFloat(), 1, "Capturing frame ${i + 1} of $count…")
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    try {
                        val outFile = FileHelper.uniqueOutputFile(outputDir, "${base}_frame_${i + 1}", ext)
                        FileOutputStream(outFile).use { fos ->
                            frame.compress(format, options.quality.coerceIn(70, 100), fos)
                        }
                        outputs += ConversionOutput(
                            outputFilePath = outFile.absolutePath,
                            fileName = outFile.name,
                            mimeType = mime,
                            sizeBytes = outFile.length(),
                            sourceSizeBytes = item.sizeBytes,
                            extraNote = "Frame at ${(timeUs / 1_000_000.0).let { "%.1fs".format(it) }}"
                        )
                    } finally {
                        frame.recycle()
                    }
                }
            }

            if (outputs.isEmpty()) {
                throw IllegalStateException("Could not capture frames from ${item.displayName}.")
            }
            onProgress(1f, 1, "Captured ${outputs.size} frames")
            return outputs
        } finally {
            runCatching { retriever.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerWithProgress(
        editedMediaItem: EditedMediaItem,
        outputPath: String,
        configureBuilder: (Transformer.Builder) -> Unit,
        onProgress: suspend (Float) -> Unit
    ): ExportResult {
        return AudioConversionEngine.runTransformerExport(
            context = context,
            editedMediaItem = editedMediaItem,
            outputPath = outputPath,
            audioMimeType = null,
            videoMimeType = null,
            configureBuilder = configureBuilder,
            onProgress = onProgress
        )
    }

    private fun pickSupportedWebmVideoMime(): String {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val hasVp9 = infos.any { it.isEncoder && MimeTypes.VIDEO_VP9 in it.supportedTypes }
        return if (hasVp9) MimeTypes.VIDEO_VP9 else MimeTypes.VIDEO_VP8
    }

    private fun pickSupportedWebmAudioMime(): String {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val hasOpus = infos.any { it.isEncoder && MimeTypes.AUDIO_OPUS in it.supportedTypes }
        return if (hasOpus) MimeTypes.AUDIO_OPUS else MimeTypes.AUDIO_VORBIS
    }

    @OptIn(UnstableApi::class)
    private class WebmMuxerFactory : Muxer.Factory {
        override fun create(path: String): Muxer =
            WebmMuxer.Builder(SeekableMuxerOutput.of(path)).build()

        override fun getSupportedSampleMimeTypes(trackType: @C.TrackType Int): ImmutableList<String> =
            when (trackType) {
                C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8)
                C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS)
                else -> ImmutableList.of()
            }
    }
}
