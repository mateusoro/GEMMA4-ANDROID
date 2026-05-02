package com.gemma.gpuchat

import java.io.File

/**
 * File reader tool that mimics the OpenCode Read tool.
 * Reads files with line numbers and provides formatted output
 * that can be injected as context in the chat.
 *
 * Supports:
 * - Single files with line numbers
 * - Directory listing
 * - Limit/offset for large files
 */
object FileReadTool {

    /**
     * Read a file and return formatted content with line numbers.
     * Format: "1: content" per line (like OpenCode read tool)
     *
     * @param filePath Absolute path to the file
     * @param limit Max lines to read (0 = unlimited)
     * @param offset Line number to start from (1-based)
     * @return Formatted string with line numbers
     */
    fun read(filePath: String, limit: Int = 0, offset: Int = 1): String {
        val file = File(filePath)
        return when {
            !file.exists() -> "ERROR: File not found: $filePath"
            file.isDirectory -> listDirectory(filePath)
            else -> readFile(file, limit, offset)
        }
    }

    /**
     * Read current working directory or a path relative to it.
     */
    fun readPath(path: String): String {
        val cwd = System.getProperty("user.dir")
        val resolved = if (path.startsWith("/")) path else "$cwd/$path"
        val file = File(resolved)
        return when {
            !file.exists() -> "ERROR: Path not found: $path"
            file.isDirectory -> listDirectory(resolved)
            else -> readFile(file, 0, 1)
        }
    }

    private fun readFile(file: File, limit: Int, offset: Int): String {
        return try {
            val lines = file.readLines()
            val startIdx = (offset - 1).coerceAtLeast(0)
            val endIdx = if (limit > 0) (startIdx + limit).coerceAtMost(lines.size) else lines.size
            val selectedLines = lines.subList(startIdx, endIdx)

            val prefix = if (file.name.endsWith(".kt") || file.name.endsWith(".java") ||
                           file.name.endsWith(".kts") || file.name.endsWith(".xml") ||
                           file.name.endsWith(".gradle") || file.name.endsWith(".json") ||
                           file.name.endsWith(".md") || file.name.endsWith(".txt") ||
                           file.name.endsWith(".py") || file.name.endsWith(".sh")) {
                // Syntax-style: show line numbers as reference
                "=== $file (${lines.size} lines) ===\n"
            } else {
                "=== $file ===\n"
            }

            val content = selectedLines.mapIndexed { idx, line ->
                val lineNum = startIdx + idx + 1
                "$lineNum: $line"
            }.joinToString("\n")

            val info = if (limit > 0 || offset > 1) {
                val remaining = lines.size - endIdx
                "\n[Showing lines $offset-$endIdx of ${lines.size}${if (remaining > 0) ", $remaining remaining" else ""}]"
            } else {
                "\n[${lines.size} lines total]"
            }

            prefix + content + info
        } catch (e: Exception) {
            "ERROR reading ${file.absolutePath}: ${e.message}"
        }
    }

    private fun listDirectory(dirPath: String): String {
        return try {
            val dir = File(dirPath)
            val entries = dir.listFiles()?.sortedBy { !it.isDirectory } ?: return "ERROR: Cannot list directory: $dirPath"
            if (entries.isEmpty()) return "=== $dirPath ===\n(empty directory)"

            val lines = entries.map { entry ->
                val icon = if (entry.isDirectory) "[DIR]" else "[FILE]"
                val size = if (entry.isFile) formatSize(entry.length()) else ""
                "$icon ${entry.name}$size"
            }

            "=== $dirPath (${entries.size} items) ===\n" + lines.joinToString("\n")
        } catch (e: Exception) {
            "ERROR listing $dirPath: ${e.message}"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }

    /**
     * Search for a pattern in a file and return matching lines with context.
     */
    fun grep(filePath: String, pattern: String, caseSensitive: Boolean = true): String {
        val file = File(filePath)
        if (!file.exists()) return "ERROR: File not found: $filePath"

        return try {
            val lines = file.readLines()
            val regex = if (caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
            val matches = lines.mapIndexedNotNull { idx, line ->
                if (regex.containsMatchIn(line)) "${idx + 1}: $line" else null
            }
            if (matches.isEmpty()) {
                "No matches for '$pattern' in $filePath"
            } else {
                "=== $filePath (${matches.size} matches) ===\n" + matches.joinToString("\n")
            }
        } catch (e: Exception) {
            "ERROR searching $filePath: ${e.message}"
        }
    }

    /**
     * Get the default working directory for the app.
     */
    fun getWorkingDirectory(): String {
        return System.getProperty("user.dir") ?: "/"
    }
}