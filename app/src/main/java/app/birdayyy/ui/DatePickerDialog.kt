package app.birdayyy.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ItemHeight = 48.dp

/** One scrolling column that snaps so the centered row is the selection. */
@Composable
private fun Wheel(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, dim: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(state, androidx.compose.foundation.gestures.snapping.SnapPosition.Center)
    val centered by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: selected
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving -> if (!moving) onSelect(centered) }
    }
    LaunchedEffect(selected, items.size) {
        if (!state.isScrollInProgress && centered != selected) state.scrollToItem(selected.coerceIn(0, items.lastIndex))
    }
    Box(modifier.height(ItemHeight * 3).alpha(if (dim) 0.35f else 1f), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Spacer(Modifier.height(ItemHeight))
            HorizontalDivider(color = c.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(ItemHeight - 2.dp))
            HorizontalDivider(color = c.onSurfaceVariant.copy(alpha = 0.5f))
        }
        LazyColumn(Modifier.fillMaxSize(), state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = ItemHeight)) {
            itemsIndexed(items) { i, label ->
                Box(Modifier.fillMaxWidth().height(ItemHeight), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 17.sp, fontWeight = if (i == centered) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == centered) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1)
                }
            }
        }
    }
}

/** Date picker with day, month and year wheels and an "Include year" switch. Year comes back null when excluded. */
@Composable
fun WheelDatePicker(
    initialMonth: Int?, initialDay: Int, initialYear: Int?,
    onPicked: (month: Int, day: Int, year: Int?) -> Unit, onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val thisYear = LocalDate.now().year
    val years = remember { (1900..thisYear + 5).map { it.toString() } }
    val months = remember { Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) } }
    var month by remember { mutableIntStateOf((initialMonth ?: LocalDate.now().monthValue) - 1) }
    var day by remember { mutableIntStateOf(initialDay.coerceIn(1, 31) - 1) }
    var year by remember { mutableIntStateOf((initialYear ?: thisYear) - 1900) }
    var includeYear by remember { mutableStateOf(initialMonth == null || initialYear != null) }
    val realYear = 1900 + year
    val maxDay = YearMonth.of(if (includeYear) realYear else 2000, month + 1).lengthOfMonth()
    val shownDay = day.coerceAtMost(maxDay - 1)
    val days = remember(maxDay) { (1..maxDay).map { "%02d".format(it) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text("${shownDay + 1} ${months[month]}" + if (includeYear) " $realYear" else "", fontSize = 32.sp, fontWeight = FontWeight.Normal)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Wheel(days, shownDay, { day = it }, Modifier.weight(1f))
                    Wheel(months, month, { month = it }, Modifier.weight(1f))
                    Wheel(years, year, { year = it }, Modifier.weight(1f), dim = !includeYear)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Include year", Modifier.weight(1f), fontSize = 16.sp)
                    Switch(includeYear, { includeYear = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
                    TextButton({ onPicked(month + 1, shownDay + 1, if (includeYear) realYear else null); onDismiss() }) { Text("OK", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
