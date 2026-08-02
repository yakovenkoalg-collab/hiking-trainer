package ru.yakovenko.mountainform.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ru.yakovenko.mountainform.domain.WorkoutTimerMode

class WorkoutSignal(context: Context) {
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun play(mode: WorkoutTimerMode) {
        val duration = if (mode == WorkoutTimerMode.REST) 350 else 500
        val toneType = if (mode == WorkoutTimerMode.REST) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
        tone.startTone(toneType, duration)
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    if (mode == WorkoutTimerMode.REST) longArrayOf(0, 180, 100, 180) else longArrayOf(0, 250, 100, 250),
                    -1,
                ),
            )
        }
    }

    fun release() {
        tone.release()
    }
}
