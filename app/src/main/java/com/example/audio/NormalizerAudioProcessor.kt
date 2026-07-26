package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Loudness normalization as a dynamic-range compressor, not a per-track precomputed gain.
 *
 * A real loudness-normalization feature (ReplayGain/EBU R128-style) needs a gain value computed
 * from analyzing each *whole* track in advance and stored somewhere - this app has no such
 * database and no offline analysis pass. What it can do honestly in real time is track a running
 * peak of the decoded signal and apply a smoothed gain that pulls loud passages down and quiet ones
 * up toward a target level - a soft AGC/compressor. That's what this does: same effect a listener
 * actually wants ("loud song, quiet song, similar volume without reaching for the slider"),
 * implemented as something that's actually true.
 *
 * Same [BaseAudioProcessor] pattern as [EqualizerAudioProcessor] - filters the decoded PCM inside
 * ExoPlayer's own pipeline rather than a platform `AudioEffect`, for the same device-compatibility
 * reason documented there.
 */
@UnstableApi
class NormalizerAudioProcessor : BaseAudioProcessor() {

    @Volatile private var enabled = false

    // Smoothed running peak (not instantaneous - a single loud sample shouldn't slam the gain
    // down) and the gain derived from it. Attack (getting quieter) is fast so clipping is caught
    // quickly; release (getting louder again) is slow so gain doesn't visibly "pump" during a
    // song's own quiet verses.
    private var runningPeak = TARGET_PEAK
    private var currentGain = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    override fun isActive(): Boolean = enabled

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val shorts = remaining / 2

        var index = 0
        while (index < shorts) {
            val sample = input.get(index) / 32768.0
            val magnitude = abs(sample)

            runningPeak = if (magnitude > runningPeak) {
                runningPeak + (magnitude - runningPeak) * ATTACK
            } else {
                runningPeak + (magnitude - runningPeak) * RELEASE
            }

            val targetGain = (TARGET_PEAK / runningPeak.coerceAtLeast(MIN_PEAK)).coerceIn(MIN_GAIN, MAX_GAIN)
            currentGain = currentGain + (targetGain - currentGain) * GAIN_SMOOTHING

            val processed = (sample * currentGain).coerceIn(-1.0, 1.0)
            output.putShort((processed * 32767.0).toInt().toShort())
            index++
        }

        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        runningPeak = TARGET_PEAK
        currentGain = 1.0
    }

    override fun onReset() {
        runningPeak = TARGET_PEAK
        currentGain = 1.0
    }

    companion object {
        val INSTANCE by lazy { NormalizerAudioProcessor() }

        private const val TARGET_PEAK = 0.5
        private const val MIN_PEAK = 0.05
        private const val MIN_GAIN = 0.5
        private const val MAX_GAIN = 3.0
        private const val ATTACK = 0.01
        private const val RELEASE = 0.0005
        private const val GAIN_SMOOTHING = 0.002
    }
}
