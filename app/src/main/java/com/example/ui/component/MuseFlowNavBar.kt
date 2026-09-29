package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TopLevelDestination
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MuseFlowShapes
import com.example.ui.theme.MuseFlowSpacing
import com.example.ui.theme.MuseFlowMotion

/**
 * MuseFlow's floating bottom navigation bar.
 *
 * A rounded, slightly translucent bar that hovers above the content rather than sitting flush at
 * the window edge, so the themed background stays visible behind it. The selected tab grows
 * horizontally to show its label beside the icon; the other tabs retain reachable icon-only
 * targets. A subtle scale bump acknowledges selection without delaying navigation.
 *
 * Deliberately NOT tinted from the current track's album palette (tried once, reverted at the
 * user's explicit request) - this bar's colour follows the app theme only, not whatever's
 * playing. The colour "seam" this was originally meant to fix was actually the system's own
 * edge-to-edge scrim (see MainActivity.onCreate's enableEdgeToEdge call), not this bar.
 */
@Composable
fun MuseFlowNavBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideInset = when {
        LocalConfiguration.current.screenWidthDp < 340 -> 16.dp
        LocalConfiguration.current.screenWidthDp < 380 -> 24.dp
        else -> 32.dp
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The system-nav-bar inset is applied once, by the Column wrapping this and
            // MiniPlayer in MainActivity - not here. Applying it to this Surface alone left
            // MiniPlayer unprotected on every screen where this bar is hidden (a playlist detail,
            // History, ...), so it drew flush against the gesture/navigation bar there.
            .padding(horizontal = sideInset, vertical = 12.dp)
            .liquidSurface(MuseFlowShapes.navigation),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = MuseFlowSpacing.small),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val reducedMotion = LocalReducedMotion.current
                val weight by animateFloatAsState(
                    targetValue = if (selected) 2f else 1f,
                    animationSpec = if (reducedMotion) snap() else tween(MuseFlowMotion.standardMillis),
                    label = "nav_item_width_${destination.route}",
                )
                NavBarItem(
                    destination = destination,
                    selected = selected,
                    onClick = { onNavigate(destination) },
                    modifier = Modifier.weight(weight),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "nav_item_scale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (reducedMotion) snap() else tween(MuseFlowMotion.quickMillis),
        label = "nav_item_color",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondary.copy(alpha = .12f)
        } else {
            Color.Transparent
        },
        animationSpec = if (reducedMotion) snap() else tween(MuseFlowMotion.quickMillis),
        label = "nav_item_pill",
    )

    // The pill and scale bump convey selection; the default ripple would spill outside the shape.
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(pillColor)
            .semantics { contentDescription = destination.label }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            // Compact density is 0.88x, so 56 logical dp still gives a 49dp physical target.
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp)
            .testTag("nav_item_${destination.route}"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp).scale(scale),
        )
        AnimatedVisibility(
            visible = selected,
            enter = if (reducedMotion) fadeIn(snap()) else
                expandHorizontally(animationSpec = tween(MuseFlowMotion.standardMillis)) +
                    fadeIn(tween(MuseFlowMotion.quickMillis)),
            exit = if (reducedMotion) fadeOut(snap()) else
                shrinkHorizontally(animationSpec = tween(MuseFlowMotion.standardMillis)) +
                    fadeOut(tween(MuseFlowMotion.quickMillis)),
        ) {
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
