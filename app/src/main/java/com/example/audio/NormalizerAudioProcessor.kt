package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

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
    private var channelCount = 1

    // Smoothed running peak (not instantaneous - a single loud sample shouldn't slam the gain
    // down) and the gain derived from it. Attack (getting quieter) is fast so clipping is caught
    // quickly; release (getting louder again) is slow so gain doesn't visibly "pump" during a
    // song's own quiet verses. [attackCoeff]/[releaseCoeff] are real per-sample-rate time-constant
    // coefficients (computed in [onConfigure]), not raw magic numbers - the previous fixed
    // per-sample constants had no relationship to sample rate, converged in tens of milliseconds
    // regardless of the "slow release" intent, and updated once per interleaved sample rather than
    // once per audio frame (double the intended rate for stereo) - together that made the gain
    // chase individual transients (kick drums, vocal peaks) fast enough to audibly pump/breathe in
    // sync with the music.
    private var runningPeak = TARGET_PEAK
    private var currentGain = 1.0
    private var attackCoeff = 1.0
    private var releaseCoeff = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        // Standard one-pole envelope-follower coefficient: coeff = 1 - e^(-1 / (tauSeconds * rate)).
        // Frame rate, not sample rate - the envelope updates once per audio frame (all channels
        // combined into one peak below), matching the real number of gain updates per second.
        val frameRate = inputAudioFormat.sampleRate.toDouble()
        attackCoeff = 1.0 - exp(-1.0 / (ATTACK_SECONDS * frameRate))
        releaseCoeff = 1.0 - exp(-1.0 / (RELEASE_SECONDS * frameRate))
        return inputAudioFormat
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    override fun isActive(): Boolean = super.isActive() && enabled

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val shorts = remaining / 2
        val channels = channelCount

        var index = 0
        while (index < shorts) {
            // One envelope/gain update per audio frame, using the loudest channel in that frame -
            // not per individual interleaved sample - so a stereo (or wider) stream doesn't
            // silently run the envelope follower at multiple times its intended rate.
            var frameMagnitude = 0.0
            var channel = 0
            while (channel < channels && index + channel < shorts) {
                frameMagnitude = max(frameMagnitude, abs(input.get(index + channel) / 32768.0))
                channel++
            }

            runningPeak = if (frameMagnitude > runningPeak) {
                runningPeak + (frameMagnitude - runningPeak) * attackCoeff
            } else {
                runningPeak + (frameMagnitude - runningPeak) * releaseCoeff
            }

            val targetGain = (TARGET_PEAK / runningPeak.coerceAtLeast(MIN_PEAK)).coerceIn(MIN_GAIN, MAX_GAIN)
            currentGain = currentGain + (targetGain - currentGain) * releaseCoeff

            channel = 0
            while (channel < channels && index + channel < shorts) {
                val sample = input.get(index + channel) / 32768.0
                val processed = (sample * currentGain).coerceIn(-1.0, 1.0)
                // Relative put (not absolute): `output` is the raw ByteBuffer BaseAudioProcessor
                // hands back, whose absolute putShort(index, ...) takes a *byte* index, not a
                // short-element one - sequential relative puts in the same frame/channel order
                // this loop already reads in avoids that mismatch entirely.
                output.putShort((processed * 32767.0).toInt().toShort())
                channel++
            }
            index += channels
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
        // Real time constants now (seconds), converted to per-frame coefficients in onConfigure -
        // fast enough to catch a loud passage before it clips, slow enough that the gain rides the
        // song's overall loudness rather than chasing individual beats/transients.
        private const val ATTACK_SECONDS = 0.030
        private const val RELEASE_SECONDS = 0.600
    }
}
