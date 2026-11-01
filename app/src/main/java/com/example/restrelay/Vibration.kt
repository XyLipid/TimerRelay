package com.example.restrelay

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** Vibration preset patterns plus a custom on/off parser. */
object Vibration {

    private val PRESETS = mapOf(
        VibrationPreset.SHORT to longArrayOf(0, 150),
        VibrationPreset.DOUBLE to longArrayOf(0, 120, 100, 120),
        VibrationPreset.LONG to longArrayOf(0, 600),
        VibrationPreset.SOS to longArrayOf(0, 150, 100, 150, 100, 150, 300, 600, 300, 600),
    )

    fun pattern(a: AlertAppearance): LongArray =
        if (a.vibration == VibrationPreset.CUSTOM) parseCustom(a.customVibration)
        else PRESETS[a.vibration] ?: longArrayOf(0, 150)

    /**
     * Parses a comma/space separated list of on/off durations in milliseconds,
     * e.g. "0,150,100,150". Returns a safe fallback if it can't be parsed.
     */
    fun parseCustom(s: String): LongArray {
        val parts = s.split(',', ' ', ';')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it >= 0 }
        return if (parts.size >= 2) parts.toLongArray() else longArrayOf(0, 150)
    }

    fun vibrate(context: Context, pattern: LongArray) {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
