package com.example.engine

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.text.HtmlCompat
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

class DocumentConversionEngine(private val context: Context) {

    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json { prettyPrint = false }

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
            onProgress(idx.toFloat() / files.size.toFloat(), idx + 1, "Converting ${item.displayName}…")

            val text = readTextFromUri(item)
            val base = FileHelper.sanitizeBaseName(item.displayName)

            val output = when (toolId) {
                ToolId.TXT_TO_PDF -> renderPlainTextToPdf(item, text, base, options, outputDir)
                ToolId.MARKDOWN_TO_PDF -> renderMarkdownToPdf(item, text, base, options, outputDir)
                ToolId.MARKDOWN_TO_HTML -> convertMarkdownToHtml(item, text, base, outputDir)
                ToolId.HTML_TO_TXT -> convertHtmlToText(item, text, base, outputDir)
                ToolId.TXT_TO_HTML -> convertTextToHtml(item, text, base, outputDir)
                ToolId.CSV_TO_JSON -> convertCsvToJson(item, text, base, outputDir)
                ToolId.JSON_TO_CSV -> convertJsonToCsv(item, text, base, outputDir)
                ToolId.JSON_FORMAT -> formatJson(item, text, base, options.jsonMinify, outputDir)
                else -> throw IllegalArgumentException("Unsupported document tool: $toolId")
            }

            outputs += output
            onProgress((idx + 1).toFloat() / files.size.toFloat(), idx + 1, "Completed ${item.displayName}")
        }

        return outputs
    }

    private fun readTextFromUri(item: SelectedFileItem): String {
        val bytes = context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read ${item.displayName}")
        if (bytes.isEmpty()) throw IllegalArgumentException("${item.displayName} is empty.")
        return bytes.toString(Charsets.UTF_8)
    }

    private fun renderPlainTextToPdf(
        item: SelectedFileItem,
        text: String,
        baseName: String,
        options: ConversionOptions,
        outputDir: File
    ): ConversionOutput {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44f
        val contentWidth = (pageWidth - margin * 2).toInt()
        val maxContentHeight = pageHeight - margin * 2 - 24f

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 24, 38)
            textSize = options.textFontSizeSp.coerceIn(9f, 22f)
            typeface = if (options.useMonospaceFont) Typeface.MONOSPACE else Typeface.SANS_SERIF
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 120, 140)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }

        val normalized = text.replace("\r\n", "\n")
        val fullLayout = StaticLayout.Builder
            .obtain(normalized, 0, normalized.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.15f)
            .build()

        val pdfDoc = PdfDocument()
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "pdf")
        try {
            var startLine = 0
            var pageNumber = 1
            while (startLine < fullLayout.lineCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val pageTopOffset = fullLayout.getLineTop(startLine)
                var endLine = startLine
                while (endLine < fullLayout.lineCount) {
                    val lineBottom = fullLayout.getLineBottom(endLine) - pageTopOffset
                    if (lineBottom > maxContentHeight && endLine > startLine) break
                    endLine++
                }

                canvas.save()
                canvas.translate(margin, margin)
                for (line in startLine until endLine) {
                    val lineStart = fullLayout.getLineStart(line)
                    val lineEnd = fullLayout.getLineEnd(line)
                    val baseline = (fullLayout.getLineBaseline(line) - pageTopOffset).toFloat()
                    canvas.drawText(normalized, lineStart, lineEnd, 0f, baseline, paint)
                }
                canvas.restore()

                canvas.drawText(
                    "Page $pageNumber",
                    pageWidth - margin,
                    pageHeight - 20f,
                    footerPaint
                )

                pdfDoc.finishPage(page)
                startLine = endLine
                pageNumber++
            }

            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            return ConversionOutput(
                outputFilePath = outFile.absolutePath,
                fileName = outFile.name,
                mimeType = "application/pdf",
                sizeBytes = outFile.length(),
                sourceSizeBytes = item.sizeBytes,
                extraNote = "${pageNumber - 1} page(s) rendered"
            )
        } finally {
            pdfDoc.close()
        }
    }

    private fun renderMarkdownToPdf(
        item: SelectedFileItem,
        markdown: String,
        baseName: String,
        options: ConversionOptions,
        outputDir: File
    ): ConversionOutput {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44f
        val contentWidth = (pageWidth - margin * 2).toInt()
        val maxY = pageHeight - margin - 24f

        val baseSize = options.textFontSizeSp.coerceIn(10f, 18f)
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 32, 48)
            textSize = baseSize
            typeface = Typeface.SANS_SERIF
        }
        val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = baseSize + 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = baseSize + 5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h3Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = baseSize + 2.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = (baseSize - 1.5f).coerceAtLeast(9f)
            typeface = Typeface.MONOSPACE
        }
        val codeBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
        }
        val quoteBarPaint = Paint().apply {
            color = Color.rgb(41, 82, 255)
            strokeWidth = 3f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 120, 140)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }

        val pdfDoc = PdfDocument()
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "pdf")
        val lines = markdown.replace("\r\n", "\n").lines()

        try {
            var pageNumber = 1
            var currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = currentPage.canvas
            canvas.drawColor(Color.WHITE)
            var cursorY = margin
            var inCodeBlock = false

            fun startNewPage() {
                canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - 20f, footerPaint)
                pdfDoc.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = currentPage.canvas
                canvas.drawColor(Color.WHITE)
                cursorY = margin
            }

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock
                    cursorY += 4f
                    continue
                }

                val (paint, displayText, indent, extraBottom, isQuote, isCode) = when {
                    inCodeBlock -> MdLineStyle(codePaint, rawLine.ifEmpty { " " }, 12f, 2f, false, true)
                    trimmed.startsWith("# ") -> MdLineStyle(h1Paint, stripInlineMd(trimmed.drop(2)), 0f, 10f, false, false)
                    trimmed.startsWith("## ") -> MdLineStyle(h2Paint, stripInlineMd(trimmed.drop(3)), 0f, 8f, false, false)
                    trimmed.startsWith("### ") -> MdLineStyle(h3Paint, stripInlineMd(trimmed.drop(4)), 0f, 6f, false, false)
                    trimmed.startsWith("> ") -> MdLineStyle(bodyPaint, stripInlineMd(trimmed.drop(2)), 14f, 6f, true, false)
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                        MdLineStyle(bodyPaint, "•  " + stripInlineMd(trimmed.drop(2)), 12f, 4f, false, false)
                    trimmed.isEmpty() -> {
                        cursorY += baseSize * 0.65f
                        if (cursorY > maxY) startNewPage()
                        continue
                    }
                    else -> MdLineStyle(bodyPaint, stripInlineMd(rawLine), 0f, 5f, false, false)
                }

                val availWidth = (contentWidth - indent).toInt().coerceAtLeast(100)
                val layout = StaticLayout.Builder
                    .obtain(displayText, 0, displayText.length, paint, availWidth)
                    .setLineSpacing(2f, 1.15f)
                    .build()

                if (cursorY + layout.height > maxY) {
                    startNewPage()
                }

                if (isCode) {
                    canvas.drawRect(
                        margin,
                        cursorY - 2f,
                        pageWidth - margin,
                        cursorY + layout.height + 4f,
                        codeBgPaint
                    )
                }
                if (isQuote) {
                    canvas.drawLine(
                        margin + 4f,
                        cursorY,
                        margin + 4f,
                        cursorY + layout.height.toFloat(),
                        quoteBarPaint
                    )
                }

                canvas.save()
                canvas.translate(margin + indent, cursorY)
                layout.draw(canvas)
                canvas.restore()

                cursorY += layout.height + extraBottom
            }

            canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - 20f, footerPaint)
            pdfDoc.finishPage(currentPage)

            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            return ConversionOutput(
                outputFilePath = outFile.absolutePath,
                fileName = outFile.name,
                mimeType = "application/pdf",
                sizeBytes = outFile.length(),
                sourceSizeBytes = item.sizeBytes,
                extraNote = "Markdown → $pageNumber page PDF"
            )
        } finally {
            pdfDoc.close()
        }
    }

    private data class MdLineStyle(
        val paint: TextPaint,
        val text: String,
        val indent: Float,
        val extraBottom: Float,
        val isQuote: Boolean,
        val isCode: Boolean
    )

    private fun stripInlineMd(text: String): String =
        text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("`(.+?)`"), "$1")
            .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1 ($2)")

    private fun convertMarkdownToHtml(
        item: SelectedFileItem,
        markdown: String,
        baseName: String,
        outputDir: File
    ): ConversionOutput {
        val bodyHtml = buildString {
            val lines = markdown.replace("\r\n", "\n").lines()
            var inCode = false
            var inList = false

            fun closeList() {
                if (inList) {
                    append("</ul>\n")
                    inList = false
                }
            }

            for (raw in lines) {
                val trimmed = raw.trim()
                if (trimmed.startsWith("```")) {
                    closeList()
                    if (!inCode) {
                        append("<pre><code>")
                        inCode = true
                    } else {
                        append("</code></pre>\n")
                        inCode = false
                    }
                    continue
                }
                if (inCode) {
                    append(escapeHtml(raw)).append('\n')
                    continue
                }
                when {
                    trimmed.startsWith("### ") -> {
                        closeList()
                        append("<h3>").append(inlineMdToHtml(trimmed.drop(4))).append("</h3>\n")
                    }
                    trimmed.startsWith("## ") -> {
                        closeList()
                        append("<h2>").append(inlineMdToHtml(trimmed.drop(3))).append("</h2>\n")
                    }
                    trimmed.startsWith("# ") -> {
                        closeList()
                        append("<h1>").append(inlineMdToHtml(trimmed.drop(2))).append("</h1>\n")
                    }
                    trimmed.startsWith("> ") -> {
                        closeList()
                        append("<blockquote>").append(inlineMdToHtml(trimmed.drop(2))).append("</blockquote>\n")
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                        if (!inList) {
                            append("<ul>\n")
                            inList = true
                        }
                        append("  <li>").append(inlineMdToHtml(trimmed.drop(2))).append("</li>\n")
                    }
                    trimmed == "---" || trimmed == "***" -> {
                        closeList()
                        append("<hr />\n")
                    }
                    trimmed.isEmpty() -> closeList()
                    else -> {
                        closeList()
                        append("<p>").append(inlineMdToHtml(raw)).append("</p>\n")
                    }
                }
            }
            if (inList) append("</ul>\n")
            if (inCode) append("</code></pre>\n")
        }

        val fullHtml = wrapHtmlDocument(baseName, bodyHtml)
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "html")
        outFile.writeText(fullHtml)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "text/html",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = "Responsive HTML5"
        )
    }

    private fun inlineMdToHtml(input: String): String {
        var s = escapeHtml(input)
        s = s.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        s = s.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        s = s.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
        return s
    }

    private fun convertHtmlToText(
        item: SelectedFileItem,
        html: String,
        baseName: String,
        outputDir: File
    ): ConversionOutput {
        val cleaned = html
            .replace(Regex("(?is)<script.*?>.*?</script>"), "")
            .replace(Regex("(?is)<style.*?>.*?</style>"), "")
        val plain = HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "txt")
        outFile.writeText(plain)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "text/plain",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = "${plain.lines().size} lines extracted"
        )
    }

    private fun convertTextToHtml(
        item: SelectedFileItem,
        text: String,
        baseName: String,
        outputDir: File
    ): ConversionOutput {
        val paragraphs = text.replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .joinToString("\n") { block ->
                val escaped = escapeHtml(block.trim()).replace("\n", "<br />\n")
                "<p>$escaped</p>"
            }
        val html = wrapHtmlDocument(baseName, paragraphs)
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "html")
        outFile.writeText(html)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "text/html",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = "Styled HTML5 page"
        )
    }

    private fun convertCsvToJson(
        item: SelectedFileItem,
        csvText: String,
        baseName: String,
        outputDir: File
    ): ConversionOutput {
        val rows = parseCsvRows(csvText)
        if (rows.isEmpty()) {
            throw IllegalArgumentException("CSV file has no readable rows.")
        }
        val headers = rows.first().mapIndexed { idx, h ->
            h.trim().ifBlank { "column_${idx + 1}" }
        }
        val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()

        val jsonArray = buildJsonArray {
            dataRows.forEach { row ->
                add(buildJsonObject {
                    headers.forEachIndexed { colIdx, key ->
                        val cell = row.getOrNull(colIdx)?.trim().orEmpty()
                        val asNumber = cell.toLongOrNull()
                        val asDouble = if (asNumber == null) cell.toDoubleOrNull() else null
                        when {
                            cell.equals("true", ignoreCase = true) -> put(key, JsonPrimitive(true))
                            cell.equals("false", ignoreCase = true) -> put(key, JsonPrimitive(false))
                            asNumber != null -> put(key, JsonPrimitive(asNumber))
                            asDouble != null -> put(key, JsonPrimitive(asDouble))
                            else -> put(key, JsonPrimitive(cell))
                        }
                    }
                })
            }
        }

        val outputJson = prettyJson.encodeToString(JsonArray.serializer(), jsonArray)
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "json")
        outFile.writeText(outputJson)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "application/json",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = "${dataRows.size} records converted"
        )
    }

    private fun convertJsonToCsv(
        item: SelectedFileItem,
        jsonText: String,
        baseName: String,
        outputDir: File
    ): ConversionOutput {
        val root = prettyJson.parseToJsonElement(jsonText.trim())
        val objects: List<JsonObject> = when (root) {
            is JsonArray -> root.jsonArray.map {
                it as? JsonObject ?: throw IllegalArgumentException("JSON array elements must be objects.")
            }
            is JsonObject -> listOf(root.jsonObject)
            else -> throw IllegalArgumentException("JSON must be an object or array of objects.")
        }
        if (objects.isEmpty()) {
            throw IllegalArgumentException("JSON array is empty.")
        }

        val headers = linkedSetOf<String>()
        objects.forEach { obj -> headers.addAll(obj.keys) }
        val headerList = headers.toList()

        val csv = buildString {
            appendLine(headerList.joinToString(",") { escapeCsvCell(it) })
            objects.forEach { obj ->
                val line = headerList.joinToString(",") { key ->
                    val value = obj[key]
                    val str = when (value) {
                        null, JsonNull -> ""
                        is JsonPrimitive -> value.content
                        else -> value.toString()
                    }
                    escapeCsvCell(str)
                }
                appendLine(line)
            }
        }

        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "csv")
        outFile.writeText(csv)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "text/csv",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = "${objects.size} rows • ${headerList.size} columns"
        )
    }

    private fun formatJson(
        item: SelectedFileItem,
        jsonText: String,
        baseName: String,
        minify: Boolean,
        outputDir: File
    ): ConversionOutput {
        val element = prettyJson.parseToJsonElement(jsonText.trim())
        val formatted = if (minify) {
            compactJson.encodeToString(JsonElement.serializer(), element)
        } else {
            prettyJson.encodeToString(JsonElement.serializer(), element)
        }
        val suffix = if (minify) "_min" else "_formatted"
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName + suffix, "json")
        outFile.writeText(formatted)
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "application/json",
            sizeBytes = outFile.length(),
            sourceSizeBytes = item.sizeBytes,
            extraNote = if (minify) "Minified JSON" else "Pretty-printed JSON"
        )
    }

    private fun parseCsvRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val normalized = input.replace("\r\n", "\n").replace("\r", "\n")

        while (i < normalized.length) {
            val c = normalized[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < normalized.length && normalized[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    currentRow += cell.toString()
                    cell.clear()
                }
                c == '\n' && !inQuotes -> {
                    currentRow += cell.toString()
                    cell.clear()
                    if (currentRow.any { it.isNotEmpty() }) {
                        rows += currentRow.toList()
                    }
                    currentRow.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += cell.toString()
            if (currentRow.any { it.isNotEmpty() }) {
                rows += currentRow.toList()
            }
        }
        return rows
    }

    private fun escapeCsvCell(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun wrapHtmlDocument(title: String, body: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>${escapeHtml(title)}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; max-width: 760px; margin: 2rem auto; padding: 0 1.25rem; line-height: 1.65; color: #0f172a; background: #ffffff; }
            h1, h2, h3 { color: #162356; line-height: 1.25; }
            code { background: #f1f5f9; padding: 0.15rem 0.35rem; border-radius: 4px; font-family: monospace; font-size: 0.9em; }
            pre { background: #f1f5f9; padding: 1rem; border-radius: 8px; overflow-x: auto; }
            blockquote { border-left: 4px solid #2952ff; margin: 1rem 0; padding: 0.25rem 1rem; color: #334155; background: #f8fafc; }
          </style>
        </head>
        <body>
        $body
        </body>
        </html>
    """.trimIndent()
}
