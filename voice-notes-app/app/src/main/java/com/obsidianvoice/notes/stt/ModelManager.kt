package com.obsidianvoice.notes.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and unpacks the offline Vosk speech model into the app's private
 * storage. The model runs entirely on-device — no audio ever leaves the phone.
 *
 * Default is the small Korean model (~80 MB). Swap [MODEL_URL] for another
 * language from https://alphacephei.com/vosk/models if desired.
 */
object ModelManager {

    // Small Korean model — good balance of size and accuracy for on-device use.
    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip"

    /** Final unpacked model root (contains `am/`, `conf/`, `graph/`, …). */
    fun modelDir(context: Context): File = File(context.filesDir, "vosk-model")

    /** True once the model has been fully unpacked and is usable. */
    fun isReady(context: Context): Boolean {
        val dir = modelDir(context)
        return dir.isDirectory && File(dir, "conf/model.conf").exists()
    }

    /**
     * Ensures the model is present, downloading and unpacking it on first run.
     * [onProgress] receives a 0..1 fraction (or -1 for the indeterminate
     * unpack phase). Safe to call repeatedly; returns immediately when ready.
     */
    suspend fun ensureModel(
        context: Context,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isReady(context)) return@withContext Result.success(Unit)

        val target = modelDir(context)
        val tmpZip = File(context.cacheDir, "vosk-model.zip")
        target.deleteRecursively()

        try {
            downloadTo(MODEL_URL, tmpZip, onProgress)
            onProgress(-1f) // unpacking
            unzipFlattened(tmpZip, target)
            tmpZip.delete()

            if (!isReady(context)) {
                error("Model unpacked but expected files are missing")
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            target.deleteRecursively()
            tmpZip.delete()
            Result.failure(t)
        }
    }

    private fun downloadTo(url: String, dest: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            error("Download failed: HTTP ${connection.responseCode}")
        }
        val total = connection.contentLength.toLong()
        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var downloaded = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        connection.disconnect()
    }

    /**
     * Unzips [zip] into [target], stripping the single top-level wrapper folder
     * that Vosk model archives contain so files land directly under [target].
     */
    private fun unzipFlattened(zip: File, target: File) {
        target.mkdirs()
        val targetPath = target.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Drop the leading "vosk-model-…/" path segment.
                val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relative.isNotEmpty()) {
                    val outFile = File(target, relative)
                    // Guard against Zip Slip path traversal.
                    if (!outFile.canonicalPath.startsWith(targetPath)) {
                        error("Unsafe zip entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
