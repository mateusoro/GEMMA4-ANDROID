package com.gemma.gpuchat

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
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
 * Para registar: AgentTools.setCallback { action -> ... }
 * Depois: tools = listOf(tool(AgentTools))
 */
class AgentTools private constructor() : ToolSet {

    // Callback para notificar ações ao app (UI executa a ação real)
    private var actionCallback: ((AgentAction) -> Unit)? = null

    companion object {
        // Referência estática para o callback (setado antes de inicializar tools)
        @Volatile
        private var staticCallback: ((AgentAction) -> Unit)? = null

        fun setCallback(cb: (AgentAction) -> Unit) {
            staticCallback = cb
            AppLogger.d(TAG, "AgentTools callback set")
        }

        fun clearCallback() {
            staticCallback = null
        }

        fun create(): AgentTools {
            val tools = AgentTools()
            tools.actionCallback = staticCallback
            return tools
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TOOLS: Mobile Actions
    // ──────────────────────────────────────────────────────────────

    /** Liga a lanterna do dispositivo. */
    @Tool(description = "Turns the flashlight on")
    fun turnOnFlashlight(context: Context): Map<String, String> {
        Log.d(TAG, "turnOnFlashlight called")
        val action = AgentAction(
            type = AgentActionType.FLASHLIGHT_ON,
            iconName = "flashlight_on",
            description = "Liga a lanterna",
            params = emptyMap()
        )
        actionCallback?.invoke(action)
        executeFlashlight(context, true)
        return mapOf("result" to "success", "action" to "flashlight_on")
    }

    /** Desliga a lanterna do dispositivo. */
    @Tool(description = "Turns the flashlight off")
    fun turnOffFlashlight(context: Context): Map<String, String> {
        Log.d(TAG, "turnOffFlashlight called")
        val action = AgentAction(
            type = AgentActionType.FLASHLIGHT_OFF,
            iconName = "flashlight_off",
            description = "Desliga a lanterna",
            params = emptyMap()
        )
        actionCallback?.invoke(action)
        executeFlashlight(context, false)
        return mapOf("result" to "success", "action" to "flashlight_off")
    }

    /** Abre as configurações de WiFi do dispositivo. */
    @Tool(description = "Opens the WiFi settings")
    fun openWifiSettings(context: Context): Map<String, String> {
        Log.d(TAG, "openWifiSettings called")
        val action = AgentAction(
            type = AgentActionType.OPEN_WIFI_SETTINGS,
            iconName = "wifi",
            description = "Abre configurações de WiFi",
            params = emptyMap()
        )
        actionCallback?.invoke(action)
        try {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open wifi settings", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "wifi_settings_opened")
    }

    /** Abre as configurações gerais do dispositivo. */
    @Tool(description = "Opens the device settings")
    fun openSettings(context: Context): Map<String, String> {
        Log.d(TAG, "openSettings called")
        val action = AgentAction(
            type = AgentActionType.OPEN_SETTINGS,
            iconName = "settings",
            description = "Abre configurações do dispositivo",
            params = emptyMap()
        )
        actionCallback?.invoke(action)
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "settings_opened")
    }

    /** Cria um contato na lista de contatos do dispositivo. */
    @Tool(description = "Creates a contact in the phone's contact list")
    fun createContact(
        context: Context,
        @ToolParam(description = "The first name of the contact") firstName: String,
        @ToolParam(description = "The last name of the contact") lastName: String,
        @ToolParam(description = "The phone number of the contact") phoneNumber: String,
        @ToolParam(description = "The email address of the contact") email: String
    ): Map<String, String> {
        Log.d(TAG, "createContact: $firstName $lastName, $phoneNumber, $email")
        val action = AgentAction(
            type = AgentActionType.CREATE_CONTACT,
            iconName = "person_add",
            description = "Cria contato: $firstName $lastName",
            params = mapOf("firstName" to firstName, "lastName" to lastName, "phone" to phoneNumber, "email" to email)
        )
        actionCallback?.invoke(action)

        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, "$firstName $lastName")
            putExtra(ContactsContract.Intents.Insert.EMAIL, email)
            putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create contact", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf(
            "result" to "success",
            "action" to "contact_created",
            "first_name" to firstName,
            "last_name" to lastName
        )
    }

    /** Envia um email. */
    @Tool(description = "Sends an email")
    fun sendEmail(
        context: Context,
        @ToolParam(description = "The email address of the recipient") to: String,
        @ToolParam(description = "The subject of the email") subject: String,
        @ToolParam(description = "The body of the email") body: String
    ): Map<String, String> {
        Log.d(TAG, "sendEmail: to=$to subject=$subject")
        val action = AgentAction(
            type = AgentActionType.SEND_EMAIL,
            iconName = "email",
            description = "Envia email para $to",
            params = mapOf("to" to to, "subject" to subject, "body" to body)
        )
        actionCallback?.invoke(action)

        val intent = Intent(Intent.ACTION_SEND).apply {
            data = "mailto:".toUri()
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email", e)
            return mapOf("result" to "error", "message" to (e.message ?: "Unknown error"))
        }
        return mapOf("result" to "success", "action" to "email_sent", "to" to to, "subject" to subject)
    }

    /** Mostra uma localização no mapa. */
    @Tool(description = "Shows a location on the map")
    fun showLocationOnMap(
        context: Context,
        @ToolParam(description = "The location to search for. May be the name of a place, a business, or an address.") location: String
    ): Map<String, String> {
        Log.d(TAG, "showLocationOnMap: $location")
        val action = AgentAction(
            type = AgentActionType.SHOW_LOCATION,
            iconName = "map",
            description = "Mostra no mapa: $location",
            params = mapOf("location" to location)
        )
        actionCallback?.invoke(action)

        val encoded = URLEncoder.encode(location, StandardCharsets.UTF_8.toString())
        val intent = Intent(Intent.ACTION_VIEW).apply { data = "geo:0,0?q=$encoded".toUri() }
        try {
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
        val action = AgentAction(
            type = AgentActionType.CREATE_CALENDAR_EVENT,
            iconName = "calendar",
            description = "Cria evento: $title em $datetime",
            params = mapOf("datetime" to datetime, "title" to title)
        )
        actionCallback?.invoke(action)

        // Parse datetime
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
        val action = AgentAction(
            type = AgentActionType.READ_WORKSPACE_FILE,
            iconName = "description",
            description = "Lê arquivo: $filename",
            params = mapOf("filename" to filename)
        )
        actionCallback?.invoke(action)

        // Try markdown first, then documents
        var content: String? = null

        // Check markdown directory
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
        val action = AgentAction(
            type = AgentActionType.SAVE_WORKSPACE_FILE,
            iconName = "save",
            description = "Salva arquivo: $filename",
            params = mapOf("filename" to filename, "size" to "${content.length} chars")
        )
        actionCallback?.invoke(action)

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

    // ──────────────────────────────────────────────────────────────
    // Helper: Flashlight
    // ──────────────────────────────────────────────────────────────

    private fun executeFlashlight(context: Context, isEnabled: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var cameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) { cameraId = id; break }
            }
            cameraId?.let { cameraManager.setTorchMode(it, isEnabled) }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error", e)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// AgentAction: ação executada por uma tool (para UI)
// ─────────────────────────────────────────────────────────────────

enum class AgentActionType {
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    OPEN_WIFI_SETTINGS,
    OPEN_SETTINGS,
    CREATE_CONTACT,
    SEND_EMAIL,
    SHOW_LOCATION,
    CREATE_CALENDAR_EVENT,
    READ_WORKSPACE_FILE,
    SAVE_WORKSPACE_FILE,
}

data class AgentAction(
    val type: AgentActionType,
    val iconName: String,
    val description: String,
    val params: Map<String, String> = emptyMap()
)