package com.example.util

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtil {
    /**
     * Produces a distinct, satisfying tactile click when logging an absence.
     */
    fun performConfirmHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 45, 60, 55),
                    intArrayOf(0, 180, 0, 255),
                    -1
                )
                vibrator?.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 50, 50),
                    intArrayOf(0, 160, 0, 240),
                    -1
                )
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(70)
            }
        } catch (_: Exception) {
            // Silently ignore if device doesn't support or permission blocked
        }
    }

    /**
     * Warning or alert haptic pattern (e.g. invalid PIN or quota alert)
     */
    fun performWarningHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 40, 30),
                    intArrayOf(0, 120, 0, 120),
                    -1
                )
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {
            // Ignore
        }
    }
}
