package app.birdayyy.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
 * Two-level pull-down at the top of a list: a short pull and release reveals older events,
 * a long pull and release refreshes.
 */
@Composable
fun TwoLevelPull(
    enabled: Boolean,
    refreshing: Boolean,
    earlierShown: Boolean,
    onEarlier: () -> Unit,
    onRefresh: () -> Unit,
    topInset: Dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val shortPx = with(density) { 56.dp.toPx() }
    val longPx = with(density) { 150.dp.toPx() }
    val holdPx = with(density) { 56.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var lastStage by remember { mutableIntStateOf(0) }
    val stage = when { pull >= longPx -> 3; pull >= shortPx -> 2; pull > 0f -> 1; else -> 0 }
    LaunchedEffect(stage) {
        if (stage > lastStage && stage >= 2) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastStage = stage
    }
    val currentEarlier by rememberUpdatedState(onEarlier)
    val currentRefresh by rememberUpdatedState(onRefresh)
    val connection = remember(enabled, shortPx, longPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
                if (pull > 0f && available.y < 0f) {
                    val consume = max(available.y, -pull)
                    pull += consume
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    pull += available.y * 0.5f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                val s = when { pull >= longPx -> 3; pull >= shortPx -> 2; else -> 1 }
                if (s == 2) currentEarlier()
                if (s == 3) currentRefresh()
                animate(pull, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { v, _ -> pull = v }
                return available
            }
        }
    }
    val headerPx = if (refreshing) max(pull, holdPx) else pull
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        if (headerPx > 0f) {
            Box(Modifier.padding(top = topInset).fillMaxWidth().height(with(density) { headerPx.toDp() }).clipToBounds(), contentAlignment = Alignment.Center) {
                val label = when {
                    refreshing -> "Refreshing…"
                    stage >= 3 -> "Release to refresh"
                    stage == 2 -> if (earlierShown) "Keep pulling to refresh" else "Release to see earlier"
                    else -> if (earlierShown) "Pull down to refresh" else "Pull to see earlier"
                }
                val hint = when {
                    refreshing || stage >= 3 -> null
                    stage == 2 && !earlierShown -> "or keep pulling to refresh"
                    !earlierShown -> "or pull further to refresh"
                    else -> null
                }
                Column(Modifier.alpha(min(1f, headerPx / shortPx)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val glyph: @Composable () -> Unit = {
                            when {
                                refreshing -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                stage >= 3 -> Icon(Ic.Refresh, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                else -> Icon(Ic.ArrowDown, null, Modifier.size(20.dp).rotate(if (stage == 2) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        glyph()
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (stage >= 3 || refreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        glyph()
                    }
                    if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Box(Modifier.fillMaxSize().offset { IntOffset(0, headerPx.roundToInt()) }) { content() }
    }
}
