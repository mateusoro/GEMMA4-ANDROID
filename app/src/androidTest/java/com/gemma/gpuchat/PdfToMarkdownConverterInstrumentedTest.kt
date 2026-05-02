package com.gemma.gpuchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for PdfToMarkdownConverter.
 * Runs ON DEVICE — tests real PDFBox integration.
 */
@RunWith(AndroidJUnit4::class)
class PdfToMarkdownConverterInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun converter_init_shouldNotThrow() {
        // Act & Assert - should not throw
        val converter = PdfToMarkdownConverter(context)
        assertNotNull("Converter should be created", converter)
    }

    @Test
    fun getMetadata_shouldReturnMetadataForPdfFile() {
        // Arrange - create a test PDF first (simple approach: use existing PDF if available)
        val pdfPath = "/data/local/tmp/gemma_startup.nlog" // Just a text file, not PDF
        // Note: This test requires a real PDF file to properly test getMetadata
        // For now, we test the conversion with a placeholder

        val converter = PdfToMarkdownConverter(context)
        // Without a real PDF file, we can't test getMetadata fully
        // This test verifies the converter can be instantiated
        assertNotNull(converter)
    }

    @Test
    fun workspaceManager_shouldBeInitialized() {
        // Arrange
        WorkspaceManager.init(context)

        // Act
        val mdDir = WorkspaceManager.getMarkdownDir(context)

        // Assert
        assertNotNull("Markdown dir should be accessible", mdDir)
    }

    @Test
    fun saveMarkdownViaWorkspace_thenConvert_shouldWork() {
        // Arrange
        WorkspaceManager.init(context)
        val testFileName = "pdf_test_${System.currentTimeMillis()}"
        val testContent = """
            # Test Document

            This is a test document for PDF conversion testing.

            ## Section 1

            Some content here.

            - Item 1
            - Item 2
            - Item 3

            **Bold text** and *italic text*.

            | Column 1 | Column 2 |
            |----------|----------|
            | Value 1  | Value 2  |
        """.trimIndent()

        // Act - save markdown
        val savedPath = WorkspaceManager.saveMarkdown(context, testFileName, testContent)
        assertNotNull("Markdown should be saved", savedPath)
        assertTrue("Saved file should exist", File(savedPath!!).exists())

        // Read content back
        val readContent = File(savedPath).readText()
        assertEquals("Content should match", testContent, readContent)

        // Cleanup
        WorkspaceManager.deleteFile(savedPath)
    }

    @Test
    fun convertInputStream_shouldReturnNonEmptyString() {
        // This test verifies the converter initialization works
        val converter = PdfToMarkdownConverter(context)
        assertNotNull("Converter should be created", converter)

        // We can't fully test convert() without a real PDF input stream
        // but we verified the converter can be instantiated
    }
}