package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.example.audio.BassBoostAudioProcessor
import com.example.audio.CrossfeedAudioProcessor
import com.example.audio.EqualizerAudioProcessor
import com.example.audio.NormalizerAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioEffectsLifecycleTest {
    @Test fun upgradedMedia3FlushRetainsEveryEffectAndClearsPreviousTrackState() {
        val effects: List<BaseAudioProcessor> = listOf(
            BassBoostAudioProcessor().apply { setIntensity(true, 60) },
            CrossfeedAudioProcessor().apply { setIntensity(true, 60) },
            EqualizerAudioProcessor().apply { setGains(true, List(7) { 3.0 }) },
            NormalizerAudioProcessor().apply { setEnabled(true) },
        )
        fun input(): ByteBuffer = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder()).apply {
            repeat(1024) { frame -> putShort(if (frame == 0) 12000.toShort() else 0.toShort()); putShort(0) }
            flip()
        }
        fun output(effect: BaseAudioProcessor): ByteArray {
            effect.queueInput(input())
            return effect.output.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
        }
        for (effect in effects) {
            effect.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
            effect.flush(AudioProcessor.StreamMetadata.DEFAULT)
            assertTrue(effect.javaClass.simpleName, effect.isActive)
            val first = output(effect)
            assertEquals(4096, first.size)
            assertTrue(first.any { it != 0.toByte() })
            output(effect) // Leave filters/normalization with previous audio state.
            effect.flush(AudioProcessor.StreamMetadata.DEFAULT)
            assertArrayEquals(effect.javaClass.simpleName, first, output(effect))
            effect.reset()
        }
    }
}
