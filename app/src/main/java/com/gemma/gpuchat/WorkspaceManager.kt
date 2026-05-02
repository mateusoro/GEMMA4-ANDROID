package com.gemma.gpuchat

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * WorkspaceManager — singleton que gerencia o workspace de arquivos do app.
 *
 * Estrutura:
 *   app.filesDir/workspace/
 *   ├── documents/   — PDFs originais salvos pelo usuário
 *   └── markdown/    — PDFs convertidos para Markdown (lidos pela IA depois)
 *
 * Métodos principais:
 * - getWorkspaceDir() / getDocumentsDir() / getMarkdownDir()
 * - savePdf(context, uri) — copia PDF do URI para documents/
 * - saveMarkdown(filename, content) — salva .md em markdown/
 * - listWorkspace() — lista todos os arquivos
 * - deleteFile(path) — remove arquivo
 * - getFilePath(type, filename) — retorna path absoluto
 */
object WorkspaceManager {

    private const val WORKSPACE_DIR = "workspace"
    private const val DOCUMENTS_DIR = "documents"
    private const val MARKDOWN_DIR = "markdown"

    private val initialized = mutableMapOf<String, Boolean>()

    // ──────────────────────────────────────────────────────────────
    // Diretórios
    // ──────────────────────────────────────────────────────────────

    /**
     * Retorna o diretório raiz do workspace.
     * Cria a estrutura completa (workspace/documents + workspace/markdown) se não existir.
     */
    fun getWorkspaceDir(context: Context): File {
        val dir = File(context.filesDir, WORKSPACE_DIR)
        ensureDirectories(context)
        return dir
    }

    /** Retorna o diretório de documentos (PDFs originais). */
    fun getDocumentsDir(context: Context): File {
        ensureDirectories(context)
        return File(context.filesDir, "$WORKSPACE_DIR/$DOCUMENTS_DIR")
    }

    /** Retorna o diretório de Markdown (arquivos .md para leitura da IA). */
    fun getMarkdownDir(context: Context): File {
        ensureDirectories(context)
        return File(context.filesDir, "$WORKSPACE_DIR/$MARKDOWN_DIR")
    }

    private fun ensureDirectories(context: Context) {
        if (initialized[context.packageName] == true) return

        val docs = File(context.filesDir, "$WORKSPACE_DIR/$DOCUMENTS_DIR")
        val md   = File(context.filesDir, "$WORKSPACE_DIR/$MARKDOWN_DIR")

        if (!docs.exists()) docs.mkdirs()
        if (!md.exists()) md.mkdirs()

        initialized[context.packageName] = true
        AppLogger.d(TAG, "Workspace directories ensured: $docs, $md")
    }

    // ──────────────────────────────────────────────────────────────
    // Salvar arquivos
    // ──────────────────────────────────────────────────────────────

    /**
     * Copia um PDF do URI para o diretório documents/ do workspace.
     * Usa o nome original do arquivo (extraído do URI ou gerado).
     *
     * @return path absoluto do arquivo salvo, ou null se falhar
     */
    fun savePdf(context: Context, uri: Uri): String? {
        return try {
            ensureDirectories(context)
            val documentsDir = getDocumentsDir(context)

            // Extrair nome do arquivo do URI
            val fileName = extractFileName(context, uri, "pdf")
            val destFile = File(documentsDir, fileName)

            // Copiar do URI para o arquivo
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

            AppLogger.i(TAG, "PDF saved: ${destFile.absolutePath} (${destFile.length()} bytes)")
            destFile.absolutePath

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save PDF: ${e.message}", e)
            null
        }
    }

    /**
     * Salva conteúdo Markdown em um arquivo .md no diretório markdown/.
     *
     * @param baseName nome base (sem extensão) — será usado como nome do .md
     * @param content conteúdo Markdown
     * @return path absoluto do arquivo salvo, ou null se falhar
     */
    fun saveMarkdown(baseName: String, content: String): String? {
        return try {
            // Sanitizar nome
            val safeName = baseName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(100)

            val fileName = if (safeName.endsWith(".md")) safeName else "$safeName.md"
            val markdownDir = getMarkdownDir(context = getStaticContext())
            val destFile = File(markdownDir, fileName)

            destFile.writeText(content)

            AppLogger.i(TAG, "Markdown saved: ${destFile.absolutePath} (${content.length} chars)")
            destFile.absolutePath

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save Markdown: ${e.message}", e)
            null
        }
    }

    /**
     * Salva Markdown passando o Context explicitamente (necessário nos primeiros调用).
     */
    fun saveMarkdown(context: Context, baseName: String, content: String): String? {
        return try {
            val safeName = baseName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(100)

            val fileName = if (safeName.endsWith(".md")) safeName else "$safeName.md"
            val markdownDir = getMarkdownDir(context)
            val destFile = File(markdownDir, fileName)

            destFile.writeText(content)

            AppLogger.i(TAG, "Markdown saved: ${destFile.absolutePath} (${content.length} chars)")
            destFile.absolutePath

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save Markdown: ${e.message}", e)
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Listar e gerenciar
    // ──────────────────────────────────────────────────────────────

    /**
     * Lista todos os arquivos no workspace (documents + markdown).
     * Retorna string formatada para exibir no chat.
     */
    fun listWorkspace(context: Context): String {
        return try {
            ensureDirectories(context)
            val docs = getDocumentsDir(context)
            val md   = getMarkdownDir(context)

            val docsFiles = docs.listFiles()?.sortedBy { it.name } ?: emptyList()
            val mdFiles   = md.listFiles()?.sortedBy { it.name }   ?: emptyList()

            val lines = mutableListOf<String>()
            lines.add("=== Workspace ===")
            lines.add("")
            lines.add("📁 documents/ (${docsFiles.size} arquivos):")
            if (docsFiles.isEmpty()) {
                lines.add("   (vazio)")
            } else {
                docsFiles.forEach { f ->
                    lines.add("   [PDF] ${f.name} (${formatSize(f.length())})")
                }
            }
            lines.add("")
            lines.add("📝 markdown/ (${mdFiles.size} arquivos):")
            if (mdFiles.isEmpty()) {
                lines.add("   (vazio)")
            } else {
                mdFiles.forEach { f ->
                    lines.add("   [MD] ${f.name} (${formatSize(f.length())})")
                }
            }
            lines.add("")
            lines.add("Total: ${docsFiles.size + mdFiles.size} arquivos")

            lines.joinToString("\n")

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list workspace: ${e.message}", e)
            "Erro ao listar workspace: ${e.message}"
        }
    }

    /**
     * Lista apenas os arquivos Markdown (para comando /ls-workspace).
     */
    fun listMarkdown(context: Context): String {
        return try {
            ensureDirectories(context)
            val md = getMarkdownDir(context)
            val files = md.listFiles()?.sortedBy { it.name } ?: emptyList()

            if (files.isEmpty()) {
                "=== markdown/ (vazio) ==="
            } else {
                val lines = files.map { f ->
                    "📝 ${f.name} (${formatSize(f.length())})"
                }
                "=== markdown/ (${files.size} arquivos) ===\n" + lines.joinToString("\n")
            }
        } catch (e: Exception) {
            "Erro ao listar markdown/: ${e.message}"
        }
    }

    /**
     * Remove um arquivo pelo path absoluto.
     * @return true se arquivo foi removido com sucesso
     */
    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && file.delete()) {
                AppLogger.i(TAG, "Deleted: $path")
                true
            } else {
                AppLogger.w(TAG, "Failed to delete: $path")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting file: ${e.message}", e)
            false
        }
    }

    /**
     * Retorna o path absoluto de um arquivo no workspace.
     *
     * @param type "documents" ou "markdown"
     * @param filename nome do arquivo
     */
    fun getFilePath(context: Context, type: String, filename: String): String? {
        return try {
            val dir = when (type) {
                "documents" -> getDocumentsDir(context)
                "markdown"  -> getMarkdownDir(context)
                else -> return null
            }
            val file = File(dir, filename)
            if (file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifica se um arquivo existe no workspace.
     */
    fun fileExists(context: Context, type: String, filename: String): Boolean {
        val path = getFilePath(context, type, filename)
        return path != null && File(path).exists()
    }

    // ──────────────────────────────────────────────────────────────
    // Utilitários
    // ──────────────────────────────────────────────────────────────

    private fun extractFileName(context: Context, uri: Uri, fallbackExt: String): String {
        // Tentar obter do ContentResolver via display name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    val name = cursor.getString(nameIdx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }

        // Fallback: gerar nome baseado no timestamp
        val timestamp = System.currentTimeMillis()
        return "document_$timestamp.$fallbackExt"
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }

    // Contexto estático para sobrecarga de saveMarkdown sem Context
    // Usado apenas internamente quando o Context já está disponível
    @Volatile
    private var staticContext: Context? = null

    private fun getStaticContext(): Context {
        return staticContext ?: throw IllegalStateException(
            "WorkspaceManager.saveMarkdown called without Context — use saveMarkdown(context, baseName, content)"
        )
    }

    fun init(context: Context) {
        staticContext = context.applicationContext
        ensureDirectories(context)
    }

    private const val TAG = "WorkspaceManager"
}