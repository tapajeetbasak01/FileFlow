package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ArchiveConversionEngine
import com.example.engine.AudioConversionEngine
import com.example.engine.DocumentConversionEngine
import com.example.engine.ImageConversionEngine
import com.example.engine.PdfConversionEngine
import com.example.model.ConversionOptions
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import com.example.model.ToolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name is FileFlow`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("FileFlow", context.getString(R.string.app_name))
    }

    @Test
    fun `pdf page range parser handles ranges and duplicates`() {
        val pages = PdfConversionEngine.parsePageRangeExpression("1, 3-5, 4, 12", maxPage = 8)
        assertEquals(listOf(1, 3, 4, 5), pages)
    }

    @Test
    fun `tool suggestions match file extensions`() {
        val pdfSuggestions = ToolRegistry.suggestToolsForFile("report.pdf", "application/pdf")
        assertTrue(pdfSuggestions.any { it.toolId == ToolId.MERGE_PDF })
        assertTrue(pdfSuggestions.any { it.toolId == ToolId.PDF_TO_IMAGES })

        val mp4Suggestions = ToolRegistry.suggestToolsForFile("clip.mp4", "video/mp4")
        assertTrue(mp4Suggestions.any { it.toolId == ToolId.MP4_TO_WEBM })
        assertTrue(mp4Suggestions.any { it.toolId == ToolId.EXTRACT_AUDIO })
    }

    @Test
    fun `image engine converts png to jpg`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_img").apply { mkdirs() }
        val pngFile = File(tempDir, "sample.png")
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        FileOutputStream(pngFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val engine = ImageConversionEngine(context)
        val outDir = File(tempDir, "out").apply { mkdirs() }
        val outputs = engine.convert(
            toolId = ToolId.PNG_TO_JPG,
            files = listOf(
                SelectedFileItem(
                    uri = Uri.fromFile(pngFile),
                    displayName = "sample.png",
                    sizeBytes = pngFile.length(),
                    mimeType = "image/png",
                    extension = "png"
                )
            ),
            options = ConversionOptions(quality = 85),
            outputDir = outDir
        ) { _, _, _ -> }

        assertEquals(1, outputs.size)
        val out = File(outputs.first().outputFilePath)
        assertTrue(out.exists() && out.length() > 0L)
        assertTrue(out.name.endsWith(".jpg"))
    }

    @Test
    fun `document engine converts csv to json and back`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_doc").apply { mkdirs() }
        val csvFile = File(tempDir, "data.csv")
        csvFile.writeText("name,score\nAlice,95\nBob,88\n")

        val engine = DocumentConversionEngine(context)
        val outDir = File(tempDir, "out").apply { mkdirs() }
        val jsonOutputs = engine.convert(
            toolId = ToolId.CSV_TO_JSON,
            files = listOf(
                SelectedFileItem(
                    uri = Uri.fromFile(csvFile),
                    displayName = "data.csv",
                    sizeBytes = csvFile.length(),
                    mimeType = "text/csv",
                    extension = "csv"
                )
            ),
            options = ConversionOptions(),
            outputDir = outDir
        ) { _, _, _ -> }

        val jsonFile = File(jsonOutputs.first().outputFilePath)
        val jsonContent = jsonFile.readText()
        assertTrue(jsonContent.contains("Alice"))
        assertTrue(jsonContent.contains("95"))

        val csvOutputs = engine.convert(
            toolId = ToolId.JSON_TO_CSV,
            files = listOf(
                SelectedFileItem(
                    uri = Uri.fromFile(jsonFile),
                    displayName = jsonFile.name,
                    sizeBytes = jsonFile.length(),
                    mimeType = "application/json",
                    extension = "json"
                )
            ),
            options = ConversionOptions(),
            outputDir = outDir
        ) { _, _, _ -> }

        val roundTripCsv = File(csvOutputs.first().outputFilePath).readText()
        assertTrue(roundTripCsv.contains("name,score"))
        assertTrue(roundTripCsv.contains("Bob,88"))
    }

    @Test
    fun `audio engine encodes wav to mp3 with pure java lame`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_audio").apply { mkdirs() }
        val wavFile = File(tempDir, "tone.wav")

        val sampleRate = 44100
        val numSamples = 4410 // 0.1s
        val dataBytes = numSamples * 2
        val wavHeader = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataBytes)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(dataBytes)
        }.array()

        FileOutputStream(wavFile).use { fos ->
            fos.write(wavHeader)
            val pcm = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                val sample = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 12000.0).toInt().toShort()
                pcm.putShort(sample)
            }
            fos.write(pcm.array())
        }

        val engine = AudioConversionEngine(context)
        val outDir = File(tempDir, "out").apply { mkdirs() }
        val out = engine.encodeWavFileToMp3(
            wavFile = wavFile,
            sourceSizeBytes = wavFile.length(),
            bitrateKbps = 128,
            baseName = "tone",
            outputDir = outDir
        ) { _, _ -> }

        val mp3File = File(out.outputFilePath)
        assertTrue(mp3File.exists() && mp3File.length() > 100L)
        assertTrue(mp3File.name.endsWith(".mp3"))
    }

    @Test
    fun `archive engine creates and extracts zip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_zip").apply { mkdirs() }
        val f1 = File(tempDir, "hello.txt").apply { writeText("Hello FileFlow!") }
        val outDir = File(tempDir, "out").apply { mkdirs() }

        val engine = ArchiveConversionEngine(context)
        val zipOut = engine.convert(
            toolId = ToolId.CREATE_ZIP,
            files = listOf(
                SelectedFileItem(
                    uri = Uri.fromFile(f1),
                    displayName = f1.name,
                    sizeBytes = f1.length(),
                    mimeType = "text/plain",
                    extension = "txt"
                )
            ),
            outputDir = outDir
        ) { _, _, _ -> }.first()

        val zipFile = File(zipOut.outputFilePath)
        assertTrue(zipFile.exists())

        val extracted = engine.convert(
            toolId = ToolId.EXTRACT_ZIP,
            files = listOf(
                SelectedFileItem(
                    uri = Uri.fromFile(zipFile),
                    displayName = zipFile.name,
                    sizeBytes = zipFile.length(),
                    mimeType = "application/zip",
                    extension = "zip"
                )
            ),
            outputDir = File(tempDir, "unzipped").apply { mkdirs() }
        ) { _, _, _ -> }

        assertEquals(1, extracted.size)
        assertEquals("Hello FileFlow!", File(extracted.first().outputFilePath).readText())
    }
}
