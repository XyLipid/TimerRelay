package com.example.restrelay

import android.app.Notification
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Watches notifications for every package that has a rule and dispatches them to
 * the rest-timer or delivery engine. Every event is recorded in the Debug Log.
 *
 * - Rest timer  -> TimerTracker (skip detection) + keyword/stale modes.
 * - Delivery    -> DeliveryTracker (ETA threshold, re-arm, delayed, arrived).
 *
 * Android may start and stop this service at will; all state is in-memory and
 * re-seeded in onListenerConnected().
 */
class RestListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val timers = TimerTracker()
    private val deliveries = DeliveryTracker(this)

    // Stale-mode state per rule id.
    private val lastUpdate = HashMap<String, Long>()
    private val staleFired = HashMap<String, Boolean>()

    private val tick = object : Runnable {
        override fun run() {
            checkStale()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.postDelayed(tick, 1000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Seed stale timestamps so STALE rules with an already-active notification
        // don't fire instantly on (re)connect.
        val now = System.currentTimeMillis()
        for (rule in Prefs.rules(this)) {
            if (rule.detection == RestDetection.STALE &&
                activeNotifications.any { it.packageName == rule.pkg } &&
                lastUpdate[rule.id] == null
            ) {
                lastUpdate[rule.id] = now
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val rules = Prefs.rules(this).filter { it.pkg == sbn.packageName }
        if (rules.isEmpty()) return
        log(sbn, "POSTED", "")

        for (rule in rules) {
            when (rule.type) {
                RuleType.REST_TIMER -> onRestPosted(rule, sbn)
                RuleType.DELIVERY -> onDeliveryPosted(rule, sbn)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val rules = Prefs.rules(this).filter { it.pkg == sbn.packageName }
        if (rules.isEmpty()) return
        log(sbn, "REMOVED", "")

        for (rule in rules) {
            when (rule.type) {
                RuleType.REST_TIMER -> onRestRemoved(rule, sbn)
                RuleType.DELIVERY -> deliveries.onRemoved(rule.pkg)
            }
        }
    }

    // ---- Rest timer ----

    private fun onRestPosted(rule: Rule, sbn: StatusBarNotification) {
        timers.observe(rule.pkg, sbn)
        when (rule.detection) {
            RestDetection.TEXT -> {
                val kw = rule.keywords
                if (kw.isNotBlank() && fullText(sbn).contains(kw, true)) {
                    fire(AlertType.REST_OVER, placeholdersFor(rule, sbn))
                }
            }
            RestDetection.STALE -> {
                lastUpdate[rule.id] = System.currentTimeMillis()
                staleFired[rule.id] = false
            }
            else -> {}
        }
    }

    private fun onRestRemoved(rule: Rule, sbn: StatusBarNotification) {
        if (rule.detection != RestDetection.REMOVED) return
        // Skip detection: only alert if the timer appears to have finished naturally.
        val (reason, shouldFire) = timers.decideRemoval(
            rule.pkg,
            Prefs.skipThresholdSec(this),
            Prefs.unsureAlert(this),
        )
        LogStore.add(entry(if (shouldFire) "REST-FINISH" else "REST-SKIP", rule.pkg, reason))
        if (shouldFire) fire(AlertType.REST_OVER, placeholdersFor(rule, sbn))
    }

    private fun checkStale() {
        val now = System.currentTimeMillis()
        for (rule in Prefs.rules(this)) {
            if (rule.type != RuleType.REST_TIMER || rule.detection != RestDetection.STALE) continue
            if (staleFired[rule.id] == true) continue
            if (activeNotifications.none { it.packageName == rule.pkg }) continue
            val last = lastUpdate[rule.id] ?: continue
            if (now - last > Prefs.staleSec(this) * 1000L) {
                staleFired[rule.id] = true
                fire(AlertType.REST_OVER, placeholdersFor(rule, null))
            }
        }
    }

    // ---- Delivery ----

    private fun onDeliveryPosted(rule: Rule, sbn: StatusBarNotification) {
        val o = deliveries.onUpdate(rule.pkg, fullText(sbn))

        if (o.eta != null) {
            LogStore.add(entry("ETA", rule.pkg, "eta=${o.eta} min (${o.etaSource})"))
        }
        if (o.fire != null) {
            val ph = placeholdersFor(rule, sbn) + ("eta" to (o.eta?.toString() ?: "?"))
            fire(o.fire, ph)
        }
        if (o.note.isNotBlank()) {
            LogStore.add(entry(if (o.fire != null) "DELIVERY-FIRE" else "DELIVERY", rule.pkg, o.note))
        }
    }

    // ---- Shared ----

    private fun fire(type: AlertType, placeholders: Map<String, String>) {
        AlertNotifier.post(this, type, Prefs.appearance(this, type), placeholders)
        LogStore.add(entry("FIRED", type.label, "posted ${type.label} alert"))
    }

    private fun placeholdersFor(rule: Rule, sbn: StatusBarNotification?): Map<String, String> {
        val title = sbn?.notification?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(rule.pkg, 0)).toString()
        }.getOrDefault(rule.pkg)
        return mapOf("app" to appLabel, "exercise" to title, "eta" to "?")
    }

    private fun fullText(sbn: StatusBarNotification): String {
        val e = sbn.notification.extras
        return listOf(
            e.getCharSequence(Notification.EXTRA_TITLE),
            e.getCharSequence(Notification.EXTRA_TEXT),
            e.getCharSequence(Notification.EXTRA_BIG_TEXT),
            e.getCharSequence(Notification.EXTRA_SUB_TEXT),
        ).filterNotNull().joinToString(" ")
    }

    private fun now(): String = timeFmt.format(Date())

    private fun entry(event: String, pkg: String, note: String): LogStore.Entry =
        LogStore.Entry(now(), event, pkg, "", "", "", "", false, "-", "-", note)

    private fun log(sbn: StatusBarNotification, event: String, note: String) {
        val e = sbn.notification.extras
        LogStore.add(
            LogStore.Entry(
                time = now(),
                event = event,
                pkg = sbn.packageName,
                title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
                subText = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
                ongoing = sbn.isOngoing,
                importance = channelImportance(sbn.notification.channelId),
                channelId = sbn.notification.channelId ?: "n/a",
                note = note,
            )
        )
    }

    private fun channelImportance(id: String?): String {
        if (id.isNullOrEmpty()) return "n/a"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(id) ?: return "unknown"
        return when (ch.importance) {
            NotificationManager.IMPORTANCE_NONE -> "NONE"
            NotificationManager.IMPORTANCE_MIN -> "MIN"
            NotificationManager.IMPORTANCE_LOW -> "LOW"
            NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
            NotificationManager.IMPORTANCE_HIGH -> "HIGH"
            NotificationManager.IMPORTANCE_MAX -> "MAX"
            else -> "?"
        }
    }
}
