package com.facebookpagemanager.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.facebookpagemanager.app.util.CrashReporter

/**
 * Runs before [FpmApplication.onCreate] (providers are created first).
 * Installs the crash handler at the earliest possible moment so that even
 * a failure while loading/initializing the Application class itself is
 * captured to the crash report instead of killing the app silently.
 */
class FpmInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        try {
            context?.let { CrashReporter.install(it) }
        } catch (_: Throwable) {
            // Never let diagnostics break startup.
        }
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}
