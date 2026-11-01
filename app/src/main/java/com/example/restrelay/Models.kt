package com.example.restrelay

/** What kind of notification a watched app produces. */
enum class RuleType(val label: String) {
    REST_TIMER("Rest timer"),
    DELIVERY("Delivery"),
}

/** How a rest-timer rule decides the timer has finished. */
enum class RestDetection(val label: String) {
    REMOVED("When removed"),
    TEXT("Keyword in text"),
    STALE("Not updated"),
}

/** The four kinds of alert the app can post. */
enum class AlertType(val label: String) {
    REST_OVER("Rest over"),
    DELIVERY_SOON("Delivery arriving soon"),
    DELIVERY_DELAYED("Delivery delayed"),
    DELIVERY_ARRIVED("Delivery arrived"),
}

enum class SoundType { NONE, DEFAULT, CUSTOM }

enum class VibrationPreset(val label: String) {
    SHORT("Short"),
    DOUBLE("Double tap"),
    LONG("Long"),
    SOS("SOS"),
    CUSTOM("Custom"),
}

/** A watched app. One rule per app; multiple apps can have rules. */
data class Rule(
    val id: String,
    val pkg: String,
    val type: RuleType,
    val detection: RestDetection,
    val keywords: String,
)

/**
 * Editable look-and-feel for one alert type. Placeholders {app}, {eta} and
 * {exercise} are filled from the triggering notification at post time.
 */
data class AlertAppearance(
    val title: String,
    val body: String,
    val vibration: VibrationPreset,
    val customVibration: String,
    val sound: SoundType,
    val soundUri: String,
    val color: Int,
    val icon: String,
    val timeoutMs: Long,
    val bigText: Boolean,
)

/** Colour swatches for the accent-colour picker. */
val COLOR_SWATCHES = intArrayOf(
    0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
    0xFF00897B.toInt(), 0xFF1E88E5.toInt(), 0xFF3949AB.toInt(), 0xFF8E24AA.toInt(),
    0xFFD81B60.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt(), 0xFF212121.toInt(),
)

/** Built-in small icons (drawable names). */
val ICON_NAMES = listOf(
    "ic_bell", "ic_alarm", "ic_timer", "ic_truck",
    "ic_package", "ic_flag", "ic_check", "ic_clock",
)
