package com.facebookpagemanager.app.ui.util

import java.time.Instant
import java.time.ZoneId

object TimeFmt {
    fun dateTime(epochSec: Long): String {
        val z = Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
        return "%04d-%02d-%02d %02d:%02d".format(z.year, z.monthValue, z.dayOfMonth, z.hour, z.minute)
    }

    fun date(epochSec: Long): String {
        val z = Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
        return "%04d-%02d-%02d".format(z.year, z.monthValue, z.dayOfMonth)
    }

    fun timeAgo(epochSec: Long): String {
        val diff = System.currentTimeMillis() / 1000 - epochSec
        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> "${diff / 86400}d ago"
        }
    }
}
