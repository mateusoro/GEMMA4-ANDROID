package com.gemma.gpuchat.gallery

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.gemma.gpuchat.WorkspaceManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "GalleryTools"

/**
 * Gallery-style ToolSet following the exact pattern from Google AI Edge Gallery.
 * Uses onFunctionCalled callback to pass action results back to the ViewModel.
 * Based on: com.google.ai.edge.gallery.customtasks.mobileactions.MobileActionsTools
 */
class GalleryTools(
    private val context: Context,
    private val filesDir: String,
    private val onFunctionCalled: (String, Map<String, String>) -> Unit
) : ToolSet {

    /** Lists all files in the workspace. */
    @Tool(description = "Lists all files in the workspace (documents and markdown)")
    fun listWorkspace(): Map<String, String> {
        Log.d(TAG, "listWorkspace() called")

        val workspaceDir = File("$filesDir/workspace")
        val documentsDir = File(workspaceDir, "documents")
        val markdownDir = File(workspaceDir, "markdown")

        val files = mutableListOf<String>()

        // List documents
        documentsDir.listFiles()?.forEach { file ->
            files.add("${file.name} (${formatFileSize(file.length())})")
        }

        // List markdown files
        markdownDir.listFiles()?.forEach { file ->
            files.add("${file.name} (${formatFileSize(file.length())})")
        }

        val result = if (files.isEmpty()) {
            mapOf("files" to "No files in workspace")
        } else {
            mapOf("files" to files.joinToString("\n"))
        }

        Log.d(TAG, "listWorkspace result: $result")
        onFunctionCalled("listWorkspace", result)
        return result
    }

    /** Lists only markdown files. */
    @Tool(description = "Lists only .md files in the workspace")
    fun listMarkdown(): Map<String, String> {
        Log.d(TAG, "listMarkdown() called")

        val markdownDir = File("$filesDir/workspace/markdown")
        val files = markdownDir.listFiles()?.map { "${it.name} (${formatFileSize(it.length())})" } ?: emptyList()

        val result = if (files.isEmpty()) {
            mapOf("files" to "No markdown files")
        } else {
            mapOf("files" to files.joinToString("\n"))
        }

        Log.d(TAG, "listMarkdown result: $result")
        onFunctionCalled("listMarkdown", result)
        return result
    }

    /** Reads a file from workspace. */
    @Tool(description = "Reads a file from the workspace")
    fun readWorkspaceFile(
        @ToolParam(description = "The filename to read (with extension)") filename: String
    ): Map<String, String> {
        Log.d(TAG, "readWorkspaceFile($filename) called")

        // Try documents first, then markdown
        var content: String? = null
        val paths = listOf(
            File("$filesDir/workspace/documents/$filename"),
            File("$filesDir/workspace/markdown/$filename"),
            File("$filesDir/workspace/$filename")
        )

        for (path in paths) {
            if (path.exists() && path.isFile) {
                content = path.readText()
                break
            }
        }

        val result = if (content != null) {
            mapOf("content" to content.take(2000), "filename" to filename)
        } else {
            mapOf("error" to "File not found: $filename")
        }

        Log.d(TAG, "readWorkspaceFile result: ${result.keys}")
        onFunctionCalled("readWorkspaceFile", result)
        return result
    }

    /** Saves a markdown file. */
    @Tool(description = "Saves a markdown file to the workspace")
    fun saveMarkdownFile(
        @ToolParam(description = "The filename (should end with .md)") filename: String,
        @ToolParam(description = "The content to save") content: String
    ): Map<String, String> {
        Log.d(TAG, "saveMarkdownFile($filename) called")

        val safeName = if (filename.endsWith(".md")) filename else "$filename.md"
        val file = File("$filesDir/workspace/markdown", safeName)

        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            val result = mapOf("result" to "success", "filename" to safeName, "size" to "${content.length} chars")
            Log.d(TAG, "saveMarkdownFile success: $safeName")
            onFunctionCalled("saveMarkdownFile", result)
            return result
        } catch (e: Exception) {
            val result = mapOf("error" to "Failed to save: ${e.message}")
            Log.e(TAG, "saveMarkdownFile failed: ${e.message}")
            onFunctionCalled("saveMarkdownFile", result)
            return result
        }
    }

    /** Gets device info. */
    @Tool(description = "Gets device information like date, time and memory")
    fun getDeviceInfo(): Map<String, String> {
        Log.d(TAG, "getDeviceInfo() called")

        val now = LocalDateTime.now()
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val dayStr = now.format(DateTimeFormatter.ofPattern("EEEE"))

        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)

        val result = mapOf(
            "datetime" to dateStr,
            "day_of_week" to dayStr,
            "app_memory_mb" to "$usedMem/$totalMem",
            "device_memory_gb" to "${Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024)}"
        )

        Log.d(TAG, "getDeviceInfo result: $result")
        onFunctionCalled("getDeviceInfo", result)
        return result
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }
}

/** Simple system prompt like the Gallery uses - no verbose function descriptions */
fun getGallerySystemPrompt(): String {
    val now = LocalDateTime.now()
    val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    val dayStr = now.format(DateTimeFormatter.ofPattern("EEEE"))

    return """
        You are a model that can do function calling with the following functions.

        Current date and time: $dateStr
        Day of week: $dayStr
    """.trimIndent()
}
