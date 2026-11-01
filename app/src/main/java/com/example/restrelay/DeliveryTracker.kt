package com.example.restrelay

import android.content.Context
import java.util.Calendar

/**
 * Delivery-alert engine. State is keyed by package; one "order" at a time per app.
 *
 * Responsibilities, in order:
 *   1. "Arrived" keyword -> fire immediately (always works, even with ETA ignored).
 *   2. If "ignore ETA" is on, stop here.
 *   3. Parse the ETA (relative or absolute) -> minutes remaining.
 *   4. "Delayed" check: ETA jumped up more than the threshold since last update.
 *   5. "Arriving soon" check: ETA <= N for a configurable number of consecutive
 *      updates (debounce glitchy ETAs).
 *   6. Re-arm: once fired, if the ETA rises above N + margin, allow firing again.
 *   7. Per-order cooldown suppresses duplicate alerts.
 */
class DeliveryTracker(private val context: Context) {

    class Order {
        var lastEta: Int? = null
        var soonFired = false
        var consecBelow = 0
        var cooldownUntil = 0L
    }

    private val orders = HashMap<String, Order>()

    data class Outcome(val fire: AlertType?, val eta: Int?, val etaSource: String?, val note: String)

    fun onUpdate(pkg: String, fullText: String): Outcome {
        val order = orders.getOrPut(pkg) { Order() }
        val now = System.currentTimeMillis()

        // 1. Arrival keyword always works.
        if (containsArrival(fullText)) {
            return if (now < order.cooldownUntil) {
                Outcome(null, null, null, "arrival matched but in cooldown")
            } else {
                order.cooldownUntil = now + Prefs.cooldownMin(context) * 60000L
                Outcome(AlertType.DELIVERY_ARRIVED, null, null, "arrival keyword matched")
            }
        }

        // 2. ETA disabled.
        if (Prefs.ignoreEta(context)) return Outcome(null, null, null, "")

        val eta = parseEta(fullText) ?: return Outcome(null, null, null, "parse failed")

        // 4. Delay detection.
        if (Prefs.delayEnabled(context)) {
            val prev = order.lastEta
            if (prev != null && eta.minutes > prev + Prefs.delayThresholdMin(context)) {
                order.lastEta = eta.minutes
                return if (now < order.cooldownUntil) {
                    Outcome(null, eta.minutes, eta.source, "delay detected but in cooldown")
                } else {
                    order.cooldownUntil = now + Prefs.cooldownMin(context) * 60000L
                    Outcome(AlertType.DELIVERY_DELAYED, eta.minutes, eta.source, "eta rose $prev -> ${eta.minutes}")
                }
            }
        }

        // 5. Arriving soon, requiring consecutive below-threshold updates.
        val before = Prefs.alertBeforeMin(context)
        val note = StringBuilder()
        if (eta.minutes <= before) {
            order.consecBelow++
            note.append("eta=${eta.minutes} <= $before (consec=${order.consecBelow})")
            if (order.consecBelow >= Prefs.consecutive(context) && !order.soonFired) {
                order.soonFired = true
                order.lastEta = eta.minutes
                if (now < order.cooldownUntil) {
                    note.append(" soon suppressed by cooldown")
                } else {
                    order.cooldownUntil = now + Prefs.cooldownMin(context) * 60000L
                    return Outcome(AlertType.DELIVERY_SOON, eta.minutes, eta.source, note.toString())
                }
            }
        } else {
            order.consecBelow = 0
            // 6. Re-arm once the ETA climbs back above the threshold + margin.
            if (order.soonFired && eta.minutes > before + Prefs.rearmMarginMin(context)) {
                order.soonFired = false
                note.append("re-armed (eta ${eta.minutes} > $before + margin)")
            }
        }

        order.lastEta = eta.minutes
        return Outcome(null, eta.minutes, eta.source, note.toString())
    }

    fun onRemoved(pkg: String) {
        orders.remove(pkg)
    }

    private fun containsArrival(text: String): Boolean {
        val lower = text.lowercase()
        return Prefs.arrivalKeywords(context).any {
            it.isNotBlank() && lower.contains(it.trim().lowercase())
        }
    }

    private fun parseEta(text: String): EtaParser.Result? =
        EtaParser.parseRelative(context, text)
            ?: EtaParser.parseAbsolute(context, text, Calendar.getInstance())
}
