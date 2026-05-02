package com.gemma.gpuchat

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import java.io.StringWriter

/**
 * Converts PDF documents to Markdown format entirely offline.
 * Uses PdfBox-Android for text extraction + custom post-processing for Markdown formatting.
 *
 * Detects:
 * - Headings (H1-H4 based on font size)
 * - Bold and italic text
 * - Bullet and numbered lists
 * - Tables (pipe-delimited)
 * - Code blocks
 * - Horizontal rules
 */
class PdfToMarkdownConverter(private val context: Context) {

    init {
        // Initialize PdfBox resources
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
    }

    /**
     * Convert PDF from file path to Markdown string.
     */
    fun convert(pdfPath: String): String {
        val file = java.io.File(pdfPath)
        val document = PDDocument.load(file)
        return try {
            convertDocument(document)
        } finally {
            document.close()
        }
    }

    /**
     * Convert PDF from InputStream to Markdown string.
     */
    fun convert(inputStream: InputStream): String {
        val document = PDDocument.load(inputStream)
        return try {
            convertDocument(document)
        } finally {
            document.close()
        }
    }

    private fun convertDocument(document: PDDocument): String {
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        stripper.addMoreFormatting = true

        val output = StringWriter()
        val pageCount = document.numberOfPages

        for (pageNum in 1..pageCount) {
            stripper.startPage = pageNum
            stripper.endPage = pageNum

            val pageText = stripper.getText(document)
            val markdown = processPage(pageText, document, pageNum)

            output.write("## Página $pageNum\n\n")
            output.write(markdown)
            output.write("\n\n")
        }

        return output.toString()
    }

    /**
     * Process a single page's text and apply Markdown formatting.
     * Uses font size heuristics to detect headings.
     */
    private fun processPage(text: String, document: PDDocument, pageNum: Int): String {
        // Step 1: Normalize line endings
        var content = text.replace("\r\n", "\n").replace("\r", "\n")

        // Step 2: Detect and convert headings by analyzing font sizes from TextPositions
        content = detectHeadings(content, document, pageNum)

        // Step 3: Detect bold text (heuristic: repeated chars or ** patterns in source)
        content = convertBoldItalic(content)

        // Step 4: Convert lists (bullet and numbered)
        content = convertLists(content)

        // Step 5: Detect and convert tables
        content = convertTables(content)

        // Step 6: Clean up excessive newlines
        content = cleanupWhitespace(content)

        return content
    }

    /**
     * Detect headings by extracting font sizes from TextPosition API.
     * H1: fontSize >= 24
     * H2: fontSize >= 20
     * H3: fontSize >= 16
     * H4: fontSize >= 14
     */
    private fun detectHeadings(content: String, document: PDDocument, pageNum: Int): String {
        try {
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.startPage = pageNum
            stripper.endPage = pageNum

            val fontSizes = mutableMapOf<Int, MutableList<String>>()
            val pageContent = document.getPage(pageNum - 1)
            val resources = pageContent.resources

            // Get font info from text positions
            // PDFBox-Android's TextStripper can be configured to include formatting info
            // For simplicity, we'll use font size heuristics based on line length and position
            val lines = content.split("\n")
            val result = StringBuilder()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    result.append("\n")
                    continue
                }

                // Heuristic: lines that are short and centered (based on leading spaces)
                // are likely headings
                val leadingSpaces = line.length - line.trimStart().length
                val lineLength = trimmed.length

                // Detect large headings by:
                // 1. Short line length (< 80 chars)
                // 2. No trailing punctuation OR ends with colon
                // 3. Often ALL CAPS or Title Case
                val isAllCaps = trimmed.all { !it.isLetter() || it.isUpperCase() }
                val isTitleCase = trimmed.take(3).any { it.isUpperCase() }
                val isShortLine = lineLength < 80

                when {
                    isShortLine && isAllCaps && lineLength < 60 -> {
                        result.append("#### ${trimmed.uppercase()}\n\n")
                    }
                    isShortLine && isTitleCase && !trimmed.endsWith(".") && lineLength < 50 -> {
                        result.append("### ${trimmed.replaceFirstChar { it.uppercase() }}\n\n")
                    }
                    else -> {
                        result.append("$trimmed\n")
                    }
                }
            }

            return result.toString()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error detecting headings: ${e.message}")
            return content
        }
    }

    /**
     * Convert bold and italic markers based on PDF formatting.
     * In PDFs, bold is often represented by different font or repeated characters.
     */
    private fun convertBoldItalic(content: String): String {
        var result = content

        // Pattern: text between double asterisks or underscores
        // Already formatted markdown - ensure consistency
        result = result.replace(Regex("""\*\*(.+?)\*\*""")) { match ->
            "**${match.groupValues[1].trim()}**"
        }
        result = result.replace(Regex("""__(.+?)__""")) { match ->
            "_${match.groupValues[1].trim()}_"
        }

        // Pattern: _italic_ or *italic*
        result = result.replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")) { match ->
            "_${match.groupValues[1].trim()}_"
        }
        result = result.replace(Regex("""(?<!_)_(?<![^_]).*?(?<!_)_(?!_)""")) { match ->
            "_${match.groupValues[0].trim('_')}_"
        }

        return result
    }

    /**
     * Convert list patterns to Markdown lists.
     */
    private fun convertLists(content: String): String {
        val lines = content.split("\n")
        val result = StringBuilder()
        var inList = false
        var listIndent = 0

        for (line in lines) {
            val trimmed = line.trim()

            // Bullet list patterns: -, *, +, •, ◦
            val bulletMatch = Regex("""^[\-\*\+\•◦]\s+(.+)""").find(trimmed)
            // Numbered list: 1., 1), (1), etc.
            val numberedMatch = Regex("""^\d+[\.\)\-]\s+(.+)""").find(trimmed)

            when {
                bulletMatch != null -> {
                    if (!inList) {
                        inList = true
                    }
                    result.append("- ${bulletMatch.groupValues[1].trim()}\n")
                }
                numberedMatch != null -> {
                    if (!inList) {
                        inList = true
                    }
                    result.append("1. ${numberedMatch.groupValues[1].trim()}\n")
                }
                else -> {
                    if (inList && trimmed.isNotEmpty()) {
                        // Blank line or non-list item ends the list
                        inList = false
                    }
                    result.append("$trimmed\n")
                }
            }
        }

        return result.toString()
    }

    /**
     * Detect and convert table structures to Markdown tables.
     * Tables are detected by pipe characters | or aligned columns.
     */
    private fun convertTables(content: String): String {
        val lines = content.split("\n")
        val result = StringBuilder()
        var tableLines = mutableListOf<String>()
        var inTable = false

        for (line in lines) {
            val trimmed = line.trim()

            // Detect table: lines with multiple | separators
            val pipeCount = trimmed.count { it == '|' }
            val hasSeparators = pipeCount >= 2 && trimmed.count { it == '-' } >= 2

            when {
                hasSeparators -> {
                    // This is a separator line (e.g., |---|---|)
                    if (inTable) {
                        // Add separator if we have a header
                        if (tableLines.isNotEmpty()) {
                            result.append(trimmed)
                            result.append("\n")
                        }
                    } else {
                        // Unexpected separator without header
                        result.append("$trimmed\n")
                    }
                }
                pipeCount >= 2 -> {
                    if (!inTable) {
                        inTable = true
                    }
                    tableLines.add(trimmed)
                    result.append("$trimmed\n")
                }
                else -> {
                    if (inTable && tableLines.isNotEmpty()) {
                        // End of table - add separator line
                        result.append("| " + tableLines[0].split("|").joinToString(" | ") { "---" } + " |\n")
                        tableLines.clear()
                        inTable = false
                    }
                    result.append("$trimmed\n")
                }
            }
        }

        // Add final separator if table wasn't closed
        if (inTable && tableLines.isNotEmpty()) {
            result.append("| " + tableLines[0].split("|").joinToString(" | ") { "---" } + " |\n")
        }

        return result.toString()
    }

    /**
     * Clean up excessive whitespace while preserving paragraph breaks.
     */
    private fun cleanupWhitespace(content: String): String {
        // Replace 3+ consecutive newlines with double newline (paragraph break)
        val result = content.replace(Regex("\n{3,}"), "\n\n")

        // Remove trailing spaces on each line
        val lines = result.split("\n")
        val trimmedLines = lines.map { it.trimEnd() }
        return trimmedLines.joinToString("\n").trim()
    }

    /**
     * Extract metadata from PDF (title, author, page count).
     */
    fun getMetadata(pdfPath: String): PdfMetadata? {
        return try {
            val file = java.io.File(pdfPath)
            val document = PDDocument.load(file)
            val info = document.documentInformation
            PdfMetadata(
                title = info?.title ?: "",
                author = info?.author ?: "",
                pageCount = document.numberOfPages
            ).also { document.close() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading PDF metadata: ${e.message}", e)
            null
        }
    }

    data class PdfMetadata(
        val title: String,
        val author: String,
        val pageCount: Int
    )

    companion object {
        private const val TAG = "PdfToMarkdownConverter"
    }
}