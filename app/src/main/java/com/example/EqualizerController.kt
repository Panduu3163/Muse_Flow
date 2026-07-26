package com.example

import android.media.audiofx.Equalizer
import android.util.Log

private const val TAG = "EqualizerController"

/**
 * Wraps the real, on-device [Equalizer] audio effect, attached to whatever ExoPlayer audio
 * session is currently active (see [PlaybackService]). Not every device/OEM audio stack supports
 * this effect, so every call is defensive (`runCatching`) - a device without it just plays audio
 * unequalized, same as before this feature existed, rather than crashing playback.
 */
class EqualizerController {
    private var equalizer: Equalizer? = null

    /** (Re)attaches to [audioSessionId] - must be called again whenever ExoPlayer's own audio
     * session id changes (it can, e.g. across some route changes), since an [Equalizer] instance
     * is bound to one specific session for its whole lifetime. */
    fun attach(audioSessionId: Int) {
        release()
        equalizer = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Equalizer unavailable on this device", it) }
            .getOrNull()

        // Publish what this device actually supports, read from the *live* player session.
        // Probing a throwaway effect on session 0 instead fails on modern Android ("Cannot
        // initialize effect engine ... Error: -3"), which would make a perfectly capable device
        // look unsupported - so the settings screen reads these values rather than probing.
        equalizer?.let { EqualizerCapabilitiesHolder.publish(it) }
    }

    fun apply(settings: EqualizerSettings) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = settings.enabled
            for (band in settings.bandLevelsMillibel.indices) {
                if (band < eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), settings.bandLevelsMillibel[band].toShort())
                }
            }
        }.onFailure { Log.w(TAG, "Failed to apply equalizer settings", it) }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}

/**
 * Capabilities of the device equalizer, captured from the live playback session by
 * [EqualizerController] and read by the settings UI.
 *
 * A plain process-wide holder rather than something injected: [PlaybackService] and the equalizer
 * screen live in the same process but have no other channel between them, and this is read-only
 * descriptive data (band count, frequencies, ranges) that never changes for a given device.
 */
object EqualizerCapabilitiesHolder {
    @Volatile
    var capabilities: EqualizerCapabilities = EqualizerCapabilities(supported = false)
        private set

    fun publish(equalizer: Equalizer) {
        capabilities = runCatching {
            val range = equalizer.bandLevelRange
            EqualizerCapabilities(
                supported = true,
                bands = (0 until equalizer.numberOfBands).map { band ->
                    EqualizerBand(
                        index = band,
                        centerFrequencyHz = equalizer.getCenterFreq(band.toShort()) / 1000,
                    )
                },
                minLevelMillibel = range[0].toInt(),
                maxLevelMillibel = range[1].toInt(),
                presets = (0 until equalizer.numberOfPresets).map { preset ->
                    equalizer.getPresetName(preset.toShort())
                },
            )
        }.getOrElse { EqualizerCapabilities(supported = false) }
    }
}
