@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.birdayyy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birdayyy.data.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs

val dateIcons: List<ImageVector> by lazy {
    listOf(Ic.Cake, Ic.Champagne, Ic.Heart, Ic.Star, Ic.Gift, Ic.Sparkle)
}

fun iconFor(e: DateEntry, meta: DateMeta?): ImageVector = when {
    meta != null -> dateIcons[meta.icon.coerceIn(dateIcons.indices)]
    e.kind == EventKind.Birthday -> Ic.Cake
    e.kind == EventKind.Anniversary -> Ic.Champagne
    else -> Ic.Star
}

fun styleIcon(style: ReminderStyle, fallback: ImageVector) = when (style) {
    ReminderStyle.Silent -> Ic.VolumeOff
    ReminderStyle.Never -> Ic.NotificationsOff
    ReminderStyle.Priority -> Ic.NotificationsActive
    else -> fallback
}

/** Dark "ink" button used on hero tiles (dark brown on light themes, primary on dark ones). */
@Composable
private fun heroInk(): Pair<Color, Color> =
    if (isDark()) MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary else Color(0xFF3D2E00) to Color.White

/** Pseudo-random tile color per event, never the same as the two tiles before it. */
private fun assignTileColors(events: List<UpcomingEvent>): Map<String, Int> {
    val out = HashMap<String, Int>()
    var prev = -1; var prev2 = -1
    events.forEach { e ->
        var c = kotlin.math.abs((e.id + "tile").hashCode()) % 5
        var guard = 0
        while ((c == prev || c == prev2) && guard++ < 5) c = (c + 1) % 5
        out[e.id] = c; prev2 = prev; prev = c
    }
    return out
}

@Composable
private fun grayTile(): Color = if (isDark()) Color(0xFF3A3837) else Color(0xFFE4E1DF)
private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun HomeScreen(vm: AppViewModel, persons: List<Person>, events: List<UpcomingEvent>, data: DataState, s: Settings) {
    if ((data.loaded && !data.canRead) || vm.debugPerm) { PermissionScreen(vm); return }
    if ((data.loaded && persons.isEmpty() && data.contactCount == 0) || vm.debugEmpty == "empty-contacts") { NoContactsScreen(vm); return }
    var filter by rememberSaveable { mutableStateOf<EventKind?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val separate = s.mode == ListMode.Separate
    val matches = { e: UpcomingEvent ->
        s.kindEnabled(e.date.kind) && (filter == null || e.date.kind == filter) &&
            (query.isBlank() || e.person.name.contains(query, true)) && (!separate || vm.sourceTab in e.person.sources)
    }
    val visible = events.filter(matches)
    val past = remember(persons, filter, query, separate, vm.sourceTab, s.birthdays, s.anniversaries, s.others) {
        previous(persons, java.time.LocalDate.now()).filter(matches)
    }
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val earlierAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val earlierGroups = remember(past) { groupEarlier(past) }
    val earlierCount = if (past.isEmpty()) 1 else past.size + earlierGroups.size
    // Index of the "Today" section header (or the first section when nobody celebrates today).
    val todayAnchor = if (vm.showEarlier) 1 + earlierCount else 0
    // Show "Back to today" the moment the Today section leaves its resting place at the top of the list.
    val awayFromToday by remember(todayAnchor) {
        derivedStateOf {
            gridState.firstVisibleItemIndex != todayAnchor || gridState.firstVisibleItemScrollOffset > 6
        }
    }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    /** Returns to Today and collapses Earlier, animating only the distance actually needed. */
    val backToToday: () -> Unit = {
        scope.launch {
            if (!vm.showEarlier) {
                if (gridState.firstVisibleItemIndex > 6) gridState.scrollToItem(6)
                gridState.animateScrollToItem(0)
                return@launch
            }
            // Scroll down to Today while the older events fade out, then remove them once they are invisible.
            kotlinx.coroutines.coroutineScope {
                launch { if (gridState.firstVisibleItemIndex < todayAnchor) gridState.animateScrollToItem(todayAnchor) }
                launch { earlierAlpha.animateTo(0f, androidx.compose.animation.core.tween(380)) }
            }
            vm.showEarlier = false
            // Let the list re-measure without the older items before touching the scroll position; scrolling by the old
            // indices while they are still being removed is what made the list jump.
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            earlierAlpha.snapTo(1f)
            if (gridState.firstVisibleItemIndex > 6) gridState.scrollToItem(6)
            if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) gridState.animateScrollToItem(0)
        }
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val dismissSearch = { if (searching) { searching = false; query = ""; focusManager.clearFocus() } }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }
    // Close search only when the user scrolls the list (not when typing reshuffles the results).
    LaunchedEffect(searching) {
        if (!searching) return@LaunchedEffect
        snapshotFlow { gridState.isScrollInProgress }.collect { moving ->
            if (moving) {
                val i0 = gridState.firstVisibleItemIndex; val o0 = gridState.firstVisibleItemScrollOffset
                snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }.first { (i, o) ->
                    !gridState.isScrollInProgress || i != i0 || kotlin.math.abs(o - o0) > 120
                }.let { (i, o) -> if (i != i0 || kotlin.math.abs(o - o0) > 120) dismissSearch() }
            }
        }
    }
    val noDates = remember(query, data.allContacts, persons) {
        if (query.isBlank()) emptyList() else data.allContacts.filter { c -> c.name.contains(query, true) && persons.none { it.contactId == c.contactId } }
    }
    GlassPage(
        topBar = {
            Column(Modifier.padding(bottom = 10.dp)) {
                BarTitle("Birdayyy", actions = {
                    IconButton(
                        { if (searching) dismissSearch() else searching = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (searching) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (searching) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Icon(Ic.Search, "Search") }
                    IconButton({ vm.push(Dest.Settings) }) { Icon(Ic.Tune, "Settings") }
                })
                if (searching) BdField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), placeholder = "Search people", leading = Ic.Search,
                    focusRequester = focus, outlined = true, trailing = if (query.isNotEmpty()) ({
                        Icon(Ic.Close, "Clear search", Modifier.size(22.dp).clip(CircleShape).clickable { query = "" })
                    }) else null)
                if (separate) SegPills(SourceType.entries.map { it.label }, SourceType.entries.indexOf(vm.sourceTab), { vm.sourceTab = SourceType.entries[it] },
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), solid = true)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("All", filter == null, { filter = null })
                    PillChip("Birthdays", filter == EventKind.Birthday, { filter = EventKind.Birthday })
                    PillChip("Anniversaries", filter == EventKind.Anniversary, { filter = EventKind.Anniversary })
                    PillChip("Other", filter == EventKind.Other, { filter = EventKind.Other })
                }
            }
        },
        floating = {
            if (awayFromToday) Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)
                    .shadow(8.dp, CircleShape, ambientColor = Color(0x33602010), spotColor = Color(0x33602010)).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer).clickable { backToToday() }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Ic.Today, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Back to today", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            FloatingActionButton({ vm.push(Dest.AddPerson) }, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).size(64.dp),
                shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Ic.Add, "Add a person or date", Modifier.size(30.dp))
            }
        },
    ) { pad ->
        if ((visible.isEmpty() && query.isBlank() && filter == null) || vm.debugEmpty == "empty-dates") {
            NoDatesState(vm, Modifier.padding(top = pad.calculateTopPadding()))
        } else {
            val todayList = visible.filter { it.daysUntil == 0 }
            val rest = visible.filter { it.daysUntil != 0 }
            val tileColors = remember(visible, past) { assignTileColors(todayList + rest + past) }
            val anchorIndex = todayAnchor
            LaunchedEffect(vm.showEarlier) {
                if (vm.showEarlier) {
                    gridState.scrollToItem(anchorIndex)
                    gridState.animateScrollBy(-220f)
                }
            }
            PullActions(
                enabled = !searching, refreshing = vm.refreshing, earlierShown = vm.showEarlier,
                onOlder = { if (vm.showEarlier) backToToday() else vm.showEarlier = true },
                onRefresh = { vm.refresh(true) },
                topInset = pad.calculateTopPadding(),
            ) {
            LazyVerticalStaggeredGrid(
                StaggeredGridCells.Fixed(2), state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize().pointerInput(searching) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        waitForUpOrCancellation(PointerEventPass.Initial)
                        scope.launch { kotlinx.coroutines.delay(180); dismissSearch() }
                    }
                }.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Show earlier events") { vm.showEarlier = true; true },
                        CustomAccessibilityAction("Refresh contacts") { vm.refresh(true); true },
                    )
                },
            ) {
                if (vm.showEarlier) item(span = StaggeredGridItemSpan.FullLine, key = "earlier-row") {
                    Box(Modifier.graphicsLayer { alpha = earlierAlpha.value }) { EarlierRow(true, past.size, { backToToday() }, { vm.refresh() }) }
                }
                if (vm.showEarlier) {
                    if (past.isEmpty()) item(span = StaggeredGridItemSpan.FullLine, key = "no-past") {
                        Text("Nothing in the last 12 months.", Modifier.graphicsLayer { alpha = earlierAlpha.value }.padding(horizontal = 8.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    earlierGroups.forEach { (title, list) ->
                        item(span = StaggeredGridItemSpan.FullLine, key = "pg-$title") { SectionLabel(title, Modifier.graphicsLayer { alpha = earlierAlpha.value }.padding(top = 4.dp)) }
                        items(list, key = { "p" + it.id }) { Box(Modifier.graphicsLayer { alpha = earlierAlpha.value }) { EventTile(vm, it, s, 0) } }
                    }
                }
                if (todayList.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "h-today") { SectionLabel("Today · ${todayList.size}") }
                    items(todayList, key = { "t" + it.id }, span = { StaggeredGridItemSpan.FullLine }) { TodayCard(vm, it, s, tileColors[it.id] ?: 1) }
                }
                val week = rest.filter { it.daysUntil in 1..7 }
                val later = rest.filter { it.daysUntil > 7 }
                if (week.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "h-week") { SectionLabel("This week") }
                    items(week, key = { "w" + it.id }) { EventTile(vm, it, s, tileColors[it.id] ?: 0) }
                }
                val thisYear = java.time.LocalDate.now().year
                later.groupBy { java.time.YearMonth.from(it.on) }.toSortedMap().forEach { (ym, list) ->
                    val name = ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                    val title = if (ym.year == thisYear) name else "$name ${ym.year}"
                    item(span = StaggeredGridItemSpan.FullLine, key = "lg-$ym") { SectionLabel(title, Modifier.padding(top = 4.dp)) }
                    items(list, key = { "l" + it.id }) { EventTile(vm, it, s, tileColors[it.id] ?: 0) }
                }
                if (noDates.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "h-nodates") { SectionLabel("No dates yet", Modifier.padding(top = 4.dp)) }
                    items(noDates.take(30), span = { StaggeredGridItemSpan.FullLine }, key = { "n" + it.id }) { c ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PersonAvatar(c, 48.dp)
                            Text(c.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            BigButton("Add a date", { vm.addForContact = c.contactId!! to c.name; vm.push(Dest.AddPerson) }, tonal = true, height = 40, hPad = 16)
                        }
                    }
                }
                if (visible.isEmpty() && noDates.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("Nothing matches.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }
    }
}

/**
 * Splits earlier events into groups, oldest first so scrolling up goes further back in time:
 * one group per month for anything older than two weeks, then "Last 2 weeks" just above Today.
 */
private fun groupEarlier(past: List<UpcomingEvent>): List<Pair<String, List<UpcomingEvent>>> {
    val now = java.time.LocalDate.now()
    val recent = past.filter { it.daysUntil >= -14 }.sortedBy { it.on }
    val older = past.filter { it.daysUntil < -14 }
    val months = older.groupBy { java.time.YearMonth.from(it.on) }.toSortedMap()
    val out = months.map { (ym, list) ->
        val name = ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        (if (ym.year == now.year) name else "$name ${ym.year}") to list.sortedBy { it.on }
    }.toMutableList()
    if (recent.isNotEmpty()) out += "Last 2 weeks" to recent
    return out
}

/** Header of the revealed Earlier section; tapping it hides the section again. */
@Composable
private fun EarlierRow(shown: Boolean, count: Int, onToggle: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel("Earlier · last 12 months", Modifier.weight(1f))
        Text("Hide earlier", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EventTile(vm: AppViewModel, e: UpcomingEvent, s: Settings, colorIdx: Int) {
    val pal = LocalPalette.current
    val meta = s.dateMeta["${e.person.id}|${e.date.label}"]
    val size = (88 + abs((e.person.id + e.date.key).hashCode()) % 4 * 8).dp
    Surface({ vm.push(Dest.Person(e.person.id)) }, shape = RoundedCornerShape(28.dp), color = if (e.daysUntil < 0) grayTile() else pal.tile(colorIdx)) {
        Box {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonAvatar(e.person, size, gray = e.daysUntil < 0)
                Column {
                    Text(e.person.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(e.subtitle(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.daysUntil == 0) Tag("Today", filled = true)
                else if (e.daysUntil < 0) Tag(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy").format(e.on))
                else Tag(dateFmt.format(e.on) + if (e.daysUntil in 1..2) " · ${e.relative()}" else "")
            }
            Icon(styleIcon(s.styleFor(e.person.id, e.date.kind), iconFor(e.date, meta)), null,
                Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .55f)).padding(7.dp))
        }
    }
}

@Composable
private fun NoDatesState(vm: AppViewModel, modifier: Modifier) {
    val pal = LocalPalette.current
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            Cluster(Modifier.offset(46.dp, 56.dp), 130, DesignShapes.Cookie, pal.avatar(1), Ic.Calendar, 54)
            Cluster(Modifier.offset(200.dp, 10.dp), 110, DesignShapes.Flower, pal.avatar(1), Ic.Cake, 46)
            Cluster(Modifier.offset(236.dp, 140.dp), 84, DesignShapes.Squircle, pal.avatar(2), Ic.Search, 36)
        }
        EmptyStateText("No dates found yet", "We checked your sources, but none of your contacts has a birthday or anniversary saved.")
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton("Add a birthday", { vm.push(Dest.AddPerson) }, Modifier.fillMaxWidth(), icon = Ic.Add)
            BigButton("Check my sources again", { vm.refresh() }, Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Cluster(modifier: Modifier, size: Int, shape: androidx.compose.ui.graphics.Shape, color: Color, icon: ImageVector, iconSize: Int) {
    Box(modifier.size(size.dp).clip(shape).background(color), contentAlignment = Alignment.Center) {
        if (iconSize > 1) Icon(icon, null, Modifier.size(iconSize.dp), tint = LocalPalette.current.onAvatar)
    }
}

@Composable
private fun NoContactsScreen(vm: AppViewModel) {
    val pal = LocalPalette.current
    GlassPage { _ ->
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Birdayyy", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp)
                IconButton({ vm.push(Dest.Settings) }) { Icon(Ic.Tune, "Settings") }
            }
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                Cluster(Modifier.offset(60.dp, 30.dp), 120, DesignShapes.Clover, pal.avatar(3), Ic.People, 50)
                Cluster(Modifier.offset(196.dp, 70.dp), 100, DesignShapes.Arch, pal.avatar(0), Ic.Add, 42)
                Cluster(Modifier.offset(130.dp, 150.dp), 74, DesignShapes.Cookie, pal.avatar(0), Ic.Add, 1)
            }
            EmptyStateText("Your address book is empty", "Bring contacts in from an account, or add the first person yourself.")
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectRow(pal.tile(1), "Connect Google") { vm.push(Dest.Sources) }
                ConnectRow(pal.tile(2), "Connect iCloud") { vm.push(Dest.ICloud) }
                BigButton("Add someone manually", { vm.push(Dest.AddPerson) }, Modifier.fillMaxWidth(), icon = Ic.Add,
                    container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ConnectRow(color: Color, title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Ic.Cloud, null)
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        BigButton("Connect", onClick, container = Color(0xFF4A0E04), height = 44, hPad = 18)
    }
}

/** Full-width card for someone celebrating today: Send a wish, Remind, and the notify overflow menu. */
@Composable
fun TodayCard(vm: AppViewModel, e: UpcomingEvent, s: Settings, colorIdx: Int) {
    val pal = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    val meta = s.dateMeta["${e.person.id}|${e.date.label}"]
    val btn = if (isDark()) MaterialTheme.colorScheme.primary else if (colorIdx == 1) Color(0xFF3D2E00) else Color(0xFF4A0E04)
    val onBtn = if (isDark()) MaterialTheme.colorScheme.onPrimary else Color.White
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(pal.tile(colorIdx)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonAvatar(e.person, 112.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(e.date.title, icon = iconFor(e.date, meta), filled = e.date.kind == EventKind.Birthday)
                Text(e.person.name, fontSize = 26.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                Text(e.subtitle() + " · " + s.categoryFor(e.person.id).name, fontSize = 14.sp)
            }
            Box {
                IconButton({ menu = true }) { Icon(Ic.More, "More") }
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(e.person.firstName.uppercase() + "'S REMINDERS", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(ReminderStyle.Priority, ReminderStyle.Silent, ReminderStyle.Never).forEach { st ->
                        DropdownMenuItem({ Text(st.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }, { vm.setStyle(e.person.id, st); menu = false },
                            leadingIcon = { Icon(styleIcon(st, Ic.Notifications), null) })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Send a wish", { vm.wishFor = e }, Modifier.weight(1f), icon = Ic.Send, iconAtEnd = true, container = btn, content = onBtn, height = 48)
            BigButton("Remind", { vm.remindFor = listOf(e) }, tonal = true, height = 48)
        }
    }
}
