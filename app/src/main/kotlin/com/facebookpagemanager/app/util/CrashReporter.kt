package com.facebookpagemanager.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions (app crashes) to a private file so the exact
 * stack trace can be viewed in Settings -> Diagnostics and shared for debugging.
 * Installed as the very first thing in [com.facebookpagemanager.app.FpmApplication.onCreate].
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    const val FILE_NAME = "crash-report.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(throwable)
                // 1) Private copy (viewable in Settings -> Diagnostics if the app opens).
                File(appContext.filesDir, FILE_NAME).writeText(report)
                // 2) Public copy in Downloads so the report can be opened with any
                //    file manager even if the app never gets past the crash.
                saveToDownloads(appContext, report)
                Log.e(TAG, "Crash captured to $FILE_NAME", throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash report", e)
            }
            // Let the system handle it normally (app closes).
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Saves a copy of [report] as FPM-crash-report.txt in the public Downloads
     * folder (no storage permission needed on Android 10+).
     */
    private fun saveToDownloads(context: Context, report: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "FPM-crash-report.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val uri = context.contentResolver.insert(collection, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(report.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report to Downloads", e)
        }
    }

    fun readReport(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }

    fun clearReport(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    private fun buildReport(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Facebook Page Manager crash report")
            appendLine("Time: $ts")
            appendLine("App: ${com.facebookpagemanager.app.BuildConfig.VERSION_NAME} " +
                "(${com.facebookpagemanager.app.BuildConfig.VERSION_CODE})")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} " +
                "(API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine(sw.toString())
            var cause = t.cause
            while (cause != null && cause !== t) {
                appendLine("Caused by:")
                val csw = StringWriter()
                cause.printStackTrace(PrintWriter(csw))
                appendLine(csw.toString())
                cause = cause.cause
            }
        }
    }
}
