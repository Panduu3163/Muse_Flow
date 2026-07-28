package com.example.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * A small, named set of reused animation specs - matching Echo's own level of centralization
 * (a few named constants, most transitions still specified inline) rather than a bigger framework
 * this app doesn't need. Two shapes cover what MuseFlow actually reaches for: a settled spring for
 * content that's expanding/collapsing/being pushed onto screen, and a short linear fade/tween for
 * things that just need to not cut instantly.
 */
object Motion {
    /** No overshoot, moderate speed - screen pushes/pops and content-size changes (e.g. an "About"
     * section expanding). */
    fun <T> emphasized(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** A short, linear fade - paired with [emphasized] for enter/exit transitions where the fade
     * should finish before the movement settles. */
    fun <T> quickFade(durationMillis: Int = 200): FiniteAnimationSpec<T> = tween(durationMillis)
}
