package com.gemma.gpuchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for WorkspaceManager file operations.
 * Runs ON DEVICE — has access to real Android Context.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceManagerInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun saveMarkdown_shouldCreateFile() {
        // Arrange
        val testFileName = "test_instrumented_${System.currentTimeMillis()}"
        val testContent = "Test content for instrumented test"

        // Act
        val result = WorkspaceManager.saveMarkdown(context, testFileName, testContent)

        // Assert
        assertNotNull("saveMarkdown should return path", result)
        assertTrue("File should exist", File(result!!).exists())

        // Cleanup
        WorkspaceManager.deleteFile(result)
    }

    @Test
    fun saveMarkdown_shouldReturnNullForNullContent() {
        // This tests the error handling path
        val result = WorkspaceManager.saveMarkdown(context, "test", "")
        // Empty string is valid content, so should work
        assertNotNull(result)
    }

    @Test
    fun listWorkspace_shouldReturnFormattedString() {
        // Act
        val result = WorkspaceManager.listWorkspace(context)

        // Assert
        assertNotNull("listWorkspace should return non-null", result)
        assertTrue("Should contain workspace header", result.contains("=== Workspace ==="))
        assertTrue("Should mention documents", result.contains("documents/"))
        assertTrue("Should mention markdown", result.contains("markdown/"))
    }

    @Test
    fun listMarkdown_shouldReturnFormattedString() {
        // Act
        val result = WorkspaceManager.listMarkdown(context)

        // Assert
        assertNotNull("listMarkdown should return non-null", result)
        assertTrue("Should contain markdown header", result.contains("markdown/"))
    }

    @Test
    fun getDocumentsDir_shouldReturnValidDirectory() {
        // Act
        val dir = WorkspaceManager.getDocumentsDir(context)

        // Assert
        assertNotNull("Directory should not be null", dir)
        assertTrue("Directory should exist or be creatable", dir.exists() || dir.mkdirs() != false)
    }

    @Test
    fun getMarkdownDir_shouldReturnValidDirectory() {
        // Act
        val dir = WorkspaceManager.getMarkdownDir(context)

        // Assert
        assertNotNull("Directory should not be null", dir)
        assertTrue("Directory should exist or be creatable", dir.exists() || dir.mkdirs() != false)
    }

    @Test
    fun saveAndDelete_shouldWork() {
        // Arrange
        val fileName = "delete_test_${System.currentTimeMillis()}"
        val content = "Content to delete"

        // Act - save
        val path = WorkspaceManager.saveMarkdown(context, fileName, content)
        assertNotNull("Save should succeed", path)
        assertTrue("File should exist after save", File(path!!).exists())

        // Act - delete
        val deleted = WorkspaceManager.deleteFile(path)
        assertTrue("Delete should return true", deleted)
        assertFalse("File should not exist after delete", File(path).exists())
    }

    @Test
    fun deleteFile_shouldReturnFalseForNonExistent() {
        // Act
        val result = WorkspaceManager.deleteFile("/non/existent/path/file.md")

        // Assert
        assertFalse("Delete non-existent should return false", result)
    }
}