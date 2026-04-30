package com.gemma.gpuchat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.chatDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_history")

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

object ChatHistoryManager {
    private val CONVERSATIONS_KEY = stringPreferencesKey("conversations")

    fun getConversationsFlow(context: Context): Flow<List<Conversation>> {
        return context.chatDataStore.data.map { prefs ->
            val json = prefs[CONVERSATIONS_KEY] ?: "[]"
            parseConversations(json)
        }
    }

    private fun parseConversations(json: String): List<Conversation> {
        val list = mutableListOf<Conversation>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val msgsArray = obj.getJSONArray("messages")
                val msgs = mutableListOf<ChatMessage>()
                for (j in 0 until msgsArray.length()) {
                    val msgObj = msgsArray.getJSONObject(j)
                    msgs.add(
                        ChatMessage(
                            id = msgObj.getString("id"),
                            text = msgObj.getString("text"),
                            isUser = msgObj.getBoolean("isUser"),
                            throughput = msgObj.getDouble("throughput").toFloat(),
                            tokenCount = msgObj.getInt("tokenCount"),
                            durationMs = msgObj.getLong("durationMs")
                        )
                    )
                }
                list.add(
                    Conversation(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        messages = msgs,
                        createdAt = obj.getLong("createdAt"),
                        updatedAt = obj.getLong("updatedAt")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list.sortByDescending { it.updatedAt }
        return list
    }

    private fun conversationToJson(conv: Conversation): JSONObject {
        return JSONObject().apply {
            put("id", conv.id)
            put("title", conv.title)
            put("createdAt", conv.createdAt)
            put("updatedAt", conv.updatedAt)
            put("messages", JSONArray().apply {
                conv.messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("id", msg.id)
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        put("throughput", msg.throughput.toDouble())
                        put("tokenCount", msg.tokenCount)
                        put("durationMs", msg.durationMs)
                    })
                }
            })
        }
    }

    suspend fun saveConversation(context: Context, conversation: Conversation) {
        context.chatDataStore.edit { prefs ->
            val json = prefs[CONVERSATIONS_KEY] ?: "[]"
            val array = JSONArray()
            try {
                val list = mutableListOf<JSONObject>()
                val existingArray = JSONArray(json)
                for (i in 0 until existingArray.length()) {
                    list.add(existingArray.getJSONObject(i))
                }
                val existingIndex = list.indexOfFirst { it.getString("id") == conversation.id }
                val convJson = conversationToJson(conversation)
                if (existingIndex >= 0) {
                    list[existingIndex] = convJson
                } else {
                    list.add(convJson)
                }
                list.forEach { array.put(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs[CONVERSATIONS_KEY] = array.toString()
        }
    }

    suspend fun deleteConversation(context: Context, id: String) {
        context.chatDataStore.edit { prefs ->
            val json = prefs[CONVERSATIONS_KEY] ?: "[]"
            val array = JSONArray()
            try {
                val list = mutableListOf<JSONObject>()
                val existingArray = JSONArray(json)
                for (i in 0 until existingArray.length()) {
                    list.add(existingArray.getJSONObject(i))
                }
                list.removeAll { it.getString("id") == id }
                list.forEach { array.put(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs[CONVERSATIONS_KEY] = array.toString()
        }
    }

    suspend fun createNewConversation(context: Context, title: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val conversation = Conversation(
            id = id,
            title = title,
            messages = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveConversation(context, conversation)
        return id
    }

    suspend fun getConversation(context: Context, id: String): Conversation? {
        val prefs = context.chatDataStore.data.first()
        val json = prefs[CONVERSATIONS_KEY] ?: "[]"
        val list = parseConversations(json)
        return list.find { it.id == id }
    }
}