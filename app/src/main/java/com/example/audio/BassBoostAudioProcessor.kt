package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A dedicated low-shelf boost, separate from the 7-band equalizer's own 60Hz band - a single
 * "Bass boost" intensity slider changing the same band the user's own EQ curve already sets would
 * mean the two constantly fight over one number. This is its own [BiquadFilter] per channel, its
 * own gain, applied after the equalizer in the processor chain. */
@UnstableApi
class BassBoostAudioProcessor : BaseAudioProcessor() {

    private var filters: Array<BiquadFilter> = emptyArray()
    private var channelCount = 0
    private var sampleRate = 0

    @Volatile private var enabled = false
    @Volatile private var gainDb = 0.0
    @Volatile private var needsRebuild = false

    /** [intensity] is 0-100; mapped to 0-10dB of shelf boost at 90Hz. */
    fun setIntensity(enabled: Boolean, intensity: Int) {
        this.enabled = enabled
        this.gainDb = (intensity.coerceIn(0, 100) / 100.0) * MAX_GAIN_DB
        needsRebuild = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        buildFilters()
        return inputAudioFormat
    }

    private fun buildFilters() {
        if (sampleRate <= 0 || channelCount <= 0) return
        filters = Array(channelCount) { BiquadFilter(sampleRate) }
        applyGain()
        needsRebuild = false
    }

    private fun applyGain() {
        filters.forEach { it.configure(FilterType.LOW_SHELF, SHELF_FREQUENCY_HZ, gainDb, q = 1.0) }
    }

    override fun isActive(): Boolean = enabled && gainDb > 0.0 && sampleRate > 0 && channelCount > 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (needsRebuild) {
            if (filters.isEmpty()) buildFilters() else applyGain()
            needsRebuild = false
        }

        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val shorts = remaining / 2

        var index = 0
        while (index < shorts) {
            val channel = index % channelCount
            val sample = filters[channel].process(input.get(index) / 32768.0).coerceIn(-1.0, 1.0)
            output.putShort((sample * 32767.0).toInt().toShort())
            index++
        }

        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        filters.forEach(BiquadFilter::reset)
    }

    override fun onReset() {
        filters = emptyArray()
        sampleRate = 0
        channelCount = 0
    }

    companion object {
        val INSTANCE by lazy { BassBoostAudioProcessor() }
        private const val SHELF_FREQUENCY_HZ = 90.0
        private const val MAX_GAIN_DB = 10.0
    }
}
