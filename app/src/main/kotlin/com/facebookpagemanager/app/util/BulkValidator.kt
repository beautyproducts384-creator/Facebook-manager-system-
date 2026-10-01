package com.facebookpagemanager.app.util

import com.facebookpagemanager.app.data.model.BulkRow
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Validates bulk-upload CSV rows BEFORE anything is scheduled.
 * Expected columns (case-insensitive header or positional):
 * date, time, caption, media, link, post_type
 */
object BulkValidator {

    val EXPECTED_HEADERS = listOf("date", "time", "caption", "media", "link", "post_type")
    val VALID_TYPES = setOf("text", "image", "video", "reel", "link")

    fun validate(rows: List<List<String>>): List<BulkRow> {
        if (rows.isEmpty()) return emptyList()
        val first = rows.first().map { it.trim().lowercase() }
        val hasHeader = first.contains("date") && first.contains("caption")
        val dataRows = if (hasHeader) rows.drop(1) else rows
        val idx = if (hasHeader) {
            EXPECTED_HEADERS.associateWith { h -> first.indexOf(h) }
        } else {
            EXPECTED_HEADERS.mapIndexed { i, h -> h to i }.toMap()
        }
        fun cell(row: List<String>, header: String): String {
            val i = idx[header] ?: -1
            return if (i in row.indices) row[i].trim() else ""
        }

        val nowSec = System.currentTimeMillis() / 1000
        return dataRows.mapIndexed { rowIdx, row ->
            val lineNumber = rowIdx + (if (hasHeader) 2 else 1)
            val date = cell(row, "date")
            val time = cell(row, "time")
            val caption = cell(row, "caption")
            val media = cell(row, "media")
            val link = cell(row, "link")
            val postType = cell(row, "post_type").lowercase().ifBlank { "text" }
            val errors = mutableListOf<String>()

            val scheduledFor = parseDateTime(date, time)
            if (date.isBlank()) errors += "date is required (YYYY-MM-DD)"
            else if (!isValidDate(date)) errors += "date '$date' is not a valid calendar date (use YYYY-MM-DD)"
            if (time.isBlank()) errors += "time is required (HH:MM, 24h)"
            else if (!time.matches(Regex("""\d{1,2}:\d{2}"""))) errors += "time '$time' must look like 14:30"
            if (scheduledFor == null) {
                if (errors.none { it.startsWith("date") || it.startsWith("time") }) errors += "could not parse date/time"
            } else {
                if (scheduledFor <= nowSec + 60) errors += "scheduled time must be at least 1 minute in the future"
                if (scheduledFor > nowSec + 75L * 24 * 3600) errors += "Meta allows scheduling up to 75 days ahead"
            }
            if (postType !in VALID_TYPES) errors += "post_type '$postType' invalid (use: ${VALID_TYPES.joinToString("/")})"
            if (caption.isBlank() && link.isBlank() && media.isBlank()) {
                errors += "caption, link or media is required — the post would be empty"
            }
            if (postType in setOf("image", "video", "reel") && media.isBlank()) {
                errors += "media is required for post_type '$postType' (file path or URL)"
            }
            if (link.isNotBlank() && !link.startsWith("http://") && !link.startsWith("https://")) {
                errors += "link must start with http:// or https://"
            }

            BulkRow(lineNumber, date, time, caption, media, link, postType, scheduledFor ?: 0, errors)
        }
    }

    private fun parseDateTime(date: String, time: String): Long? {
        return try {
            val t = if (time.matches(Regex("""\d{1,2}:\d{2}"""))) {
                val (h, m) = time.split(":")
                "${h.padStart(2, '0')}:${m}"
            } else return null
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            fmt.isLenient = false
            (fmt.parse("$date $t")?.time ?: return null) / 1000
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidDate(date: String): Boolean {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.isLenient = false
            fmt.parse(date) != null
        } catch (_: Exception) {
            false
        }
    }

    fun sampleCsv(): String = buildString {
        appendLine("date,time,caption,media,link,post_type")
        appendLine("2026-10-05,10:00,\"Good morning! Fresh brews are ready.\",,https://example.com,text")
        appendLine("2026-10-06,18:30,\"New collection just dropped!\",/sdcard/Download/launch.jpg,,image")
    }
}
