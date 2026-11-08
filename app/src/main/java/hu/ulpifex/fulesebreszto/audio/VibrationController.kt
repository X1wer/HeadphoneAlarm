package hu.ulpifex.fulesebreszto.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Owns the vibrator during an alarm. All calls are idempotent. */
class VibrationController(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    var isActive: Boolean = false
        private set

    val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true

    fun startAlarmPattern() {
        if (isActive) return
        val v = vibrator ?: return
        isActive = true
        val effect = VibrationEffect.createWaveform(ALARM_PATTERN, 0)
        vibrate(v, effect)
    }

    fun stop() {
        // Always cancel, even if we believe nothing is running.
        isActive = false
        vibrator?.cancel()
    }

    fun testPulse() {
        val v = vibrator ?: return
        vibrate(v, VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }

    private fun vibrate(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private companion object {
        val ALARM_PATTERN = longArrayOf(0, 700, 400, 700, 1200)
    }
}
