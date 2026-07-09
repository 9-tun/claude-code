package com.obsidianvoice.notes.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a transcript as a Markdown note into the user-selected sync folder
 * (their Obsidian vault) using the Storage Access Framework. Once written,
 * Obsidian Sync propagates the note to the desktop automatically.
 */
object VaultWriter {

    data class SavedNote(val fileName: String)

    suspend fun save(
        context: Context,
        treeUri: Uri,
        transcript: String,
        title: String?,
        recordedAt: Date,
    ): Result<SavedNote> = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Selected folder is no longer accessible")
            if (!tree.canWrite()) error("No write permission for the selected folder")

            val baseName = buildFileName(title, recordedAt)
            val fileName = uniqueName(tree, baseName)

            val doc = tree.createFile("text/markdown", fileName)
                ?: error("Could not create note file")

            val content = buildMarkdown(transcript, title, recordedAt)
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open the note for writing")

            // createFile may append an extension; report the real display name.
            Result.success(SavedNote(doc.name ?: "$fileName.md"))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun buildFileName(title: String?, recordedAt: Date): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.getDefault()).format(recordedAt)
        val clean = title?.let { sanitize(it) }?.takeIf { it.isNotBlank() }
        return if (clean != null) "$stamp $clean" else stamp
    }

    /** Removes characters that are illegal in file names across platforms. */
    private fun sanitize(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|#^\[\]]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)

    private fun uniqueName(tree: DocumentFile, baseName: String): String {
        if (tree.findFile("$baseName.md") == null) return baseName
        var i = 2
        while (tree.findFile("$baseName ($i).md") != null) i++
        return "$baseName ($i)"
    }

    private fun buildMarkdown(transcript: String, title: String?, recordedAt: Date): String {
        val isoDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(recordedAt)
        val heading = title?.takeIf { it.isNotBlank() } ?: "음성 노트"
        return buildString {
            appendLine("---")
            appendLine("created: $isoDate")
            appendLine("source: Obsidian Voice Notes")
            appendLine("tags: [voice-note]")
            appendLine("---")
            appendLine()
            appendLine("# $heading")
            appendLine()
            appendLine("> 녹음 일시: $isoDate")
            appendLine()
            appendLine("## 요약")
            appendLine()
            appendLine("<!-- PC에서 Claude로 이 스크립트를 요약해 여기에 붙여넣으세요 -->")
            appendLine()
            appendLine("## 스크립트")
            appendLine()
            appendLine(transcript.trim())
            appendLine()
        }
    }
}
