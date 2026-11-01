package com.example.restrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Posts the alert notifications. Importance is always HIGH so the alert can show
 * as a heads-up pop-up (final behaviour also depends on the app's system-level
 * notification settings).
 *
 * Android locks a channel's vibration + sound once created, so the channel id
 * includes a hash of those settings; when they change we create a fresh channel
 * and delete the now-unused old ones.
 */
object AlertNotifier {

    /** Channel id embeds a hash of the vibration + sound so changes force a new channel. */
    fun channelId(type: AlertType, a: AlertAppearance): String {
        val sig = "${a.vibration.name}|${a.customVibration}|${a.sound.name}|${a.soundUri}"
        val h = sig.hashCode()
        return "${type.name.lowercase()}_${if (h < 0) "n" else "p"}${kotlin.math.abs(h)}"
    }

    fun post(context: Context, type: AlertType, a: AlertAppearance, placeholders: Map<String, String>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = channelId(type, a)
        ensureChannel(nm, type, a, id)

        val title = fill(a.title, placeholders).ifBlank { type.label }
        val body = fill(a.body, placeholders)
        val iconRes = context.resources.getIdentifier(a.icon, "drawable", context.packageName)
        val icon = if (iconRes != 0) iconRes else R.drawable.ic_bell

        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, id)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setColor(a.color)
            .setVibrate(Vibration.pattern(a))

        if (a.bigText) builder.setStyle(Notification.BigTextStyle().bigText(body))
        if (a.timeoutMs > 0) builder.setTimeoutAfter(a.timeoutMs)

        nm.notify(type.ordinal, builder.build())
        cleanupChannels(nm, type, id)
    }

    private fun ensureChannel(nm: NotificationManager, type: AlertType, a: AlertAppearance, id: String) {
        val channel = NotificationChannel(id, type.label, NotificationManager.IMPORTANCE_HIGH).apply {
            description = type.label
            enableVibration(true)
            vibrationPattern = Vibration.pattern(a)
            enableLights(true)
            lightColor = a.color
            when (a.sound) {
                SoundType.NONE -> setSound(null, null)
                SoundType.DEFAULT -> setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null)
                SoundType.CUSTOM -> if (a.soundUri.isNotBlank()) setSound(Uri.parse(a.soundUri), null)
            }
        }
        nm.createNotificationChannel(channel)
    }

    /** Delete any other channels we previously created for this alert type. */
    private fun cleanupChannels(nm: NotificationManager, type: AlertType, keepId: String) {
        val prefix = "${type.name.lowercase()}_"
        nm.notificationChannels.forEach { c ->
            if (c.id.startsWith(prefix) && c.id != keepId) nm.deleteNotificationChannel(c.id)
        }
    }

    /** Replace {app}, {eta}, {exercise} placeholders in a template. */
    fun fill(template: String, placeholders: Map<String, String>): String {
        var s = template
        placeholders.forEach { (k, v) -> s = s.replace("{$k}", v) }
        return s
    }
}
