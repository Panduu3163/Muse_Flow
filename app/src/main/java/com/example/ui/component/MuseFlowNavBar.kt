package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TopLevelDestination
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MuseFlowSpacing
import com.example.ui.theme.MuseFlowMotion
import dev.chrisbanes.haze.HazeState

/**
 * MuseFlow's floating bottom navigation bar.
 *
 * One source-backed frosted surface with a sliding selection capsule. The selected tab grows
 * horizontally to show its label; the other tabs keep reachable icon-only targets. Animations
 * are interaction-driven and follow reduced motion, with no permanent redraw clock.
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
    backdrop: HazeState? = null,
) {
    if (destinations.isEmpty()) return
    val reducedMotion = LocalReducedMotion.current
    val selection by animateFloatAsState(
        destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0).toFloat(),
        if (reducedMotion) snap() else tween(MuseFlowMotion.standardMillis, easing = FastOutSlowInEasing),
        label = "nav_selection_position",
    )
    val selectionFill = MaterialTheme.colorScheme.secondary.copy(alpha = .14f)
    val selectionEdge = MaterialTheme.colorScheme.secondary.copy(alpha = .18f)
    val windowWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() }
    val sideInset = when {
        windowWidthDp < 340.dp -> 16.dp
        windowWidthDp < 380.dp -> 24.dp
        else -> 32.dp
    }
    val availableWidth = windowWidthDp - sideInset * 2 - MuseFlowSpacing.small * 2
    val requestedWeight = 2f + (LocalDensity.current.fontScale - 1f).coerceIn(0f, 1f)
    val maxWeight = (availableWidth.value / 48f - (destinations.size - 1)).coerceAtLeast(2f)
    val selectedWeight = requestedWeight.coerceAtMost(maxWeight)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The system-nav-bar inset is applied once, by the Column wrapping this and
            // MiniPlayer in MainActivity - not here. Applying it to this Surface alone left
            // MiniPlayer unprotected on every screen where this bar is hidden (a playlist detail,
            // History, ...), so it drew flush against the gesture/navigation bar there.
            .padding(horizontal = sideInset, vertical = 12.dp)
            .navigationGlass(backdrop)
            .testTag("navigation_glass"),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = MuseFlowSpacing.small, vertical = 4.dp)) {
          Canvas(Modifier.matchParentSize()) {
            val unit = size.width / (destinations.size - 1 + selectedWeight)
            val logicalX = unit * selection
            val width = unit * selectedWeight
            val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - logicalX - width else logicalX
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(selectionFill, Offset(x, 0f), Size(width, size.height), radius)
            drawRoundRect(selectionEdge, Offset(x + .5.dp.toPx(), .5.dp.toPx()),
                Size((width - 1.dp.toPx()).coerceAtLeast(0f), (size.height - 1.dp.toPx()).coerceAtLeast(0f)), radius, style = Stroke(.75.dp.toPx()))
          }
          Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val reducedMotion = LocalReducedMotion.current
                val weight by animateFloatAsState(
                    targetValue = if (selected) selectedWeight else 1f,
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
}

@Composable
private fun NavBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .88f else if (selected) 1f else .94f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f),
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
    Row(
        modifier = modifier
            .clip(CircleShape)
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
