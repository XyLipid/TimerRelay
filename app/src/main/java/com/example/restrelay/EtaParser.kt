package com.example.restrelay

import android.content.Context
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Parses a delivery ETA out of notification text.
 *
 * Two forms are supported, each backed by an editable regex (in Settings) with a
 * hand-written fallback so it still works if the user's regex is empty or bad:
 *
 *   RELATIVE  "arriving in 6 min", "1 hr 5 min"  -> minutes from now
 *   ABSOLUTE  "arriving at 7:45 PM", "by 19:45"  -> minutes until that clock time
 *
 * Named capture groups the user regexes must expose:
 *   relative:  (?<h> hours ) optional, (?<m> minutes )
 *   absolute:  (?<h> hour 0-23 ), (?<m> minute ), (?<ap> am|pm ) optional
 */
object EtaParser {

    data class Result(val minutes: Int, val source: String)

    fun parseRelative(context: Context, text: String): Result? {
        val viaRegex = runCatching {
            val m = Pattern.compile(Prefs.etaRelativeRegex(context)).matcher(text)
            if (!m.find()) return@runCatching null
            val h = m.group("h")?.toIntOrNull() ?: 0
            val min = m.group("m")?.toIntOrNull() ?: return@runCatching null
            Result(h * 60 + min, "regex")
        }.getOrNull()
        return viaRegex ?: fallbackRelative(text)
    }

    fun parseAbsolute(context: Context, text: String, now: Calendar): Result? {
        val viaRegex = runCatching {
            val m = Pattern.compile(Prefs.etaAbsoluteRegex(context)).matcher(text)
            if (!m.find()) return@runCatching null
            var h = m.group("h")?.toIntOrNull() ?: return@runCatching null
            val min = m.group("m")?.toIntOrNull() ?: 0
            val ap = m.group("ap")?.lowercase()
            h = normalizeHour(h, ap)
            Result(minutesUntil(now, h, min), "regex")
        }.getOrNull()
        return viaRegex ?: fallbackAbsolute(text, now)
    }

    /** Fallback: sum any "N hours" / "N minutes" tokens found in the text. */
    private fun fallbackRelative(text: String): Result? {
        var total = 0
        var found = false
        Regex("(?i)(\\d+)\\s*(?:h|hr|hrs|hour|hours)\\b").find(text)?.let {
            total += it.groupValues[1].toInt() * 60; found = true
        }
        Regex("(?i)(\\d+)\\s*(?:m|min|mins|minute|minutes)\\b").find(text)?.let {
            total += it.groupValues[1].toInt(); found = true
        }
        return if (found) Result(total, "fallback") else null
    }

    /** Fallback: find an HH:MM (optionally am/pm) clock time. */
    private fun fallbackAbsolute(text: String, now: Calendar): Result? {
        val m = Regex("(?i)(\\d{1,2}):(\\d{2})\\s*(am|pm)?").find(text) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val ap = m.groupValues[3].lowercase()
        if (h > 23 || min > 59) return null
        h = normalizeHour(h, ap)
        return Result(minutesUntil(now, h, min), "fallback")
    }

    /** 12-hour + am/pm -> 24-hour. */
    private fun normalizeHour(h: Int, ap: String?): Int = when {
        ap == "am" && h == 12 -> 0
        ap == "pm" && h != 12 -> h + 12
        else -> h
    }

    /** Minutes from [now] until today's [h]:[min]; wraps to tomorrow if already past. */
    private fun minutesUntil(now: Calendar, h: Int, min: Int): Int {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var diff = (target.timeInMillis - now.timeInMillis) / 60000
        if (diff < 0) diff += 24 * 60
        return diff.toInt()
    }
}
