package com.example.heliboard.ime

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.heliboard.data.KeyActionType

/**
 * Coordinates tactile feedback for keyboard taps and long-press actions,
 * honoring user preferences and OS API capabilities without throwing in headless contexts.
 */
open class HapticFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    open fun performKeyPress(
        actionType: KeyActionType,
        enabled: Boolean,
        view: View? = null
    ) {
        if (!enabled) return
        if (view != null) {
            val performed = runCatching {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }.getOrDefault(false)
            if (performed) return
        }

        val ms = if (actionType == KeyActionType.CHARACTER) 10L else 18L
        val predefined = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (actionType == KeyActionType.CHARACTER) {
                VibrationEffect.EFFECT_TICK
            } else {
                VibrationEffect.EFFECT_CLICK
            }
        } else {
            null
        }
        triggerVibrator(durationMs = ms, predefinedEffectId = predefined)
    }

    open fun performLongPress(
        enabled: Boolean,
        view: View? = null
    ) {
        if (!enabled) return
        if (view != null) {
            val performed = runCatching {
                view.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }.getOrDefault(false)
            if (performed) return
        }

        val predefined = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.EFFECT_HEAVY_CLICK
        } else {
            null
        }
        triggerVibrator(durationMs = 24L, predefinedEffectId = predefined)
    }

    private fun triggerVibrator(durationMs: Long, predefinedEffectId: Int?) {
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && predefinedEffectId != null) {
                vib.vibrate(VibrationEffect.createPredefined(predefinedEffectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }
}
