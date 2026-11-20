package com.example.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CategoryArchiveColor
import com.example.ui.theme.CategoryAudioColor
import com.example.ui.theme.CategoryDocumentColor
import com.example.ui.theme.CategoryImageColor
import com.example.ui.theme.CategoryPdfColor
import com.example.ui.theme.CategoryVideoColor

enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ConversionCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentColor: Color
) {
    IMAGE("image", "Images", "PNG, JPG, WEBP, Compress, Resize", CategoryImageColor),
    PDF("pdf", "PDF Studio", "Merge, Split, Extract, Render, Text", CategoryPdfColor),
    DOCUMENT("document", "Documents", "TXT, Markdown, HTML, CSV, JSON", CategoryDocumentColor),
    AUDIO("audio", "Audio", "MP3, WAV, M4A, Compress, Trim", CategoryAudioColor),
    VIDEO("video", "Video", "MP4, WEBM, Compress, Audio, Frames", CategoryVideoColor),
    ARCHIVE("archive", "Archives & Utils", "ZIP, Unzip, File Checksums", CategoryArchiveColor)
}

enum class ToolId(val id: String) {
    // Image Tools
    PNG_TO_JPG("png_to_jpg"),
    JPG_TO_PNG("jpg_to_png"),
    WEBP_TO_PNG("webp_to_png"),
    PNG_TO_WEBP("png_to_webp"),
    JPG_TO_WEBP("jpg_to_webp"),
    IMAGE_COMPRESS("image_compress"),
    IMAGE_RESIZE("image_resize"),
    IMAGE_ANY_TO_PNG_JPG("image_any_convert"),

    // PDF Tools
    MERGE_PDF("merge_pdf"),
    SPLIT_PDF("split_pdf"),
    EXTRACT_PDF_PAGES("extract_pdf_pages"),
    PDF_TO_IMAGES("pdf_to_images"),
    IMAGES_TO_PDF("images_to_pdf"),
    PDF_TO_TXT("pdf_to_txt"),
    COMPRESS_PDF("compress_pdf"),

    // Document & Data Tools
    TXT_TO_PDF("txt_to_pdf"),
    MARKDOWN_TO_PDF("md_to_pdf"),
    MARKDOWN_TO_HTML("md_to_html"),
    HTML_TO_TXT("html_to_txt"),
    TXT_TO_HTML("txt_to_html"),
    CSV_TO_JSON("csv_to_json"),
    JSON_TO_CSV("json_to_csv"),
    JSON_FORMAT("json_format"),

    // Audio Tools
    MP3_TO_WAV("mp3_to_wav"),
    WAV_TO_MP3("wav_to_mp3"),
    AUDIO_COMPRESS("audio_compress"),
    AUDIO_TO_M4A("audio_to_m4a"),
    AUDIO_TRIM("audio_trim"),

    // Video Tools
    MP4_TO_WEBM("mp4_to_webm"),
    VIDEO_COMPRESS("video_compress"),
    EXTRACT_AUDIO("extract_audio"),
    VIDEO_TRIM("video_trim"),
    VIDEO_TO_FRAMES("video_to_frames"),

    // Archive & Utility Tools
    CREATE_ZIP("create_zip"),
    EXTRACT_ZIP("extract_zip"),
    FILE_CHECKSUM("file_checksum");

    companion object {
        fun fromId(id: String): ToolId? = entries.find { it.id == id }
    }
}

enum class PickerKind {
    PHOTO_PICKER_IMAGE,
    PHOTO_PICKER_VIDEO,
    SAF_DOCUMENT
}

data class ConversionTool(
    val toolId: ToolId,
    val title: String,
    val shortTitle: String,
    val description: String,
    val category: ConversionCategory,
    val inputLabel: String,
    val outputLabel: String,
    val acceptedMimeTypes: List<String>,
    val acceptedExtensions: List<String>,
    val pickerKind: PickerKind = PickerKind.SAF_DOCUMENT,
    val supportsBatch: Boolean = true,
    val requiresMultipleInputs: Boolean = false,
    val isExtraUsefulTool: Boolean = false
)

object ToolRegistry {
    val allTools: List<ConversionTool> = listOf(
        // IMAGE CONVERTER
        ConversionTool(
            toolId = ToolId.PNG_TO_JPG,
            title = "PNG to JPG",
            shortTitle = "PNG → JPG",
            description = "Convert PNG images to lightweight JPG with adjustable quality and transparency flattening.",
            category = ConversionCategory.IMAGE,
            inputLabel = "PNG",
            outputLabel = "JPG",
            acceptedMimeTypes = listOf("image/png", "image/*"),
            acceptedExtensions = listOf("png"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.JPG_TO_PNG,
            title = "JPG to PNG",
            shortTitle = "JPG → PNG",
            description = "Convert JPG photos to crisp lossless PNG format.",
            category = ConversionCategory.IMAGE,
            inputLabel = "JPG",
            outputLabel = "PNG",
            acceptedMimeTypes = listOf("image/jpeg", "image/*"),
            acceptedExtensions = listOf("jpg", "jpeg"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.WEBP_TO_PNG,
            title = "WEBP to PNG",
            shortTitle = "WEBP → PNG",
            description = "Decode WebP graphics into universal lossless PNG images.",
            category = ConversionCategory.IMAGE,
            inputLabel = "WEBP",
            outputLabel = "PNG",
            acceptedMimeTypes = listOf("image/webp", "image/*"),
            acceptedExtensions = listOf("webp"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.PNG_TO_WEBP,
            title = "PNG to WEBP",
            shortTitle = "PNG → WEBP",
            description = "Convert PNG images into modern, compact WebP files.",
            category = ConversionCategory.IMAGE,
            inputLabel = "PNG",
            outputLabel = "WEBP",
            acceptedMimeTypes = listOf("image/png", "image/*"),
            acceptedExtensions = listOf("png"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.JPG_TO_WEBP,
            title = "JPG to WEBP",
            shortTitle = "JPG → WEBP",
            description = "Re-encode JPG photos to WebP for smaller size with high visual fidelity.",
            category = ConversionCategory.IMAGE,
            inputLabel = "JPG",
            outputLabel = "WEBP",
            acceptedMimeTypes = listOf("image/jpeg", "image/*"),
            acceptedExtensions = listOf("jpg", "jpeg"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.IMAGE_COMPRESS,
            title = "Image Compression",
            shortTitle = "Compress Image",
            description = "Shrink image file sizes with smart quality control, optional max width, and EXIF stripping.",
            category = ConversionCategory.IMAGE,
            inputLabel = "IMG",
            outputLabel = "OPT",
            acceptedMimeTypes = listOf("image/*"),
            acceptedExtensions = listOf("png", "jpg", "jpeg", "webp", "bmp"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.IMAGE_RESIZE,
            title = "Resize & Scale Image",
            shortTitle = "Resize Image",
            description = "Scale images by percentage (25%–75%) or preset dimensions while keeping aspect ratio.",
            category = ConversionCategory.IMAGE,
            inputLabel = "IMG",
            outputLabel = "RESIZED",
            acceptedMimeTypes = listOf("image/*"),
            acceptedExtensions = listOf("png", "jpg", "jpeg", "webp", "bmp", "heic"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.IMAGE_ANY_TO_PNG_JPG,
            title = "HEIC / BMP / GIF Converter",
            shortTitle = "Any → PNG/JPG",
            description = "Convert HEIC, BMP, or static GIF images into standard PNG, JPG, or WEBP.",
            category = ConversionCategory.IMAGE,
            inputLabel = "ANY IMG",
            outputLabel = "PNG/JPG",
            acceptedMimeTypes = listOf("image/*"),
            acceptedExtensions = listOf("heic", "heif", "bmp", "gif", "png", "jpg", "jpeg", "webp"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true,
            isExtraUsefulTool = true
        ),

        // PDF TOOLS
        ConversionTool(
            toolId = ToolId.MERGE_PDF,
            title = "Merge PDFs",
            shortTitle = "Merge PDF",
            description = "Combine multiple PDF documents into one ordered PDF file offline.",
            category = ConversionCategory.PDF,
            inputLabel = "PDFs",
            outputLabel = "PDF",
            acceptedMimeTypes = listOf("application/pdf"),
            acceptedExtensions = listOf("pdf"),
            supportsBatch = true,
            requiresMultipleInputs = true
        ),
        ConversionTool(
            toolId = ToolId.SPLIT_PDF,
            title = "Split PDF",
            shortTitle = "Split PDF",
            description = "Split a multi-page PDF into individual pages or fixed page chunks.",
            category = ConversionCategory.PDF,
            inputLabel = "PDF",
            outputLabel = "PDFs",
            acceptedMimeTypes = listOf("application/pdf"),
            acceptedExtensions = listOf("pdf"),
            supportsBatch = false
        ),
        ConversionTool(
            toolId = ToolId.EXTRACT_PDF_PAGES,
            title = "Extract PDF Pages",
            shortTitle = "Extract Pages",
            description = "Extract specific pages or ranges (e.g. 1, 3-5, 8) into a new PDF.",
            category = ConversionCategory.PDF,
            inputLabel = "PDF",
            outputLabel = "PDF",
            acceptedMimeTypes = listOf("application/pdf"),
            acceptedExtensions = listOf("pdf"),
            supportsBatch = false
        ),
        ConversionTool(
            toolId = ToolId.PDF_TO_IMAGES,
            title = "PDF to Images",
            shortTitle = "PDF → Images",
            description = "Render every PDF page as crisp PNG or JPG images at selectable DPI.",
            category = ConversionCategory.PDF,
            inputLabel = "PDF",
            outputLabel = "PNG/JPG",
            acceptedMimeTypes = listOf("application/pdf"),
            acceptedExtensions = listOf("pdf"),
            supportsBatch = false
        ),
        ConversionTool(
            toolId = ToolId.IMAGES_TO_PDF,
            title = "Images to PDF",
            shortTitle = "Images → PDF",
            description = "Pack one or more photos into a clean paginated A4, Letter, or Fit-to-Image PDF.",
            category = ConversionCategory.PDF,
            inputLabel = "IMGs",
            outputLabel = "PDF",
            acceptedMimeTypes = listOf("image/*"),
            acceptedExtensions = listOf("png", "jpg", "jpeg", "webp", "bmp"),
            pickerKind = PickerKind.PHOTO_PICKER_IMAGE,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.PDF_TO_TXT,
            title = "PDF to Text",
            shortTitle = "PDF → TXT",
            description = "Extract readable plain text from PDF documents into a .txt file.",
            category = ConversionCategory.PDF,
            inputLabel = "PDF",
            outputLabel = "TXT",
            acceptedMimeTypes = listOf("application/pdf"),
            acceptedExtensions = listOf("pdf"),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.COMPRESS_PDF,
            title = "Compress PDF",
            shortTitle = "Compress PDF",
            description = "Reduce large image-heavy PDF sizes by optimizing page resolution and JPEG compression.",
            category = ConversionCategory.PDF,
            inputLabel = "PDF",
            outputLabel = "OPT PDF",
            acceptedMimeTypes = listOf("application/pdf"),
            acceptedExtensions = listOf("pdf"),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),

        // DOCUMENT & DATA TOOLS
        ConversionTool(
            toolId = ToolId.TXT_TO_PDF,
            title = "TXT to PDF",
            shortTitle = "TXT → PDF",
            description = "Convert plain text files into neatly paginated PDFs with font & margin controls.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "TXT",
            outputLabel = "PDF",
            acceptedMimeTypes = listOf("text/plain", "text/*"),
            acceptedExtensions = listOf("txt", "log", "csv", "json", "md"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.MARKDOWN_TO_PDF,
            title = "Markdown to PDF",
            shortTitle = "MD → PDF",
            description = "Render Markdown (.md) headings, lists, quotes, and code into a styled PDF.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "MD",
            outputLabel = "PDF",
            acceptedMimeTypes = listOf("text/markdown", "text/plain", "text/*", "application/octet-stream"),
            acceptedExtensions = listOf("md", "markdown", "txt"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.MARKDOWN_TO_HTML,
            title = "Markdown to HTML",
            shortTitle = "MD → HTML",
            description = "Compile Markdown files into a clean standalone HTML5 document with responsive CSS.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "MD",
            outputLabel = "HTML",
            acceptedMimeTypes = listOf("text/markdown", "text/plain", "text/*", "application/octet-stream"),
            acceptedExtensions = listOf("md", "markdown", "txt"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.HTML_TO_TXT,
            title = "HTML to TXT",
            shortTitle = "HTML → TXT",
            description = "Strip HTML markup and scripts to extract clean readable plain text.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "HTML",
            outputLabel = "TXT",
            acceptedMimeTypes = listOf("text/html", "text/plain"),
            acceptedExtensions = listOf("html", "htm"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.TXT_TO_HTML,
            title = "TXT to HTML",
            shortTitle = "TXT → HTML",
            description = "Wrap plain text into a clean, readable, mobile-friendly HTML5 page.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "TXT",
            outputLabel = "HTML",
            acceptedMimeTypes = listOf("text/plain", "text/*"),
            acceptedExtensions = listOf("txt", "log"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.CSV_TO_JSON,
            title = "CSV to JSON",
            shortTitle = "CSV → JSON",
            description = "Parse tabular CSV spreadsheets into structured pretty-printed JSON arrays.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "CSV",
            outputLabel = "JSON",
            acceptedMimeTypes = listOf("text/csv", "text/comma-separated-values", "text/plain"),
            acceptedExtensions = listOf("csv"),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.JSON_TO_CSV,
            title = "JSON to CSV",
            shortTitle = "JSON → CSV",
            description = "Flatten JSON object arrays into spreadsheet-ready CSV files.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "JSON",
            outputLabel = "CSV",
            acceptedMimeTypes = listOf("application/json", "text/plain"),
            acceptedExtensions = listOf("json"),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.JSON_FORMAT,
            title = "JSON Formatter / Minifier",
            shortTitle = "Format JSON",
            description = "Validate and pretty-print or minify JSON files cleanly.",
            category = ConversionCategory.DOCUMENT,
            inputLabel = "JSON",
            outputLabel = "JSON",
            acceptedMimeTypes = listOf("application/json", "text/plain"),
            acceptedExtensions = listOf("json"),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),

        // AUDIO TOOLS
        ConversionTool(
            toolId = ToolId.MP3_TO_WAV,
            title = "MP3 to WAV",
            shortTitle = "MP3 → WAV",
            description = "Decode MP3 (or AAC/M4A/OGG) into uncompressed 16-bit PCM WAV audio.",
            category = ConversionCategory.AUDIO,
            inputLabel = "MP3",
            outputLabel = "WAV",
            acceptedMimeTypes = listOf("audio/*"),
            acceptedExtensions = listOf("mp3", "m4a", "aac", "ogg", "flac"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.WAV_TO_MP3,
            title = "WAV to MP3",
            shortTitle = "WAV → MP3",
            description = "Encode PCM WAV recordings into compact MP3 using pure-Java LAME.",
            category = ConversionCategory.AUDIO,
            inputLabel = "WAV",
            outputLabel = "MP3",
            acceptedMimeTypes = listOf("audio/wav", "audio/x-wav", "audio/*"),
            acceptedExtensions = listOf("wav"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.AUDIO_COMPRESS,
            title = "Audio Compression",
            shortTitle = "Compress Audio",
            description = "Reduce MP3, WAV, M4A, or FLAC sizes by re-encoding at a lower target bitrate.",
            category = ConversionCategory.AUDIO,
            inputLabel = "AUDIO",
            outputLabel = "MP3",
            acceptedMimeTypes = listOf("audio/*"),
            acceptedExtensions = listOf("mp3", "wav", "m4a", "aac", "ogg", "flac"),
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.AUDIO_TO_M4A,
            title = "Audio to M4A (AAC)",
            shortTitle = "Audio → M4A",
            description = "Transcode audio tracks into hardware-accelerated M4A AAC format.",
            category = ConversionCategory.AUDIO,
            inputLabel = "AUDIO",
            outputLabel = "M4A",
            acceptedMimeTypes = listOf("audio/*"),
            acceptedExtensions = listOf("mp3", "wav", "ogg", "flac", "aac", "m4a"),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.AUDIO_TRIM,
            title = "Audio Trimmer",
            shortTitle = "Trim Audio",
            description = "Cut a precise start-to-end clip from any audio file.",
            category = ConversionCategory.AUDIO,
            inputLabel = "AUDIO",
            outputLabel = "CLIP",
            acceptedMimeTypes = listOf("audio/*"),
            acceptedExtensions = listOf("mp3", "wav", "m4a", "aac", "ogg"),
            supportsBatch = false,
            isExtraUsefulTool = true
        ),

        // VIDEO TOOLS
        ConversionTool(
            toolId = ToolId.MP4_TO_WEBM,
            title = "MP4 to WEBM",
            shortTitle = "MP4 → WEBM",
            description = "Transcode MP4 video into web-friendly WEBM (VP8/VP9 + Opus/Vorbis).",
            category = ConversionCategory.VIDEO,
            inputLabel = "MP4",
            outputLabel = "WEBM",
            acceptedMimeTypes = listOf("video/*"),
            acceptedExtensions = listOf("mp4", "mov", "mkv", "3gp"),
            pickerKind = PickerKind.PHOTO_PICKER_VIDEO,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.VIDEO_COMPRESS,
            title = "Video Compression",
            shortTitle = "Compress Video",
            description = "Compress large videos with target resolution (480p/720p/1080p) and bitrate presets.",
            category = ConversionCategory.VIDEO,
            inputLabel = "VIDEO",
            outputLabel = "OPT MP4",
            acceptedMimeTypes = listOf("video/*"),
            acceptedExtensions = listOf("mp4", "mov", "mkv", "webm", "3gp"),
            pickerKind = PickerKind.PHOTO_PICKER_VIDEO,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.EXTRACT_AUDIO,
            title = "Extract Audio from Video",
            shortTitle = "Video → Audio",
            description = "Strip the audio track from video files into M4A, MP3, or WAV.",
            category = ConversionCategory.VIDEO,
            inputLabel = "VIDEO",
            outputLabel = "AUDIO",
            acceptedMimeTypes = listOf("video/*"),
            acceptedExtensions = listOf("mp4", "mov", "mkv", "webm", "3gp"),
            pickerKind = PickerKind.PHOTO_PICKER_VIDEO,
            supportsBatch = true
        ),
        ConversionTool(
            toolId = ToolId.VIDEO_TRIM,
            title = "Video Trimmer",
            shortTitle = "Trim Video",
            description = "Trim a video clip between start and end seconds cleanly.",
            category = ConversionCategory.VIDEO,
            inputLabel = "VIDEO",
            outputLabel = "MP4",
            acceptedMimeTypes = listOf("video/*"),
            acceptedExtensions = listOf("mp4", "mov", "mkv", "3gp"),
            pickerKind = PickerKind.PHOTO_PICKER_VIDEO,
            supportsBatch = false,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.VIDEO_TO_FRAMES,
            title = "Video to Frames",
            shortTitle = "Video → Images",
            description = "Capture still PNG/JPG frames from a video at regular time intervals.",
            category = ConversionCategory.VIDEO,
            inputLabel = "VIDEO",
            outputLabel = "FRAMES",
            acceptedMimeTypes = listOf("video/*"),
            acceptedExtensions = listOf("mp4", "mov", "mkv", "webm", "3gp"),
            pickerKind = PickerKind.PHOTO_PICKER_VIDEO,
            supportsBatch = false,
            isExtraUsefulTool = true
        ),

        // ARCHIVE & UTILITY TOOLS
        ConversionTool(
            toolId = ToolId.CREATE_ZIP,
            title = "Create ZIP Archive",
            shortTitle = "Files → ZIP",
            description = "Compress one or more files of any type into a single portable .zip archive.",
            category = ConversionCategory.ARCHIVE,
            inputLabel = "FILES",
            outputLabel = "ZIP",
            acceptedMimeTypes = listOf("*/*"),
            acceptedExtensions = emptyList(),
            supportsBatch = true,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.EXTRACT_ZIP,
            title = "Extract ZIP Archive",
            shortTitle = "Unzip Files",
            description = "Unpack files from a .zip archive so you can inspect, save, or share each item.",
            category = ConversionCategory.ARCHIVE,
            inputLabel = "ZIP",
            outputLabel = "FILES",
            acceptedMimeTypes = listOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
            acceptedExtensions = listOf("zip"),
            supportsBatch = false,
            isExtraUsefulTool = true
        ),
        ConversionTool(
            toolId = ToolId.FILE_CHECKSUM,
            title = "File Hash & Checksum",
            shortTitle = "SHA-256 / MD5",
            description = "Compute cryptographic SHA-256, SHA-1, and MD5 checksums for verification.",
            category = ConversionCategory.ARCHIVE,
            inputLabel = "ANY",
            outputLabel = "HASH TXT",
            acceptedMimeTypes = listOf("*/*"),
            acceptedExtensions = emptyList(),
            supportsBatch = true,
            isExtraUsefulTool = true
        )
    )

    fun getTool(toolId: ToolId): ConversionTool =
        allTools.firstOrNull { it.toolId == toolId } ?: allTools.first()

    fun toolsForCategory(category: ConversionCategory): List<ConversionTool> =
        allTools.filter { it.category == category }

    fun suggestToolsForFile(fileName: String, mimeType: String?): List<ConversionTool> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = mimeType?.lowercase().orEmpty()
        val matched = allTools.filter { tool ->
            (ext.isNotEmpty() && ext in tool.acceptedExtensions) ||
                (mime.isNotEmpty() && tool.acceptedMimeTypes.any { accepted ->
                    accepted == "*/*" ||
                        (accepted.endsWith("/*") && mime.startsWith(accepted.removeSuffix("*"))) ||
                        accepted == mime
                })
        }
        return if (matched.isNotEmpty()) matched else allTools.filter { it.category == ConversionCategory.ARCHIVE }
    }
}

data class SelectedFileItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val extension: String,
    val pageCount: Int? = null
)

enum class PdfPageSizeOption(val label: String) {
    FIT_IMAGE("Fit Image"),
    A4("A4 Portrait"),
    LETTER("US Letter")
}

enum class SplitMode(val label: String) {
    EVERY_PAGE("Every Page"),
    CHUNKS("Fixed Page Chunks")
}

enum class VideoQualityPreset(val label: String, val bitrateBps: Int, val targetHeight: Int?) {
    HIGH("High (1080p • 5 Mbps)", 5_000_000, 1080),
    BALANCED("Balanced (720p • 2.5 Mbps)", 2_500_000, 720),
    COMPACT("Compact (480p • 1.1 Mbps)", 1_100_000, 480)
}

data class ConversionOptions(
    val quality: Int = 85,
    val scalePercent: Int = 100,
    val stripExif: Boolean = false,
    val imageOutputFormat: String = "PNG", // PNG, JPG, WEBP
    val pdfPageRange: String = "1",
    val splitMode: SplitMode = SplitMode.EVERY_PAGE,
    val splitChunkSize: Int = 2,
    val pdfDpi: Int = 180,
    val pdfPageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    val textFontSizeSp: Float = 12f,
    val useMonospaceFont: Boolean = false,
    val jsonMinify: Boolean = false,
    val audioBitrateKbps: Int = 128,
    val extractedAudioFormat: String = "M4A", // M4A, MP3, WAV
    val trimStartSec: Float = 0f,
    val trimEndSec: Float = 15f,
    val videoQualityPreset: VideoQualityPreset = VideoQualityPreset.BALANCED,
    val frameCount: Int = 5
)

data class ConversionOutput(
    val outputFilePath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceSizeBytes: Long,
    val extraNote: String? = null
)

sealed interface ConversionState {
    data object Idle : ConversionState
    data class Running(
        val progress: Float,
        val currentIndex: Int,
        val totalFiles: Int,
        val statusMessage: String
    ) : ConversionState

    data class Success(
        val outputs: List<ConversionOutput>,
        val durationMs: Long
    ) : ConversionState

    data class Error(
        val message: String,
        val details: String? = null
    ) : ConversionState
}
