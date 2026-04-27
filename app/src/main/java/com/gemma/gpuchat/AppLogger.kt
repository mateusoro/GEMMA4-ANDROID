package com.gemma.gpuchat

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE = "gemma_startup.nlog"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE)
        logFile?.delete() // fresh log each launch
        i("AppLogger", "=== LOGGER INITIALIZED ===")
        i("AppLogger", "Log file: ${logFile?.absolutePath}")
        i("AppLogger", "filesDir: ${context.filesDir}")
        i("AppLogger", "Android version: ${android.os.Build.VERSION.SDK_INT}")
    }

    fun d(tag: String, msg: String) { log("DEBUG", tag, msg) }
    fun i(tag: String, msg: String) { log("INFO ", tag, msg) }
    fun w(tag: String, msg: String) { log("WARN ", tag, msg) }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        log("ERROR", tag, msg)
        tr?.let { log("ERROR", tag, "  EXCEPTION: ${it.javaClass.name}: ${it.message}") }
    }

    private fun log(level: String, tag: String, msg: String) {
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp [$level] ($tag) $msg\n"
        synchronized(lock) {
            try {
                logFile?.appendText(line)
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to write log: $e")
            }
        }
        // Also print to Logcat for when it works
        when (level) {
            "DEBUG" -> Log.d(tag, msg)
            "INFO " -> Log.i(tag, msg)
            "WARN " -> Log.w(tag, msg)
            "ERROR" -> Log.e(tag, msg)
        }
    }

    fun getLogFilePath(): String? = logFile?.absolutePath

    fun readLogFile(): String {
        return try {
            logFile?.readText() ?: "LOG FILE NOT FOUND"
        } catch (e: Exception) {
            "ERROR READING LOG: $e"
        }
    }
}
