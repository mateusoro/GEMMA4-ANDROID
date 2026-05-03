package com.gemma.gpuchat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_settings")

object LlmPreferences {
    private val MAX_TOKENS_KEY = intPreferencesKey("max_tokens")
    private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
    private val TOP_K_KEY = intPreferencesKey("top_k")
    private val TOP_P_KEY = floatPreferencesKey("top_p")
    private val SYSTEM_PROMPT_KEY = stringPreferencesKey("system_prompt")

    // Default system prompt — used as initial value and fallback
    const val DEFAULT_SYSTEM_PROMPT = """You can do function call.
You have access to these functions:
- listWorkspace() -> lists all files in the workspace (documents and markdown)
- listMarkdown() -> lists only .md files in the workspace
- readWorkspaceFile(filename) -> reads a file content. Pass just the filename like "documento.md", the function searches in both markdown/ and documents/ folders automatically
- saveMarkdownFile(filename, content) -> saves a .md file
- showLocationOnMap(location) -> opens the map with the specified location
- createCalendarEvent(datetime, title) -> creates a calendar event. datetime format: "2026-05-15T14:00:00"
- getDeviceInfo() -> returns current date, time and memory info

When a user asks you to read, list or manage files, always call listWorkspace() first to see what files exist, then use readWorkspaceFile() to read content.
Responda sempre em português. O nome do usuário é Mateus.
Current date: {CURRENT_DATE}"""

    // Gemma-4-E2B-IT recommended defaults (per litert-community model card)
    data class Settings(
        val maxTokens: Int = 2048,
        val temperature: Float = 1.0f,   // Gemma-4-E2B-IT standard: 1.0
        val topK: Int = 64,              // Gemma-4-E2B-IT standard: 64
        val topP: Float = 0.95f,         // Gemma-4-E2B-IT standard: 0.95
        val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    )

    fun getSettingsFlow(context: Context): Flow<Settings> {
        return context.dataStore.data.map { prefs ->
            Settings(
                maxTokens = prefs[MAX_TOKENS_KEY] ?: 2048,
                temperature = prefs[TEMPERATURE_KEY] ?: 1.0f,   // Gemma-4-E2B-IT standard
                topK = prefs[TOP_K_KEY] ?: 64,                  // Gemma-4-E2B-IT standard
                topP = prefs[TOP_P_KEY] ?: 0.95f,
                systemPrompt = prefs[SYSTEM_PROMPT_KEY] ?: DEFAULT_SYSTEM_PROMPT
            )
        }
    }

    suspend fun saveSettings(context: Context, settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[MAX_TOKENS_KEY] = settings.maxTokens
            prefs[TEMPERATURE_KEY] = settings.temperature
            prefs[TOP_K_KEY] = settings.topK
            prefs[TOP_P_KEY] = settings.topP
            prefs[SYSTEM_PROMPT_KEY] = settings.systemPrompt
        }
    }

    fun settingsToLlmParams(settings: Settings): LlmChatModelHelper.LlmParams {
        return LlmChatModelHelper.LlmParams(
            maxNumTokens = settings.maxTokens,
            temperature = settings.temperature,
            topK = settings.topK,
            topP = settings.topP
        )
    }
}