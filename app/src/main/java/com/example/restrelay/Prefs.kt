package com.example.restrelay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences wrapper. Rules are stored as a JSON array; each alert type's
 * appearance is stored as its own JSON object. Simple scalars use typed keys.
 */
object Prefs {
    private const val NAME = "rest_relay"

    const val DEFAULT_COLOR = 0xFF1565C0.toInt()

    const val DEFAULT_RELATIVE_REGEX =
        "(?i)(?:(?<h>\\d+)\\s*(?:h|hr|hrs|hour|hours)\\s*)?(?<m>\\d+)\\s*(?:m|min|mins|minute|minutes)\\b"
    const val DEFAULT_ABSOLUTE_REGEX =
        "(?i)(?<h>\\d{1,2}):(?<m>\\d{2})\\s*(?<ap>am|pm)?"

    private const val KEY_RULES = "rules"
    private const val KEY_IGNORE_ETA = "ignore_eta"
    private const val KEY_SKIP_THRESHOLD = "skip_threshold_sec"
    private const val KEY_UNSURE_ALERT = "unsure_alert"
    private const val KEY_STALE_SEC = "stale_sec"
    private const val KEY_ALERT_BEFORE = "alert_before_min"
    private const val KEY_CONSECUTIVE = "consecutive"
    private const val KEY_REARM = "rearm_margin_min"
    private const val KEY_DELAY_ENABLED = "delay_enabled"
    private const val KEY_DELAY_THRESHOLD = "delay_threshold_min"
    private const val KEY_COOLDOWN = "cooldown_min"
    private const val KEY_ARRIVAL_KEYWORDS = "arrival_keywords"
    private const val KEY_ETA_REL = "eta_relative_regex"
    private const val KEY_ETA_ABS = "eta_absolute_regex"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, def: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(def)

    // ---- Rules ----

    fun rules(c: Context): List<Rule> {
        val raw = sp(c).getString(KEY_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ruleFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setRules(c: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(ruleToJson(it)) }
        sp(c).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    private fun ruleFromJson(o: JSONObject): Rule = Rule(
        id = o.getString("id"),
        pkg = o.getString("pkg"),
        type = enumOrDefault(o.optString("type"), RuleType.REST_TIMER),
        detection = enumOrDefault(o.optString("detection"), RestDetection.REMOVED),
        keywords = o.optString("keywords", ""),
    )

    private fun ruleToJson(r: Rule): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("pkg", r.pkg)
        put("type", r.type.name)
        put("detection", r.detection.name)
        put("keywords", r.keywords)
    }

    // ---- Simple scalars ----

    fun ignoreEta(c: Context): Boolean = sp(c).getBoolean(KEY_IGNORE_ETA, false)
    fun setIgnoreEta(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_IGNORE_ETA, v).apply()

    fun skipThresholdSec(c: Context): Int = sp(c).getInt(KEY_SKIP_THRESHOLD, 3)
    fun setSkipThresholdSec(c: Context, v: Int) = sp(c).edit().putInt(KEY_SKIP_THRESHOLD, v).apply()

    fun unsureAlert(c: Context): Boolean = sp(c).getBoolean(KEY_UNSURE_ALERT, false)
    fun setUnsureAlert(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_UNSURE_ALERT, v).apply()

    fun staleSec(c: Context): Int = sp(c).getInt(KEY_STALE_SEC, 15)
    fun setStaleSec(c: Context, v: Int) = sp(c).edit().putInt(KEY_STALE_SEC, v).apply()

    fun alertBeforeMin(c: Context): Int = sp(c).getInt(KEY_ALERT_BEFORE, 2)
    fun setAlertBeforeMin(c: Context, v: Int) = sp(c).edit().putInt(KEY_ALERT_BEFORE, v).apply()

    fun consecutive(c: Context): Int = sp(c).getInt(KEY_CONSECUTIVE, 2)
    fun setConsecutive(c: Context, v: Int) = sp(c).edit().putInt(KEY_CONSECUTIVE, v).apply()

    fun rearmMarginMin(c: Context): Int = sp(c).getInt(KEY_REARM, 3)
    fun setRearmMarginMin(c: Context, v: Int) = sp(c).edit().putInt(KEY_REARM, v).apply()

    fun delayEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_DELAY_ENABLED, false)
    fun setDelayEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_DELAY_ENABLED, v).apply()

    fun delayThresholdMin(c: Context): Int = sp(c).getInt(KEY_DELAY_THRESHOLD, 5)
    fun setDelayThresholdMin(c: Context, v: Int) = sp(c).edit().putInt(KEY_DELAY_THRESHOLD, v).apply()

    fun cooldownMin(c: Context): Int = sp(c).getInt(KEY_COOLDOWN, 10)
    fun setCooldownMin(c: Context, v: Int) = sp(c).edit().putInt(KEY_COOLDOWN, v).apply()

    fun arrivalKeywords(c: Context): List<String> {
        val raw = sp(c).getString(KEY_ARRIVAL_KEYWORDS, "arrived, at your door, outside")
            ?: "arrived, at your door, outside"
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setArrivalKeywords(c: Context, v: String) =
        sp(c).edit().putString(KEY_ARRIVAL_KEYWORDS, v).apply()

    fun etaRelativeRegex(c: Context): String =
        sp(c).getString(KEY_ETA_REL, DEFAULT_RELATIVE_REGEX) ?: DEFAULT_RELATIVE_REGEX

    fun setEtaRelativeRegex(c: Context, v: String) =
        sp(c).edit().putString(KEY_ETA_REL, v).apply()

    fun etaAbsoluteRegex(c: Context): String =
        sp(c).getString(KEY_ETA_ABS, DEFAULT_ABSOLUTE_REGEX) ?: DEFAULT_ABSOLUTE_REGEX

    fun setEtaAbsoluteRegex(c: Context, v: String) =
        sp(c).edit().putString(KEY_ETA_ABS, v).apply()

    // ---- Alert appearance ----

    private fun key(type: AlertType) = "appearance_" + type.name

    fun appearance(c: Context, type: AlertType): AlertAppearance {
        val raw = sp(c).getString(key(type), null) ?: return defaultAppearance(type)
        return try {
            val o = JSONObject(raw)
            AlertAppearance(
                title = o.optString("title", ""),
                body = o.optString("body", ""),
                vibration = enumOrDefault(o.optString("vibration"), VibrationPreset.DOUBLE),
                customVibration = o.optString("customVibration", ""),
                sound = enumOrDefault(o.optString("sound"), SoundType.DEFAULT),
                soundUri = o.optString("soundUri", ""),
                color = o.optInt("color", DEFAULT_COLOR),
                icon = o.optString("icon", "ic_bell"),
                timeoutMs = o.optLong("timeoutMs", 8000L),
                bigText = o.optBoolean("bigText", false),
            )
        } catch (e: Exception) {
            defaultAppearance(type)
        }
    }

    fun setAppearance(c: Context, type: AlertType, a: AlertAppearance) {
        val o = JSONObject().apply {
            put("title", a.title)
            put("body", a.body)
            put("vibration", a.vibration.name)
            put("customVibration", a.customVibration)
            put("sound", a.sound.name)
            put("soundUri", a.soundUri)
            put("color", a.color)
            put("icon", a.icon)
            put("timeoutMs", a.timeoutMs)
            put("bigText", a.bigText)
        }
        sp(c).edit().putString(key(type), o.toString()).apply()
    }

    fun defaultAppearance(type: AlertType): AlertAppearance = when (type) {
        AlertType.REST_OVER -> AlertAppearance(
            "Rest over", "Rest timer finished",
            VibrationPreset.DOUBLE, "", SoundType.DEFAULT, "", DEFAULT_COLOR,
            "ic_bell", 8000L, false,
        )
        AlertType.DELIVERY_SOON -> AlertAppearance(
            "Delivery arriving soon", "{app} is about {eta} minutes away",
            VibrationPreset.DOUBLE, "", SoundType.DEFAULT, "", DEFAULT_COLOR,
            "ic_truck", 8000L, false,
        )
        AlertType.DELIVERY_DELAYED -> AlertAppearance(
            "Delivery delayed", "{app} is now arriving later",
            VibrationPreset.LONG, "", SoundType.DEFAULT, "", DEFAULT_COLOR,
            "ic_clock", 8000L, false,
        )
        AlertType.DELIVERY_ARRIVED -> AlertAppearance(
            "Delivery arrived", "{app} is at your door",
            VibrationPreset.LONG, "", SoundType.DEFAULT, "", DEFAULT_COLOR,
            "ic_package", 8000L, false,
        )
    }
}
