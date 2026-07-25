package com.example

import android.app.Application
import android.media.audiofx.Equalizer
import com.example.audio.EQ_BAND_FREQUENCIES
import com.example.audio.EQ_MAX_GAIN_DB
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One band of the device equalizer, as the UI needs to draw it. */
data class EqualizerBand(
    val index: Int,
    val centerFrequencyHz: Int,
) {
    /** "60 Hz" / "2.4 kHz" - centre frequencies come back in millihertz. */
    val label: String
        get() = if (centerFrequencyHz >= 1000) {
            val khz = centerFrequencyHz / 1000f
            if (khz % 1f == 0f) "${khz.toInt()} kHz" else "${"%.1f".format(khz)} kHz"
        } else {
            "$centerFrequencyHz Hz"
        }
}

/**
 * What the device's equalizer actually supports. Queried once from a throwaway [Equalizer] on the
 * global output session, because the live effect instance belongs to [PlaybackService] and isn't
 * reachable from here.
 *
 * [supported] is false on audio stacks that don't implement the effect at all - the screen then
 * says so plainly instead of showing sliders that do nothing.
 */
data class EqualizerCapabilities(
    val supported: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val minLevelMillibel: Int = -1500,
    val maxLevelMillibel: Int = 1500,
    val presets: List<String> = emptyList(),
) {
    companion object {
        fun probe(): EqualizerCapabilities {
            var equalizer: Equalizer? = null
            return try {
                // Session 0 = global output mix; used only to read capabilities, never enabled.
                equalizer = Equalizer(0, 0)
                val range = equalizer.bandLevelRange
                EqualizerCapabilities(
                    supported = true,
                    bands = (0 until equalizer.numberOfBands).map { band ->
                        EqualizerBand(
                            index = band,
                            // getCenterFreq returns millihertz.
                            centerFrequencyHz = equalizer.getCenterFreq(band.toShort()) / 1000,
                        )
                    },
                    minLevelMillibel = range[0].toInt(),
                    maxLevelMillibel = range[1].toInt(),
                    presets = (0 until equalizer.numberOfPresets).map { preset ->
                        equalizer.getPresetName(preset.toShort())
                    },
                )
            } catch (e: Exception) {
                EqualizerCapabilities(supported = false)
            } finally {
                runCatching { equalizer?.release() }
            }
        }
    }
}

/**
 * Equalizer state.
 *
 * Writes go to [EqualizerRepository]; [PlaybackService] already collects that same Flow and
 * re-applies it to the live audio effect, so changes take effect while a track is playing without
 * this ViewModel touching the player at all.
 */
class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EqualizerRepository.getInstance(application)

    /**
     * MuseFlow's own band layout, not the device's.
     *
     * Equalization runs as an ExoPlayer [com.example.audio.EqualizerAudioProcessor] inside the
     * playback pipeline, so the bands are ours to define and are **always available** - no
     * AudioFlinger involvement, and nothing for a vendor audio HAL to refuse. (The platform
     * `AudioEffect` path is still applied in parallel where a device allows it, but it is no
     * longer what the UI is built on.)
     */
    val capabilities: EqualizerCapabilities = EqualizerCapabilities(
        supported = true,
        bands = EQ_BAND_FREQUENCIES.mapIndexed { index, frequency ->
            EqualizerBand(index = index, centerFrequencyHz = frequency)
        },
        minLevelMillibel = (-EQ_MAX_GAIN_DB * 100).toInt(),
        maxLevelMillibel = (EQ_MAX_GAIN_DB * 100).toInt(),
        presets = listOf("Flat", "Bass boost", "Treble boost", "Rock", "Pop", "Dance", "Jazz"),
    )

    val settings: StateFlow<EqualizerSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EqualizerSettings())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun setBandLevel(bandIndex: Int, levelMillibel: Int) {
        viewModelScope.launch { repository.setBandLevel(bandIndex, levelMillibel) }
    }

    fun resetToFlat() {
        viewModelScope.launch { repository.resetToFlat() }
    }

    /**
     * Applies a named device preset by computing the levels it produces, then storing them as
     * ordinary band values - so a preset stays editable afterwards rather than locking the sliders.
     *
     * The curve is derived arithmetically rather than by opening another effect: a second
     * [Equalizer] on the same session would fight the one [PlaybackService] already owns, and one
     * on session 0 isn't permitted at all.
     */
    fun applyPreset(presetIndex: Int) {
        val levels = presetCurve(presetIndex) ?: return
        viewModelScope.launch {
            levels.forEachIndexed { band, level -> repository.setBandLevel(band, level) }
            repository.setEnabled(true)
        }
    }

    /**
     * Approximates a named preset as a gain curve across however many bands this device exposes.
     * Positions are normalised (0 = lowest band, 1 = highest) so the same shape works whether the
     * device reports 5 bands or 10.
     */
    private fun presetCurve(presetIndex: Int): List<Int>? {
        val name = capabilities.presets.getOrNull(presetIndex)?.lowercase() ?: return null
        val bands = capabilities.bands
        if (bands.isEmpty()) return null

        val max = capabilities.maxLevelMillibel
        val min = capabilities.minLevelMillibel

        return bands.mapIndexed { index, _ ->
            val position = if (bands.size == 1) 0.5f else index.toFloat() / (bands.size - 1)
            val gain = when {
                "bass" in name -> 1f - position * 1.6f              // lift lows, cut highs
                "treble" in name -> position * 1.6f - 0.6f          // opposite
                "rock" in name -> if (position < 0.3f || position > 0.7f) 0.55f else -0.3f
                "pop" in name -> if (position in 0.3f..0.7f) 0.5f else -0.2f
                "jazz" in name || "classical" in name -> if (position > 0.6f) 0.4f else 0.15f
                "dance" in name -> if (position < 0.4f) 0.7f else 0.2f
                "flat" in name || "normal" in name -> 0f
                else -> 0f
            }
            (gain.coerceIn(-1f, 1f) * if (gain >= 0) max else -min).toInt()
        }
    }
}
