package com.facebookpagemanager.app.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

/** Copies picked media into app-private storage so scheduled posts survive gallery changes. */
object MediaUtils {

    fun copyUriToAppFile(context: Context, uri: Uri, prefix: String): File? {
        return try {
            val mime = context.contentResolver.getType(uri)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: when {
                    mime?.startsWith("video") == true -> "mp4"
                    else -> "jpg"
                }
            val dir = File(context.filesDir, "media").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.takeIf { it.exists() && it.length() > 0 }
        } catch (_: Exception) {
            null
        }
    }

    fun copyUriToCache(context: Context, uri: Uri, prefix: String): File? {
        return try {
            val file = File.createTempFile(prefix, ".media", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.takeIf { it.exists() && it.length() > 0 }
        } catch (_: Exception) {
            null
        }
    }

    fun uriType(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        return if (mime.startsWith("video")) "video" else "image"
    }

    fun displayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex("_display_name")
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: "media_${System.currentTimeMillis()}"
        } catch (_: Exception) {
            "media_${System.currentTimeMillis()}"
        }
    }
}
