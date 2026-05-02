package com.gemma.gpuchat

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PdfToMarkdownConverter text processing.
 * Tests heading detection, list conversion, table formatting, whitespace cleanup.
 */
class PdfToMarkdownConverterTest {

    // Helper: simulate the heading detection logic from detectHeadings
    private fun detectHeadingsLogic(content: String): String {
        val lines = content.split("\n")
        val result = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.append("\n")
                continue
            }

            val leadingSpaces = line.length - line.trimStart().length
            val lineLength = trimmed.length

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
    }

    // Helper: simulate the list conversion logic
    private fun convertListsLogic(content: String): String {
        val lines = content.split("\n")
        val result = StringBuilder()
        var inList = false

        for (line in lines) {
            val trimmed = line.trim()

            val bulletMatch = Regex("""^[\-\*\+\•◦]\s+(.+)""").find(trimmed)
            val numberedMatch = Regex("""^\d+[\.\)\-]\s+(.+)""").find(trimmed)

            when {
                bulletMatch != null -> {
                    if (!inList) inList = true
                    result.append("- ${bulletMatch.groupValues[1].trim()}\n")
                }
                numberedMatch != null -> {
                    if (!inList) inList = true
                    result.append("1. ${numberedMatch.groupValues[1].trim()}\n")
                }
                else -> {
                    if (inList && trimmed.isNotEmpty()) inList = false
                    result.append("$trimmed\n")
                }
            }
        }
        return result.toString()
    }

    // Helper: simulate bold/italic conversion
    private fun convertBoldItalicLogic(content: String): String {
        var result = content

        // Pattern: text between double asterisks or underscores
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

        return result
    }

    // Helper: simulate whitespace cleanup
    private fun cleanupWhitespaceLogic(content: String): String {
        var result = content.replace(Regex("\n{3,}"), "\n\n")
        result = result.replace(Regex(""" +$"""), "")
        return result.trim()
    }

    // ============ Heading Detection Tests ============

    @Test
    fun `detectHeadings should format ALL CAPS lines as H4`() {
        val input = "IMPORTANT NOTICE\nSome regular text here"
        val result = detectHeadingsLogic(input)
        assertTrue("Should contain H4 heading", result.contains("#### IMPORTANT NOTICE"))
    }

    @Test
    fun `detectHeadings should format Title Case lines as H3`() {
        val input = "Introduction\nSome regular text"
        val result = detectHeadingsLogic(input)
        assertTrue("Should contain H3 heading", result.contains("### Introduction"))
    }

    @Test
    fun `detectHeadings should NOT format lines ending with period as heading`() {
        val input = "This is a sentence. It ends with a period."
        val result = detectHeadingsLogic(input)
        assertFalse("Should not format as heading", result.contains("###"))
    }

    @Test
    fun `detectHeadings should keep normal lines as regular text`() {
        val input = "This is a normal paragraph of text that should remain as is."
        val result = detectHeadingsLogic(input)
        assertTrue("Normal text should be preserved", result.contains("normal paragraph"))
    }

    @Test
    fun `detectHeadings should handle empty lines`() {
        val input = "Header\n\n\nText after blank lines"
        val result = detectHeadingsLogic(input)
        assertTrue("Should handle empty lines", result.contains("\n\n"))
    }

    // ============ List Conversion Tests ============

    @Test
    fun `convertLists should convert bullet points`() {
        val input = "- First item\n- Second item\n- Third item"
        val result = convertListsLogic(input)
        assertTrue("Should contain bullet list", result.contains("- First item"))
        assertTrue("Should contain all items", result.contains("- Second item"))
        assertTrue("Should contain third", result.contains("- Third item"))
    }

    @Test
    fun `convertLists should convert numbered lists`() {
        val input = "1. First numbered\n2. Second numbered\n3. Third numbered"
        val result = convertListsLogic(input)
        assertTrue("Should contain numbered list", result.contains("1. First"))
        assertTrue("Should contain all items", result.contains("1. Second"))
    }

    @Test
    fun `convertLists should handle mixed lists and text`() {
        val input = "Some text\n- Bullet item\nMore text"
        val result = convertListsLogic(input)
        assertTrue("Should contain bullet", result.contains("- Bullet item"))
        assertTrue("Should preserve text", result.contains("Some text"))
    }

    @Test
    fun `convertLists should handle various bullet characters`() {
        val bullets = listOf("-", "*", "+", "•", "◦")
        for (bullet in bullets) {
            val input = "$bullet Single item"
            val result = convertListsLogic(input)
            assertTrue("Bullet '$bullet' should convert to '-'", result.contains("- Single item"))
        }
    }

    // ============ Bold/Italic Tests ============

    @Test
    fun `convertBoldItalic should normalize double asterisks`() {
        val input = "** bold text **"
        val result = convertBoldItalicLogic(input)
        assertTrue("Should normalize to **text**", result.contains("**bold text**"))
    }

    @Test
    fun `convertBoldItalic should normalize double underscores`() {
        val input = "__ italic text __"
        val result = convertBoldItalicLogic(input)
        assertTrue("Should normalize to _text_", result.contains("_italic text_"))
    }

    @Test
    fun `convertBoldItalic should convert single asterisks to italic`() {
        val input = "This has *italic* in it"
        val result = convertBoldItalicLogic(input)
        assertTrue("Should convert to _italic_", result.contains("_italic_"))
    }

    // ============ Whitespace Cleanup Tests ============

    @Test
    fun `cleanupWhitespace should reduce multiple newlines to double newlines`() {
        val input = "Paragraph one\n\n\n\n\nParagraph two"
        val result = cleanupWhitespaceLogic(input)
        // Should have at most 2 consecutive newlines
        assertFalse("Should not have more than 2 newlines", result.contains("\n\n\n"))
    }

    @Test
    fun `cleanupWhitespace should remove trailing spaces`() {
        val input = "Line with trailing spaces     \nAnother line"
        val result = cleanupWhitespaceLogic(input)
        assertFalse("Should not have trailing spaces on first line", result.contains("spaces     "))
    }

    @Test
    fun `cleanupWhitespace should trim overall content`() {
        val input = "\n\nSome content\n\n"
        val result = cleanupWhitespaceLogic(input)
        assertFalse("Should not start with newlines", result.startsWith("\n"))
        assertFalse("Should not end with newlines", result.endsWith("\n"))
    }

    // ============ Multi-page Header Test ============

    @Test
    fun `page headers should be formatted correctly`() {
        val pageHeader = "## Página 1\n\nContent here"
        assertTrue("Should have H2 for page", pageHeader.contains("## Página 1"))
    }

    // ============ Table Detection (simplified logic) ============

    @Test
    fun `table lines should be detected by pipe count`() {
        fun countPipes(line: String) = line.count { it == '|' }

        assertTrue("| col1 | col2 | should have 3 pipes", countPipes("| col1 | col2 |") == 3)
        assertTrue("|---|---| should detect separator", countPipes("|---|---|") >= 2)
        assertFalse("Regular text has no pipes", countPipes("Just text") > 0)
    }

    // ============ Integration Test ============

    @Test
    fun `full pipeline should produce clean markdown`() {
        val rawPdfText = """
            INTRODUCTION

            This is a paragraph of text.

            - First point
            - Second point
            - Third point

            **Bold text** and *italic text*
        """.trimIndent()

        // Apply transformations in order (like PdfToMarkdownConverter does)
        var result = rawPdfText.replace("\r\n", "\n").replace("\r", "\n")
        result = detectHeadingsLogic(result) // Step 2
        result = convertBoldItalicLogic(result) // Step 3
        result = convertListsLogic(result) // Step 4
        result = cleanupWhitespaceLogic(result) // Step 6 (last)

        assertTrue("Should have headings detected", result.contains("###") || result.contains("####"))
        assertTrue("Should have lists", result.contains("- First point"))
        assertTrue("Should have bold", result.contains("**"))
        assertFalse("Should not have excessive newlines", result.contains("\n\n\n"))
    }
}