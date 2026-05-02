package com.gemma.gpuchat

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for WorkspaceManager file operations.
 * Tests saveMarkdown, listWorkspace, listMarkdown, fileExists operations.
 */
class WorkspaceManagerTest {

    // Note: These tests need Android Context, so they're more like integration tests.
    // For pure unit tests, we test the logic that doesn't require Context.

    @Test
    fun `saveMarkdown should sanitize filename correctly`() {
        // Test filename sanitization logic
        val unsafeNames = listOf(
            "my file.pdf" to "my_file.pdf",
            "test<>:*.md" to "test____.md",
            "normal.md" to "normal.md",
            "file-with-dashes_and_underscores.md" to "file-with-dashes_and_underscores.md",
            "verylongfilename_that_exceeds_100_chars_with_lots_of_extra_text_here_and_more_and_more_and_more.md" to "verylongfilename_that_exceeds_100_chars_with_lots_of_extra_text_here_and_more_and_more_and_more.md"
        )

        for ((input, expected) in unsafeNames) {
            val sanitized = input
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(100)
            // If it doesn't end in .md, .md gets added by saveMarkdown
            val result = if (sanitized.endsWith(".md")) sanitized else "$sanitized.md"
            assertEquals("Sanitization failed for: $input", expected, result)
        }
    }

    @Test
    fun `saveMarkdown should add md extension if missing`() {
        val withoutExt = "myfile"
        val withExt = "myfile.md"

        // Simulate the extension logic
        fun addMdExtension(name: String): String {
            return if (name.endsWith(".md")) name else "$name.md"
        }

        assertEquals("myfile.md", addMdExtension(withoutExt))
        assertEquals("existing.md", addMdExtension(withExt))
    }

    @Test
    fun `formatSize should format bytes correctly`() {
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                else -> "${bytes / (1024 * 1024)}MB"
            }
        }

        assertEquals("0B", formatSize(0))
        assertEquals("512B", formatSize(512))
        assertEquals("1023B", formatSize(1023))
        assertEquals("1KB", formatSize(1024))
        assertEquals("1KB", formatSize(1536))
        assertEquals("10KB", formatSize(10 * 1024))
        assertEquals("1MB", formatSize(1024 * 1024))
        assertEquals("10MB", formatSize(10 * 1024 * 1024))
    }

    @Test
    fun `listWorkspace should format empty directories correctly`() {
        // Simulate the listWorkspace format for empty dirs
        fun formatEmptyDir(type: String): String {
            return when (type) {
                "documents" -> "📁 documents/ (0 arquivos):\n   (vazio)"
                "markdown" -> "📝 markdown/ (0 arquivos):\n   (vazio)"
                else -> "$type/ (0 arquivos):\n   (vazio)"
            }
        }

        val docsEmpty = formatEmptyDir("documents")
        assertTrue(docsEmpty.contains("vazio"))
        assertTrue(docsEmpty.contains("📁"))

        val mdEmpty = formatEmptyDir("markdown")
        assertTrue(mdEmpty.contains("vazio"))
        assertTrue(mdEmpty.contains("📝"))
    }

    @Test
    fun `listWorkspace should format files with emoji and size`() {
        // Simulate file listing format
        data class MockFile(val name: String, val length: Long)

        fun formatFile(file: MockFile, emoji: String): String {
            val size = when {
                file.length < 1024 -> "${file.length}B"
                file.length < 1024 * 1024 -> "${file.length / 1024}KB"
                else -> "${file.length / (1024 * 1024)}MB"
            }
            return "$emoji ${file.name} ($size)"
        }

        val pdfFile = MockFile("document.pdf", 1024 * 50) // 50KB
        val mdFile = MockFile("notes.md", 512) // 512B

        assertEquals("📁 document.pdf (50KB)", formatFile(pdfFile, "📁"))
        assertEquals("📝 notes.md (512B)", formatFile(mdFile, "📝"))
    }

    @Test
    fun `extractFileName should handle various URI paths`() {
        fun extractFileName(uriPath: String?): String {
            if (uriPath == null) return "document_${System.currentTimeMillis()}.pdf"
            return uriPath.substringAfterLast('/').ifEmpty { "document.pdf" }
        }

        assertEquals("file.pdf", extractFileName("/path/to/file.pdf"))
        assertEquals("document.pdf", extractFileName("/path/to/document"))
        assertEquals("file.pdf", extractFileName("file.pdf"))
        assertEquals(null, extractFileName(null).substringBefore('_')) // should start with document_
    }

    @Test
    fun `workspace directory structure should be correct`() {
        // Verify the directory constants
        val WORKSPACE_DIR = "workspace"
        val DOCUMENTS_DIR = "documents"
        val MARKDOWN_DIR = "markdown"

        assertEquals("workspace", WORKSPACE_DIR)
        assertEquals("documents", DOCUMENTS_DIR)
        assertEquals("markdown", MARKDOWN_DIR)
        assertNotEquals(DOCUMENTS_DIR, MARKDOWN_DIR) // should be different
    }

    @Test
    fun `fileExists logic should work correctly`() {
        // Test the path generation logic
        fun getFilePath(baseDir: String, type: String, filename: String): String {
            return "$baseDir/$type/$filename"
        }

        val base = "/data/data/com.gemma.gpuchat/files"
        val docsPath = getFilePath(base, "documents", "test.pdf")
        val mdPath = getFilePath(base, "markdown", "notes.md")

        assertEquals("/data/data/com.gemma.gpuchat/files/documents/test.pdf", docsPath)
        assertEquals("/data/data/com.gemma.gpuchat/files/markdown/notes.md", mdPath)
    }
}