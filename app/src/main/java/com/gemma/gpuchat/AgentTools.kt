package com.gemma.gpuchat

import android.content.Context
import android.content.Intent
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
 * Context é capturado na criação (via create(context)) e guardado como campo de instância —
 * NÃO é passado como parâmetro do método (LiteRT-LM não suporta Context como tipo tool).
 *
 * Para registar: tools = listOf(tool(AgentTools.create(context)))
 */
class AgentTools private constructor() : ToolSet {

    private var appContext: Context? = null

    companion object {
        fun create(context: Context): AgentTools {
            val tools = AgentTools()
            tools.appContext = context.applicationContext
            return tools
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Location & Calendar (sem parâmetro Context)
    // ──────────────────────────────────────────────────────────────

    /** Mostra uma localização no mapa. */
    @Tool(description = "Shows a location on the map")
    fun showLocationOnMap(
        @ToolParam(description = "The location to search for. May be the name of a place, a business, or an address.") location: String
    ): Map<String, String> {
        Log.d(TAG, "showLocationOnMap: $location")
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")
        try {
            val encoded = URLEncoder.encode(location, StandardCharsets.UTF_8.toString())
            val intent = Intent(Intent.ACTION_VIEW).apply { data = "geo:0,0?q=$encoded".toUri() }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show location", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "location_shown", "location" to location)
    }

    /** Cria um evento no calendário. */
    @Tool(description = "Creates a new calendar event")
    fun createCalendarEvent(
        @ToolParam(description = "The date and time of the event in the format YYYY-MM-DDTHH:MM:SS") datetime: String,
        @ToolParam(description = "The title of the event") title: String
    ): Map<String, String> {
        Log.d(TAG, "createCalendarEvent: $datetime - $title")
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")
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
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create calendar event", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "calendar_event_created", "title" to title, "datetime" to datetime)
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Workspace / File operations (sem parâmetro Context)
    // ──────────────────────────────────────────────────────────────

    /** Lista os arquivos no workspace (documents e markdown). */
    @Tool(description = "Lists all files in the workspace directory")
    fun listWorkspace(): Map<String, String> {
        Log.d(TAG, "listWorkspace called")
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")
        val result = WorkspaceManager.listWorkspace(ctx)
        return mapOf("result" to "success", "files" to result)
    }

    /** Lista apenas arquivos Markdown no workspace. */
    @Tool(description = "Lists markdown files in the workspace")
    fun listMarkdown(): Map<String, String> {
        Log.d(TAG, "listMarkdown called")
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")
        val result = WorkspaceManager.listMarkdown(ctx)
        return mapOf("result" to "success", "markdown_files" to result)
    }

    /** Lê o conteúdo de um arquivo no workspace. */
    @Tool(description = "Reads the content of a file from the workspace. Use this to read documents, markdown files, or code files.")
    fun readWorkspaceFile(
        @ToolParam(description = "The name of the file to read. May be just the filename (e.g., 'documento.md') or a path with 'markdown/' or 'documents/' prefix.") filename: String
    ): Map<String, String> {
        Log.d(TAG, "readWorkspaceFile: $filename")
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")

        // Strip prefix if model passes full path like "markdown/document_100.md" or "documents/file.pdf"
        val cleanName = filename
            .removePrefix("markdown/")
            .removePrefix("documents/")
            .removePrefix("/")
        val justFilename = cleanName.substringAfterLast('/')

        var content: String? = null

        val mdDir = WorkspaceManager.getMarkdownDir(ctx)
        val mdFile = java.io.File(mdDir, justFilename)
        if (mdFile.exists()) {
            content = mdFile.readText()
        }
        if (content == null) {
            val docsDir = WorkspaceManager.getDocumentsDir(ctx)
            val docFile = java.io.File(docsDir, justFilename)
            if (docFile.exists()) {
                content = "[Binary file: ${justFilename}]"
            }
        }

        return if (content != null) {
            mapOf("result" to "success", "filename" to justFilename, "content" to content)
        } else {
            mapOf("result" to "error", "message" to "File not found: $justFilename (tried markdown/ and documents/)")
        }
    }

    /** Salva um arquivo Markdown no workspace. */
    @Tool(description = "Saves a markdown file to the workspace with the given filename and content.")
    fun saveMarkdownFile(
        @ToolParam(description = "The filename for the markdown file (e.g., 'nota.md')") filename: String,
        @ToolParam(description = "The markdown content to save") content: String
    ): Map<String, String> {
        Log.d(TAG, "saveMarkdownFile: $filename")
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")
        val path = WorkspaceManager.saveMarkdown(ctx, filename.removeSuffix(".md"), content)
        return if (path != null) {
            mapOf("result" to "success", "filename" to filename, "path" to path)
        } else {
            mapOf("result" to "error", "message" to "Failed to save file: $filename")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Information (sem parâmetro Context)
    // ──────────────────────────────────────────────────────────────

    /** Retorna information about the current device and time. */
    @Tool(description = "Returns current device information including time, date, and available memory.")
    fun getDeviceInfo(): Map<String, String> {
        val ctx = appContext ?: return mapOf("result" to "error", "message" to "Context not initialized")
        val memInfo = LlmChatModelHelper.getMemoryUsage()
        val sysMem = LlmChatModelHelper.getSystemMemory(ctx)
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