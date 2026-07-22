package com.wandernear.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles the on-device AI model file: where it lives, whether it's downloaded,
 * and downloading it. The model is Gemma 4 E2B in LiteRT-LM format — Apache-2.0
 * and freely downloadable (no HuggingFace token needed). It's ~2.6 GB, so it is
 * downloaded once on demand into the app's private storage, never bundled.
 */
object ModelManager {

    const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE"

    // The real file is ~2.59 GB; anything much smaller means an incomplete/failed download.
    private const val MIN_VALID_BYTES = 2_000_000_000L

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, MODEL_FILE)

    /** True once the model has been fully downloaded. */
    fun isDownloaded(context: Context): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() >= MIN_VALID_BYTES
    }

    /**
     * Downloads the model to a ".part" file, then renames it on success so a
     * half-finished download is never mistaken for a complete one. [onProgress]
     * receives a 0f..1f fraction. Returns true on success.
     *
     * ponytail: no resume/checksum yet — a failed download just deletes the
     * partial file and is retried by tapping Download again.
     */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            val part = File(target.path + ".part")
            try {
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                connection.connect()
                val total = connection.contentLengthLong.takeIf { it > 0 }
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)   // 64 KB chunks
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total != null) onProgress(downloaded.toFloat() / total)
                        }
                    }
                }
                if (target.exists()) target.delete()
                part.renameTo(target)
            } catch (e: Exception) {
                part.delete()
                false
            }
        }
}
