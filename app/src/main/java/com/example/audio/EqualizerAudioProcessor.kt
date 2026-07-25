package com.example.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** The fixed band layout MuseFlow's equalizer exposes, in Hz. */
val EQ_BAND_FREQUENCIES = listOf(60, 150, 400, 1000, 2400, 6000, 12000)

/** Widest boost/cut a band allows, in dB. */
const val EQ_MAX_GAIN_DB = 12.0

/**
 * A parametric equalizer implemented as an ExoPlayer [AudioProcessor].
 *
 * **Why this rather than `android.media.audiofx.Equalizer`:** the platform effect asks AudioFlinger
 * to insert a system effect into the output chain, and plenty of devices simply refuse — Xiaomi's
 * audio HAL, for one, answers `status: -38` because its own processing chain owns that stage. This
 * processor instead filters the decoded PCM *inside* ExoPlayer's own pipeline, before it ever
 * reaches the platform, so it works on every device and needs no audio permissions at all.
 *
 * Outer and inner bands use different shapes: the lowest and highest are shelves (so a bass or
 * treble adjustment affects everything below/above it, which is what a listener expects), and
 * everything between is a peaking filter centred on its frequency.
 */
@UnstableApi
class EqualizerAudioProcessor : BaseAudioProcessor() {

    /** One filter per band per channel — biquads carry per-channel state (see [BiquadFilter]). */
    private var filters: Array<Array<BiquadFilter>> = emptyArray()
    private var channelCount = 0
    private var sampleRate = 0

    @Volatile private var enabled = false
    @Volatile private var gainsDb: DoubleArray = DoubleArray(EQ_BAND_FREQUENCIES.size)
    @Volatile private var needsRebuild = false

    /** [gains] are in dB, one per entry of [EQ_BAND_FREQUENCIES]. */
    fun setGains(enabled: Boolean, gains: List<Double>) {
        this.enabled = enabled
        this.gainsDb = DoubleArray(EQ_BAND_FREQUENCIES.size) { index ->
            (gains.getOrElse(index) { 0.0 }).coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)
        }
        needsRebuild = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // 16-bit PCM only. Returning NOT_SET for anything else makes ExoPlayer bypass this
        // processor entirely rather than us mangling a format we don't understand.
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        buildFilters()
        return inputAudioFormat
    }

    private fun buildFilters() {
        if (sampleRate <= 0 || channelCount <= 0) return
        filters = Array(channelCount) {
            Array(EQ_BAND_FREQUENCIES.size) { BiquadFilter(sampleRate) }
        }
        applyGainsToFilters()
        needsRebuild = false
    }

    private fun applyGainsToFilters() {
        val lastBand = EQ_BAND_FREQUENCIES.lastIndex
        for (channel in filters.indices) {
            EQ_BAND_FREQUENCIES.forEachIndexed { band, frequency ->
                val type = when (band) {
                    0 -> FilterType.LOW_SHELF
                    lastBand -> FilterType.HIGH_SHELF
                    else -> FilterType.PEAKING
                }
                filters[channel][band].configure(
                    type = type,
                    frequencyHz = frequency.toDouble(),
                    gainDb = gainsDb[band],
                    q = 1.0,
                )
            }
        }
    }

    override fun isActive(): Boolean = enabled && sampleRate > 0 && channelCount > 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (needsRebuild) {
            if (filters.isEmpty()) buildFilters() else applyGainsToFilters()
            needsRebuild = false
        }

        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val shorts = remaining / 2

        var index = 0
        while (index < shorts) {
            val channel = index % channelCount
            // Normalise to -1..1 so filter maths is scale-independent.
            var sample = input.get(index) / 32768.0

            for (band in EQ_BAND_FREQUENCIES.indices) {
                sample = filters[channel][band].process(sample)
            }

            // Boosting can push past full scale; clamping here turns what would be wrap-around
            // distortion into ordinary (much less audible) clipping.
            val clamped = sample.coerceIn(-1.0, 1.0)
            output.putShort((clamped * 32767.0).toInt().toShort())
            index++
        }

        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        // Seeks and track changes leave stale energy in the delay lines; clearing avoids a click.
        filters.forEach { channel -> channel.forEach(BiquadFilter::reset) }
    }

    override fun onReset() {
        filters = emptyArray()
        sampleRate = 0
        channelCount = 0
    }

    companion object {
        /** Shared instance: [com.example.PlaybackService] installs it into the audio pipeline and
         * the settings layer pushes gains into it. */
        val INSTANCE by lazy { EqualizerAudioProcessor() }

        /** Millibel (the unit the existing repository stores) to dB. */
        fun millibelToDb(millibel: Int): Double = millibel / 100.0

        /** dB to the linear amplitude factor, for callers that need it. */
        fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)
    }
}
