package com.gemma.gpuchat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_settings")

object LlmPreferences {
    private val MAX_TOKENS_KEY = intPreferencesKey("max_tokens")
    private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
    private val TOP_K_KEY = intPreferencesKey("top_k")
    private val TOP_P_KEY = floatPreferencesKey("top_p")

    data class Settings(
        val maxTokens: Int = 2048,
        val temperature: Float = 0.8f,
        val topK: Int = 10,
        val topP: Float = 0.95f
    )

    fun getSettingsFlow(context: Context): Flow<Settings> {
        return context.dataStore.data.map { prefs ->
            Settings(
                maxTokens = prefs[MAX_TOKENS_KEY] ?: 2048,
                temperature = prefs[TEMPERATURE_KEY] ?: 0.8f,
                topK = prefs[TOP_K_KEY] ?: 10,
                topP = prefs[TOP_P_KEY] ?: 0.95f
            )
        }
    }

    suspend fun saveSettings(context: Context, settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[MAX_TOKENS_KEY] = settings.maxTokens
            prefs[TEMPERATURE_KEY] = settings.temperature
            prefs[TOP_K_KEY] = settings.topK
            prefs[TOP_P_KEY] = settings.topP
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