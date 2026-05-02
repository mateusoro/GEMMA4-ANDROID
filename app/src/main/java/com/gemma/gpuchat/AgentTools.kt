package com.gemma.gpuchat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.net.toUri
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "AgentTools"

/**
 * ToolSet para o Gemma Chat Agent.
 * Cada método @Tool é chamado automaticamente pelo LiteRT-LM quando o modelo decide usar a tool.
 *
 * Para registar: tools = listOf(tool(AgentTools()))
 */
class AgentTools private constructor() : ToolSet {

    companion object {
        fun create(): AgentTools = AgentTools()
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Location & Calendar
    // ──────────────────────────────────────────────────────────────

    /** Mostra uma localização no mapa. */
    @Tool(description = "Shows a location on the map")
    fun showLocationOnMap(
        context: Context,
        @ToolParam(description = "The location to search for. May be the name of a place, a business, or an address.") location: String
    ): Map<String, String> {
        Log.d(TAG, "showLocationOnMap: $location")
        try {
            val encoded = URLEncoder.encode(location, StandardCharsets.UTF_8.toString())
            val intent = Intent(Intent.ACTION_VIEW).apply { data = "geo:0,0?q=$encoded".toUri() }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show location", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "location_shown", "location" to location)
    }

    /** Cria um evento no calendário. */
    @Tool(description = "Creates a new calendar event")
    fun createCalendarEvent(
        context: Context,
        @ToolParam(description = "The date and time of the event in the format YYYY-MM-DDTHH:MM:SS") datetime: String,
        @ToolParam(description = "The title of the event") title: String
    ): Map<String, String> {
        Log.d(TAG, "createCalendarEvent: $datetime - $title")
        var ms = System.currentTimeMillis()
        try {
            val localDateTime = java.time.LocalDateTime.parse(datetime)
            val zone = java.time.ZoneId.systemDefault()
            ms = localDateTime.atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse datetime: '$datetime'", e)
        }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms + 3600000)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create calendar event", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "calendar_event_created", "title" to title, "datetime" to datetime)
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Workspace / File operations
    // ──────────────────────────────────────────────────────────────

    /** Lista os arquivos no workspace (documents e markdown). */
    @Tool(description = "Lists all files in the workspace directory")
    fun listWorkspace(context: Context): Map<String, String> {
        Log.d(TAG, "listWorkspace called")
        val result = WorkspaceManager.listWorkspace(context)
        return mapOf("result" to "success", "files" to result)
    }

    /** Lista apenas arquivos Markdown no workspace. */
    @Tool(description = "Lists markdown files in the workspace")
    fun listMarkdown(context: Context): Map<String, String> {
        Log.d(TAG, "listMarkdown called")
        val result = WorkspaceManager.listMarkdown(context)
        return mapOf("result" to "success", "markdown_files" to result)
    }

    /** Lê o conteúdo de um arquivo no workspace. Pode ser usado para ler arquivos .md, .txt, código, etc. */
    @Tool(description = "Reads the content of a file from the workspace. Use this to read documents, markdown files, or code files.")
    fun readWorkspaceFile(
        context: Context,
        @ToolParam(description = "The name of the file to read (e.g., 'documento.md' or 'documento.pdf')") filename: String
    ): Map<String, String> {
        Log.d(TAG, "readWorkspaceFile: $filename")
        var content: String? = null

        // Check markdown directory first
        val mdDir = WorkspaceManager.getMarkdownDir(context)
        val mdFile = java.io.File(mdDir, filename)
        if (mdFile.exists()) {
            content = mdFile.readText()
        }
        // Check documents directory
        if (content == null) {
            val docsDir = WorkspaceManager.getDocumentsDir(context)
            val docFile = java.io.File(docsDir, filename)
            if (docFile.exists()) {
                content = "[Binary file: ${filename}]"
            }
        }

        return if (content != null) {
            mapOf("result" to "success", "filename" to filename, "content" to content)
        } else {
            mapOf("result" to "error", "message" to "File not found: $filename")
        }
    }

    /** Salva um arquivo Markdown no workspace. */
    @Tool(description = "Saves a markdown file to the workspace with the given filename and content.")
    fun saveMarkdownFile(
        context: Context,
        @ToolParam(description = "The filename for the markdown file (e.g., 'nota.md')") filename: String,
        @ToolParam(description = "The markdown content to save") content: String
    ): Map<String, String> {
        Log.d(TAG, "saveMarkdownFile: $filename")
        val path = WorkspaceManager.saveMarkdown(context, filename.removeSuffix(".md"), content)
        return if (path != null) {
            mapOf("result" to "success", "filename" to filename, "path" to path)
        } else {
            mapOf("result" to "error", "message" to "Failed to save file: $filename")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Information
    // ──────────────────────────────────────────────────────────────

    /** Retorna information about the current device and time. */
    @Tool(description = "Returns current device information including time, date, and available memory.")
    fun getDeviceInfo(context: Context): Map<String, String> {
        val memInfo = LlmChatModelHelper.getMemoryUsage()
        val sysMem = LlmChatModelHelper.getSystemMemory(context)
        val now = java.time.LocalDateTime.now()
        val dateTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val dayOfWeek = now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE"))

        return mapOf(
            "result" to "success",
            "datetime" to dateTime,
            "day_of_week" to dayOfWeek,
            "app_memory_mb" to "${memInfo.appUsedMb}/${memInfo.appTotalMb}",
            "device_memory_mb" to "${sysMem.usedMb}/${sysMem.totalMb}",
            "model_size_mb" to "${memInfo.modelSizeMb}"
        )
    }
}