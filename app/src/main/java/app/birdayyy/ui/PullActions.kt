package app.birdayyy.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Standard pull-to-refresh with one extra step. A short pull and release leaves a button that opens older events;
 * a longer pull and release refreshes.
 */
@Composable
fun PullActions(
    enabled: Boolean,
    refreshing: Boolean,
    earlierShown: Boolean,
    onOlder: () -> Unit,
    onRefresh: () -> Unit,
    topInset: Dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val c = MaterialTheme.colorScheme
    val ctaArmPx = with(density) { 36.dp.toPx() }      // a short pull this far leaves the "older events" button open
    val ctaHoldPx = with(density) { 64.dp.toPx() }     // height the button strip rests at
    val refreshArmPx = with(density) { 112.dp.toPx() } // pulling past this refreshes on release
    val maxPx = with(density) { 190.dp.toPx() }
    val holdPx = with(density) { 56.dp.toPx() }        // height of the spinner strip while refreshing
    val damping = 0.6f

    var raw by remember { mutableFloatStateOf(0f) }
    var pull by remember { mutableFloatStateOf(0f) }
    var ctaOpen by remember { mutableStateOf(false) }
    val currentOlder by rememberUpdatedState(onOlder)
    val currentRefresh by rememberUpdatedState(onRefresh)
    val refreshReady = pull >= refreshArmPx

    LaunchedEffect(refreshReady) { if (refreshReady) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    var ctaWasVisible by remember { mutableStateOf(false) }
    val ctaVisible = pull >= ctaArmPx
    LaunchedEffect(ctaVisible) {
        if (ctaVisible && !ctaWasVisible && !ctaOpen) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        ctaWasVisible = ctaVisible
    }

    fun setPull(v: Float) { pull = v.coerceIn(0f, maxPx); raw = pull / damping }

    val connection = remember(enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
                if (pull > 0f && available.y < 0f) {
                    // Moving the thumb up closes the strip one-to-one.
                    val newPull = max(0f, pull + available.y)
                    val consume = newPull - pull
                    setPull(newPull)
                    if (newPull <= 0f) ctaOpen = false
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    raw += available.y
                    pull = min(maxPx, raw * damping)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                val target: Float
                when {
                    pull >= refreshArmPx -> { ctaOpen = false; currentRefresh(); target = 0f }
                    pull >= ctaArmPx || ctaOpen -> { ctaOpen = pull >= ctaArmPx || ctaOpen; target = if (ctaOpen) ctaHoldPx else 0f }
                    else -> { ctaOpen = false; target = 0f }
                }
                animate(pull, target, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> setPull(v) }
                return available
            }
        }
    }

    val headerPx = if (refreshing) max(pull, holdPx) else pull
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().offset { IntOffset(0, headerPx.roundToInt()) }) { content() }
        // Drawn after the list so the button receives taps.
        if (headerPx > 0f) {
            Box(
                Modifier.padding(top = topInset).fillMaxWidth().height(with(density) { headerPx.toDp() }).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    refreshing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    refreshReady -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Ic.Refresh, null, Modifier.size(22.dp), tint = c.primary)
                        Text("Release to refresh", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary)
                    }
                    else -> Column(
                        Modifier.graphicsLayer { alpha = ((headerPx - ctaArmPx * 0.5f) / (ctaArmPx * 0.8f)).coerceIn(0f, 1f) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            Modifier.clip(CircleShape).background(c.primaryContainer)
                                .clickable { currentOlder(); ctaOpen = false; /* strip closes via the spring below */ }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Ic.OlderEvents, null, Modifier.size(20.dp), tint = c.onPrimaryContainer)
                            Text(if (earlierShown) "Hide older events" else "See older events", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer)
                        }
                        if (!ctaOpen && headerPx > ctaHoldPx + 4f) Text("or keep pulling to refresh", fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Tapping the button closes the strip.
    LaunchedEffect(ctaOpen) {
        if (!ctaOpen && pull > 0f && !refreshing) {
            animate(pull, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ -> setPull(v) }
        }
    }
}
