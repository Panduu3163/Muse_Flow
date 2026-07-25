package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The shapes an equalizer band can take. */
enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF }

/**
 * A single second-order (biquad) IIR filter — the standard building block of a parametric
 * equalizer.
 *
 * Coefficients follow the well-known RBJ "Audio EQ Cookbook" formulas. Each instance keeps its own
 * delay line, so **one filter per channel is required** — sharing an instance across left and right
 * would mix the channels' history together and smear the stereo image.
 *
 * State and coefficients are `Double`; audio is processed in floating point and only converted
 * back to 16-bit at the end, which avoids the rounding artefacts of integer-domain filtering.
 */
class BiquadFilter(
    private val sampleRate: Int,
) {
    // Feed-forward / feedback coefficients, normalised by a0.
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Delay line (Direct Form I): two previous inputs and outputs.
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    /**
     * Recomputes coefficients for a band.
     *
     * @param frequencyHz centre (peaking) or corner (shelf) frequency
     * @param gainDb positive boosts, negative cuts; 0 is a no-op
     * @param q bandwidth — higher is narrower. Ignored for shelves, which use a fixed slope.
     */
    fun configure(type: FilterType, frequencyHz: Double, gainDb: Double, q: Double) {
        // Above Nyquist a filter is meaningless; clamp so a high band on a low-rate stream
        // degrades to "just below Nyquist" rather than producing NaN coefficients.
        val f = frequencyHz.coerceIn(20.0, sampleRate / 2.0 - 100.0)
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * f / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q.coerceAtLeast(0.1))

        val a0: Double
        when (type) {
            FilterType.PEAKING -> {
                a0 = 1.0 + alpha / a
                b0 = (1.0 + alpha * a) / a0
                b1 = (-2.0 * cosOmega) / a0
                b2 = (1.0 - alpha * a) / a0
                a1 = (-2.0 * cosOmega) / a0
                a2 = (1.0 - alpha / a) / a0
            }

            FilterType.LOW_SHELF -> {
                val beta = 2.0 * sqrt(a) * alpha
                a0 = (a + 1.0) + (a - 1.0) * cosOmega + beta
                b0 = (a * ((a + 1.0) - (a - 1.0) * cosOmega + beta)) / a0
                b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosOmega)) / a0
                b2 = (a * ((a + 1.0) - (a - 1.0) * cosOmega - beta)) / a0
                a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosOmega)) / a0
                a2 = ((a + 1.0) + (a - 1.0) * cosOmega - beta) / a0
            }

            FilterType.HIGH_SHELF -> {
                val beta = 2.0 * sqrt(a) * alpha
                a0 = (a + 1.0) - (a - 1.0) * cosOmega + beta
                b0 = (a * ((a + 1.0) + (a - 1.0) * cosOmega + beta)) / a0
                b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosOmega)) / a0
                b2 = (a * ((a + 1.0) + (a - 1.0) * cosOmega - beta)) / a0
                a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosOmega)) / a0
                a2 = ((a + 1.0) - (a - 1.0) * cosOmega - beta) / a0
            }
        }
    }

    /** Direct Form I difference equation. */
    fun process(input: Double): Double {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }

    /** Clears the delay line — call on seek/track change so the previous audio's tail doesn't
     * bleed into the new one as a click. */
    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}
