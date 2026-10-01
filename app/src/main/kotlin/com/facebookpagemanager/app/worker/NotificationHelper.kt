package com.facebookpagemanager.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.facebookpagemanager.app.R

/**
 * Notifications for scheduled-post due reminders and publish failures.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SCHEDULED = "fpm_scheduled"
        private const val NOTIF_BASE_ID = 1000
        private var counter = 0
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SCHEDULED,
                "Scheduled posts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when scheduled posts publish, and alerts on publish failures."
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun nextId(): Int = NOTIF_BASE_ID + (counter++ % 900)

    private fun post(title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULED)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(nextId(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — stay silent, the in-app state still updates.
        }
    }

    fun showPublished(title: String, text: String) = post(title, text)

    fun showPublishFailed(text: String) =
        post("Scheduled post failed to publish", text)

    fun showDueReminder(text: String) =
        post("Scheduled post due", text)
}
