package com.example.restrelay

import android.app.Notification
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

/**
 * Tracks a rest-timer notification so we can tell "finished naturally" apart from
 * "user skipped / cancelled it early".
 *
 * On every post/update we record:
 *   - postTime            : when the timer notification first appeared
 *   - endTime (elapsed)   : countdown chronometer's end, from Notification.when
 *   - countdownSec        : the last "m:ss" value parsed from title/text
 *   - durationSec         : the best-known total duration (for the fallback)
 *
 * When the notification is removed we compute the remaining time and return a
 * decision: alert only if it finished naturally, skip if there was still lots of
 * time left (i.e. the user dismissed it).
 */
class TimerTracker {

    class Timer {
        var postTime = 0L        // SystemClock.elapsedRealtime() when first seen
        var endTime = 0L         // elapsedRealtime() the countdown reaches zero
        var countdownSec = -1    // last parsed remaining seconds
        var countdownAt = 0L     // elapsedRealtime() when countdownSec was parsed
        var durationSec = -1     // best-known total timer duration
    }

    private val timers = HashMap<String, Timer>()

    /** Called on each post/update of a rest-timer notification. */
    fun observe(pkg: String, sbn: StatusBarNotification) {
        val t = timers.getOrPut(pkg) { Timer() }
        val now = SystemClock.elapsedRealtime()
        if (t.postTime == 0L) t.postTime = now

        val extras = sbn.notification.extras
        val isCountdown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val whenMs = sbn.notification.`when`

        // A count-down chronometer stores its end time in Notification.when, in the
        // elapsedRealtime time base. Remaining = when - elapsedRealtime().
        if (isCountdown && whenMs > 0) {
            t.endTime = whenMs
            if (t.durationSec < 0) {
                val d = (whenMs - t.postTime) / 1000
                if (d > 0) t.durationSec = d.toInt()
            }
        }

        // Also parse any "m:ss" (or "mm:ss") text as a second source of truth.
        val full = textOf(sbn, Notification.EXTRA_TITLE) + " " + textOf(sbn, Notification.EXTRA_TEXT)
        parseCountdown(full)?.let { sec ->
            t.countdownSec = sec
            t.countdownAt = now
            if (t.durationSec < 0) t.durationSec = sec
        }
    }

    /**
     * Called when the timer notification is removed. Returns (reason, shouldFire).
     * Consumes the tracker state for [pkg].
     */
    fun decideRemoval(pkg: String, thresholdSec: Int, unsureAlert: Boolean): Pair<String, Boolean> {
        val t = timers[pkg] ?: return ("no timer state", unsureAlert)
        val now = SystemClock.elapsedRealtime()

        val result: Pair<String, Boolean> = when {
            // 1. Countdown chronometer end time is the most reliable signal.
            t.endTime > 0 -> {
                val rem = ((t.endTime - now) / 1000).toInt()
                if (rem <= thresholdSec) ("finished (remaining=$rem s <= threshold)" to true)
                else ("skipped (remaining=$rem s > threshold)" to false)
            }
            // 2. Extrapolate from the last parsed countdown value.
            t.countdownSec >= 0 -> {
                val rem = t.countdownSec - ((now - t.countdownAt) / 1000).toInt()
                if (rem <= thresholdSec) ("finished (remaining=$rem s <= threshold)" to true)
                else ("skipped (remaining=$rem s > threshold)" to false)
            }
            // 3. Fallback: compare elapsed time against the last known duration.
            t.durationSec >= 0 -> {
                val elapsed = ((now - t.postTime) / 1000).toInt()
                if (elapsed >= t.durationSec)
                    ("fallback finished (elapsed=$elapsed s >= duration=${t.durationSec} s)" to true)
                else
                    ("unsure (elapsed=$elapsed s < duration=${t.durationSec} s) -> ${if (unsureAlert) "alert" else "skip"}" to unsureAlert)
            }
            // 4. No information at all: defer to the "when unsure" policy.
            else -> ("unknown remaining -> ${if (unsureAlert) "alert" else "skip"}" to unsureAlert)
        }

        timers.remove(pkg)
        return result
    }

    private fun textOf(sbn: StatusBarNotification, key: String): String =
        sbn.notification.extras.getCharSequence(key)?.toString().orEmpty()

    private fun parseCountdown(text: String): Int? {
        val m = COUNTDOWN.matcher(text)
        if (!m.find()) return null
        val mm = m.group(1).toInt()
        val ss = m.group(2).toInt()
        if (ss > 59) return null
        return mm * 60 + ss
    }

    companion object {
        private val COUNTDOWN = Pattern.compile("\\b(\\d{1,3}):(\\d{2})\\b")
    }
}
