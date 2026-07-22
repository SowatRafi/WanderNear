package com.wandernear.data.journal

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies photos into the app's OWN private storage (files/photos/). This keeps
 * them private (never uploaded) and means they survive even if the user later
 * deletes the original from their gallery. The Android Photo Picker gives us
 * temporary read access to the picked photo, which we use here to copy it.
 */
object PhotoStorage {

    /** Copies [uri] into private storage and returns the saved file path, or null. */
    fun save(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            input.use { source -> file.outputStream().use { output -> source.copyTo(output) } }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes a photo file we previously saved (ignores it if already gone). */
    fun delete(path: String) {
        runCatching { File(path).delete() }
    }
}
