package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Headphone crossfeed - blends a low-passed portion of each channel into the other, the classic
 * "Chu Moy" technique. Stereo music mixed for speakers (where both ears hear both channels a
 * little, just with a level/timing difference) sounds artificially hard-panned on headphones
 * (where each ear hears *only* its channel); crossfeed adds back some of what a speaker's acoustic
 * crosstalk would have supplied, at some cost to stereo width. That's the honest scope of this -
 * not a "3D virtualizer"/HRTF spatializer, which is a different (much bigger) DSP problem this
 * doesn't attempt.
 *
 * Only active for genuinely stereo input; passes mono/multichannel straight through untouched.
 */
@UnstableApi
class CrossfeedAudioProcessor : BaseAudioProcessor() {

    // One low-pass filter per channel, feeding into the *other* channel's output - a crossfed low
    // needs to be dulled first, or it just sounds like a leaky panpot rather than the room
    // reflection it's standing in for.
    private var lowPassLeft = BiquadFilter(SAMPLE_RATE_PLACEHOLDER)
    private var lowPassRight = BiquadFilter(SAMPLE_RATE_PLACEHOLDER)
    private var channelCount = 0

    @Volatile private var enabled = false
    @Volatile private var amount = 0.0 // 0f..0.6f, how much of the other channel bleeds in
    @Volatile private var needsRebuild = false

    /** [intensity] is 0-100. */
    fun setIntensity(enabled: Boolean, intensity: Int) {
        this.enabled = enabled
        this.amount = (intensity.coerceIn(0, 100) / 100.0) * MAX_BLEED
        needsRebuild = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        lowPassLeft = BiquadFilter(inputAudioFormat.sampleRate)
        lowPassRight = BiquadFilter(inputAudioFormat.sampleRate)
        configureFilters()
        return inputAudioFormat
    }

    private fun configureFilters() {
        // A shelf, not a true low-pass: HIGH_SHELF with a negative gain cuts everything *above*
        // the corner frequency while leaving bass untouched - close enough to "dull" for a bled
        // channel, and it reuses the same filter shapes the equalizer already has rather than
        // needing a fourth kind of biquad.
        lowPassLeft.configure(FilterType.HIGH_SHELF, CUTOFF_HZ, gainDb = -18.0, q = 1.0)
        lowPassRight.configure(FilterType.HIGH_SHELF, CUTOFF_HZ, gainDb = -18.0, q = 1.0)
        needsRebuild = false
    }

    override fun isActive(): Boolean = enabled && amount > 0.0 && channelCount == 2

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (needsRebuild) configureFilters()

        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = remaining / 4 // 2 channels * 2 bytes

        var frame = 0
        while (frame < frames) {
            val left = input.get(frame * 2) / 32768.0
            val right = input.get(frame * 2 + 1) / 32768.0

            // The *other* channel's low end, attenuated, bled into this one.
            val leftBleed = lowPassRight.process(right) * amount
            val rightBleed = lowPassLeft.process(left) * amount

            val outLeft = (left * (1 - amount) + leftBleed).coerceIn(-1.0, 1.0)
            val outRight = (right * (1 - amount) + rightBleed).coerceIn(-1.0, 1.0)

            output.putShort((outLeft * 32767.0).toInt().toShort())
            output.putShort((outRight * 32767.0).toInt().toShort())
            frame++
        }

        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        lowPassLeft.reset()
        lowPassRight.reset()
    }

    override fun onReset() {
        channelCount = 0
    }

    companion object {
        val INSTANCE by lazy { CrossfeedAudioProcessor() }
        private const val CUTOFF_HZ = 700.0
        private const val MAX_BLEED = 0.6
        private const val SAMPLE_RATE_PLACEHOLDER = 44100
    }
}
