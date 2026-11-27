package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

interface SoundFeedback {
    fun playSelect(soundEnabled: Boolean, hapticsEnabled: Boolean)
    fun playPour(fillRatio: Float, soundEnabled: Boolean, hapticsEnabled: Boolean)
    fun playUndo(soundEnabled: Boolean, hapticsEnabled: Boolean)
    fun playError(soundEnabled: Boolean, hapticsEnabled: Boolean)
    fun playVictory(soundEnabled: Boolean, hapticsEnabled: Boolean)
}

class SoundFeedbackEngine(private val context: Context) : SoundFeedback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val selectBuffer: ShortArray by lazy {
        synthesizeChime(freqStart = 540.0, freqEnd = 700.0, durationMs = 55, gain = 0.25)
    }

    private val undoBuffer: ShortArray by lazy {
        synthesizeChime(freqStart = 600.0, freqEnd = 390.0, durationMs = 95, gain = 0.22)
    }

    private val errorBuffer: ShortArray by lazy {
        synthesizeChime(freqStart = 210.0, freqEnd = 155.0, durationMs = 90, gain = 0.24)
    }

    private val victoryBuffer: ShortArray by lazy {
        synthesizeVictoryArpeggio()
    }

    private val pourBuffers: Array<ShortArray> by lazy {
        Array(4) { step ->
            val ratio = (step + 1) / 4.0
            synthesizePourBubble(baseFreq = 320.0 + ratio * 260.0, durationMs = 195)
        }
    }

    private val audioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private val audioFormat: AudioFormat by lazy {
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
    }

    override fun playSelect(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) {
            vibratePredefinedOrTick(
                predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1,
                fallbackDurationMs = 14L,
                fallbackAmplitude = 75
            )
        }
        if (!soundEnabled) return
        scope.launch { playToneBuffer(selectBuffer) }
    }

    override fun playPour(fillRatio: Float, soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) {
            vibratePourTrickle()
        }
        if (!soundEnabled) return
        val bucket = ((fillRatio.coerceIn(0f, 1f) * 3f).toInt()).coerceIn(0, 3)
        scope.launch {
            playToneBuffer(pourBuffers[bucket])
        }
    }

    override fun playUndo(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) {
            vibratePredefinedOrTick(
                predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1,
                fallbackDurationMs = 16L,
                fallbackAmplitude = 70
            )
        }
        if (!soundEnabled) return
        scope.launch { playToneBuffer(undoBuffer) }
    }

    override fun playError(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) vibrateDoubleThud()
        if (!soundEnabled) return
        scope.launch { playToneBuffer(errorBuffer) }
    }

    override fun playVictory(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) vibrateVictoryFanfare()
        if (!soundEnabled) return
        scope.launch { playToneBuffer(victoryBuffer) }
    }

    private fun vibratePredefinedOrTick(
        predefinedId: Int,
        fallbackDurationMs: Long,
        fallbackAmplitude: Int
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && predefinedId >= 0) {
                vib.vibrate(VibrationEffect.createPredefined(predefinedId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(fallbackDurationMs, fallbackAmplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(fallbackDurationMs)
            }
        }
    }

    private fun vibratePourTrickle() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val timings = longArrayOf(0, 12, 55, 18, 45, 24)
        val amplitudes = intArrayOf(0, 55, 0, 95, 0, 125)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vib.hasAmplitudeControl()) {
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vib.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    private fun vibrateDoubleThud() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val timings = longArrayOf(0, 18, 35, 22)
        val amplitudes = intArrayOf(0, 95, 0, 75)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vib.hasAmplitudeControl()) {
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vib.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    private fun vibrateVictoryFanfare() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val timings = longArrayOf(0, 18, 50, 26, 55, 48)
        val amplitudes = intArrayOf(0, 90, 0, 145, 0, 220)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vib.hasAmplitudeControl()) {
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vib.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    private suspend fun playToneBuffer(samples: ShortArray) {
        if (samples.isEmpty()) return
        var track: AudioTrack? = null
        try {
            val bufferBytes = samples.size * 2
            track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bufferBytes)
                .build()

            val written = track.write(samples, 0, samples.size)
            if (written > 0) {
                track.play()
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE + 40L
                delay(durationMs)
            }
        } catch (_: Throwable) {
            // Gracefully ignore audio hardware unavailability on headless/muted devices
        } finally {
            runCatching {
                track?.stop()
            }
            runCatching {
                track?.release()
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 22050

        @VisibleForTesting
        fun synthesizeChime(
            freqStart: Double,
            freqEnd: Double,
            durationMs: Int,
            gain: Double
        ): ShortArray {
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val out = ShortArray(numSamples)
            var phase = 0.0
            for (i in 0 until numSamples) {
                val t = i.toDouble() / numSamples
                val freq = freqStart + (freqEnd - freqStart) * t
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val env = sin(PI * t.coerceAtMost(0.15) / 0.15) * exp(-4.2 * t)
                val sample = sin(phase) * env * gain
                out[i] = (sample * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            return out
        }

        @VisibleForTesting
        fun synthesizePourBubble(baseFreq: Double, durationMs: Int): ShortArray {
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val out = ShortArray(numSamples)
            var phase = 0.0
            for (i in 0 until numSamples) {
                val t = i.toDouble() / numSamples
                val bubbleWobble = sin(2.0 * PI * 14.0 * t) * 45.0
                val freq = baseFreq + 160.0 * t + bubbleWobble
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val env = sin(PI * t) * exp(-1.6 * t)
                val sample = (0.7 * sin(phase) + 0.3 * sin(phase * 1.5)) * env * 0.24
                out[i] = (sample * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            return out
        }

        @VisibleForTesting
        fun synthesizeVictoryArpeggio(): ShortArray {
            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
            val noteSamples = (SAMPLE_RATE * 110) / 1000
            val total = noteSamples * notes.size
            val out = ShortArray(total)
            for ((nIdx, freq) in notes.withIndex()) {
                var phase = 0.0
                val offset = nIdx * noteSamples
                for (i in 0 until noteSamples) {
                    val t = i.toDouble() / noteSamples
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    val env = (1.0 - exp(-28.0 * t)) * exp(-3.5 * t)
                    val sample = (0.75 * sin(phase) + 0.25 * sin(phase * 2.0)) * env * 0.28
                    out[offset + i] = (sample * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }
            return out
        }
    }
}
