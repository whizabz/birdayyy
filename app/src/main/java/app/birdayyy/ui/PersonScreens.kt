@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.birdayyy.ui

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birdayyy.data.*
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

private enum class PersonSheet { Reminders, AddDate, Source }

private fun monthName(m: Int) = Month.of(m).getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun relativeLong(e: UpcomingEvent): String = when {
    e.daysUntil == 0 -> "Today"
    e.daysUntil == 1 -> "Tomorrow"
    e.daysUntil <= 60 -> "in ${e.daysUntil} days"
    else -> "in ${e.daysUntil / 30} months"
}

@Composable
private fun InfoChip(text: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.clip(CircleShape).background(chipBg()).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        trailing?.invoke()
    }
}

@Composable
fun PersonScreen(vm: AppViewModel, id: String, persons: List<Person>, events: List<UpcomingEvent>, s: Settings, data: DataState) {
    val person = persons.firstOrNull { it.id == id }
    if (person == null) { LaunchedEffect(Unit) { vm.pop() }; return }
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf<PersonSheet?>(when (vm.debugSheet) { "reminder" -> PersonSheet.Reminders; "adddate" -> PersonSheet.AddDate; "source" -> PersonSheet.Source; else -> null }) }
    val pal = LocalPalette.current
    val mine = events.filter { it.person.id == id }
    var catMenu by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val time = java.time.LocalTime.of(s.hour, s.minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

    GlassPage(
        bottomBar = {
            BigButton("Send a wish to ${person.firstName}", { (mine.minByOrNull { it.daysUntil })?.let { vm.wishFor = it } },
                Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 24.dp), icon = Ic.Send, iconAtEnd = true)
        },
        overlay = {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                GlassCircleButton(Ic.ChevronLeft, "Back", { vm.pop() })
                if (person.contactId != null) GlassCircleButton(Ic.Edit, "Edit contact", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person.contactId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) else Spacer(Modifier.size(48.dp))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PersonAvatar(person, 184.dp)
            Text(person.name, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    InfoChip(s.categoryFor(id).name, { catMenu = true })
                    DropdownMenu(catMenu, { catMenu = false }) {
                        Category.entries.forEach { cat -> DropdownMenuItem({ Text(cat.name) }, { vm.setCategory(id, cat); catMenu = false }) }
                    }
                }
                InfoChip(person.sources.joinToString(" + ") { it.label }, { sheet = PersonSheet.Source })
            }
            SettingsCard {
                person.dates.forEach { d ->
                    val ev = mine.firstOrNull { it.date.key == d.key }
                    val meta = s.dateMeta["${person.id}|${d.label}"]
                    val (tileBg, tileFg) = when {
                        d.kind == EventKind.Birthday -> c.primary to c.onPrimary
                        d.kind == EventKind.Anniversary -> c.secondaryContainer to c.onSecondaryContainer
                        meta != null -> pal.avatar(meta.color).copy(alpha = .55f) to c.onSurface
                        else -> c.surfaceContainerHigh to c.onSurface
                    }
                    SettingRow(null, d.title, listOfNotNull("${d.day} ${monthName(d.month)}",
                        ev?.turning?.let { if (d.kind == EventKind.Birthday) "turns $it" else "$it years" }).joinToString(" · "),
                        iconContent = { IconTile(iconFor(d, meta), container = tileBg, content = tileFg) },
                        trailing = {
                            when {
                                ev != null && ev.daysUntil == 0 -> Tag("Today", filled = true)
                                d.kind == EventKind.Other -> Tag("Custom")
                                ev != null -> Text(relativeLong(ev), fontSize = 13.sp, color = c.onSurfaceVariant)
                            }
                        })
                    RowDivider()
                }
                Row(Modifier.fillMaxWidth().clickable { sheet = PersonSheet.AddDate }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val line = c.primary
                    Box(Modifier.size(44.dp).drawBehind {
                        drawRoundRect(line, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                    }, contentAlignment = Alignment.Center) { Icon(Ic.Add, null, tint = line) }
                    Text("Add a date", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = line)
                }
            }
            SettingsCard {
                SettingRow(Ic.Notifications, "Reminders", "${s.styleFor(id).title} · $time", { sheet = PersonSheet.Reminders },
                    trailing = { Icon(Ic.ChevronRight, null, tint = c.onSurfaceVariant) })
            }
            SettingsCard {
                SettingRow(Ic.Merge, "Sources", (if (person.sources.size > 1) "Merged from " else "From ") + person.sources.joinToString(" and ") { it.label }, { sheet = PersonSheet.Source },
                    trailing = { Text("Move or merge", Modifier.clip(CircleShape).background(c.primaryContainer).padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer) })
            }
        }
    }
    when (sheet) {
        PersonSheet.Reminders -> ReminderSheet(person, s, vm, time) { sheet = null }
        PersonSheet.AddDate -> AddDateSheet(person, s, vm, time) { sheet = null }
        PersonSheet.Source -> SourceSheet(person, persons, data, s, vm) { sheet = null }
        null -> {}
    }
}

@Composable
private fun SheetButtons(cancel: String, action: String, onCancel: () -> Unit, onAction: () -> Unit, enabled: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onCancel, Modifier.height(52.dp)) { Text(cancel, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        BigButton(action, onAction, icon = icon, enabled = enabled, height = 52)
    }
}

@Composable
private fun ReminderSheet(person: Person, s: Settings, vm: AppViewModel, time: String, onDismiss: () -> Unit) {
    var scope by remember { mutableStateOf(0) }
    val scopeKind = listOf(null, EventKind.Birthday, EventKind.Anniversary)
    var choice by remember { mutableStateOf(s.styleFor(person.id)) }
    BdSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetHeader("Reminders for ${person.firstName}", "Choose how Birdayyy treats their dates at $time.")
            ReminderStyle.entries.forEach { st ->
                OptionCard(choice == st, st.title, when (st) {
                    ReminderStyle.Priority -> "Pops up on screen, even in Do Not Disturb exceptions"
                    else -> st.hint
                }, when (st) {
                    ReminderStyle.Normal, ReminderStyle.Priority -> Ic.Notifications
                    ReminderStyle.Silent -> Ic.VolumeOff
                    ReminderStyle.Never -> Ic.NotificationsOff
                }, { choice = st })
            }
            Text("Applies to", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            SegPills(listOf("All dates", "Birthday only", "Anniversary only"), scope, { scope = it; choice = s.styleFor(person.id, scopeKind[it]) })
            SheetButtons("Cancel", "Save", onDismiss, { vm.setStyle(person.id, choice, scopeKind[scope]); onDismiss() })
        }
    }
}


private fun dateLabel(month: Int?, day: Int, year: Int?, noYear: Boolean, short: Boolean = false): String? =
    month?.let {
        val m = monthName(it).let { n -> if (short) n.take(3) else n }
        "$day $m" + if (!noYear && year != null) " $year" else ""
    }

@Composable
private fun FieldLabel(text: String) = Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun AddDateSheet(person: Person, s: Settings, vm: AppViewModel, time: String, onDismiss: () -> Unit) {
    val pal = LocalPalette.current
    val c = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var month by remember { mutableStateOf<Int?>(null) }
    var day by remember { mutableStateOf(1) }
    var year by remember { mutableStateOf<Int?>(null) }
    var icon by remember { mutableStateOf(2) }
    var color by remember { mutableStateOf(4) }
    var picking by remember { mutableStateOf(false) }
    if (picking) WheelDatePicker(month, day, year, { m, d, y -> month = m; day = d; year = y }, { picking = false })
    val dotColors = listOf(0, 1, 2, 3, 1, 0)
    BdSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add a date", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { FieldLabel("Name"); BdField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Biscuit's gotcha day") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FieldLabel("Date")
                BdField(dateLabel(month, day, year, false) ?: "Pick a date", {}, Modifier.fillMaxWidth().padding(top = 4.dp), leading = Ic.Calendar, onClick = { picking = true })
            }
            FieldLabel("Icon")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dateIcons.forEachIndexed { i, ic ->
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainer)
                        .then(if (i == icon) Modifier.border(2.dp, c.primary, RoundedCornerShape(16.dp)) else Modifier).clickable { icon = i }, contentAlignment = Alignment.Center) { Icon(ic, null) }
                }
            }
            FieldLabel("Tile color")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(start = 3.dp, top = 2.dp)) {
                dotColors.forEachIndexed { i, ci ->
                    Box(Modifier.size(32.dp).then(if (i == color) Modifier.border(3.dp, c.primary, CircleShape).padding(3.dp) else Modifier).clip(CircleShape).background(pal.avatar(ci)).clickable { color = i })
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat every year", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Remind at $time · ${s.defaultStyle.title}", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                Icon(Ic.Check, null, tint = c.primary)
            }
            SheetButtons("Cancel", "Save date", onDismiss, {
                val m = month ?: return@SheetButtons
                vm.addDate(person, DateEntry(EventKind.Other, name.trim(), m, day, year?.takeIf { it > 1700 }), DateMeta(icon, dotColors[color]))
                onDismiss()
            }, enabled = month != null && name.isNotBlank())
        }
    }
}

@Composable
private fun SourceSheet(person: Person, persons: List<Person>, data: DataState, s: Settings, vm: AppViewModel, onDismiss: () -> Unit) {
    val pal = LocalPalette.current
    val c = MaterialTheme.colorScheme
    val googleAcct = data.accounts.firstOrNull { it.type == "com.google" }
    val icloudOn = s.icloudEmail != null
    val lookup = person.id.substringAfterLast(':')
    val isSplit = lookup in s.splits
    var target by remember { mutableStateOf<SourceType?>(null) }
    var move by remember { mutableStateOf(false) }
    var linkPick by remember { mutableStateOf(false) }
    val linkedChildren = s.links.filterValues { it == person.id }.keys
    val dateSummary = person.dates.joinToString(" · ") { d -> d.title + " " + d.day + " " + monthName(d.month).take(3) }
    BdSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetHeader("${person.firstName} is in ${person.sources.size} source" + if (person.sources.size == 1) "" else "s",
                "Merge them, keep them apart, or move them to another source.")
            person.sources.forEachIndexed { i, src ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.tile(1)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if (src == SourceType.Phone) pal.avatar(1) else pal.avatar(0)), contentAlignment = Alignment.Center) {
                        Icon(if (src == SourceType.Phone) Ic.Phone else Ic.Cloud, null, Modifier.size(20.dp), tint = pal.onAvatar)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(src.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(dateSummary, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1)
                    }
                    if (i == 0 && person.sources.size > 1) Text("PRIMARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = c.onSurfaceVariant)
                }
            }
            FieldLabel("Show as")
            SegPills(listOf("One tile", "Separate tiles"), if (isSplit) 1 else 0, { vm.setSplit(person.id, it == 1) })
            if (linkedChildren.isNotEmpty()) linkedChildren.forEach { ch ->
                val other = persons.firstOrNull { it.id == ch }
                SettingsCard { SettingRow(Ic.Merge, "Linked entry", other?.name ?: ch, trailing = { TextButton({ vm.unlink(ch) }) { Text("Unlink") } }) }
            }
            TextButton({ linkPick = !linkPick }) { Icon(Ic.Merge, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Merge with another entry", fontWeight = FontWeight.Bold) }
            if (linkPick) Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                persons.filter { it.id != person.id }.forEach { other ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceContainer).clickable { vm.link(other.id, person.id); linkPick = false }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(other, 36.dp); Text(other.name, Modifier.weight(1f)); Text(other.sources.joinToString { it.label }, fontSize = 12.sp)
                    }
                }
            }
            FieldLabel("Move or copy to")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DestCard("Phone", pal.avatar(1), RoundedCornerShape(12.dp), Ic.Phone, target == SourceType.Phone, Modifier.weight(1f)) { target = SourceType.Phone }
                DestCard("Google", pal.avatar(0), CircleShape, Ic.Cloud, target == SourceType.Google, Modifier.weight(1f)) { if (googleAcct != null) target = SourceType.Google }
                DestCard("iCloud", pal.avatar(2), RoundedCornerShape(12.dp), Ic.Cloud, target == SourceType.ICloud, Modifier.weight(1f)) { if (icloudOn) target = SourceType.ICloud else { onDismiss(); vm.push(Dest.ICloud) } }
            }
            SegPills(listOf("Copy", "Move"), if (move) 1 else 0, { move = it == 1 })
            Text(if (move) "Move adds ${person.firstName} and their dates to ${target?.label ?: "the new source"}, then removes the old entry."
            else "Copy keeps the original and adds a second entry in ${target?.label ?: "the new source"}.", fontSize = 13.sp, lineHeight = 18.sp, color = c.onSurfaceVariant)
            SheetButtons("Cancel", "Apply", onDismiss, {
                val t = target ?: return@SheetButtons
                vm.moveOrCopy(person, when (t) { SourceType.Phone -> AccountRef(null, null); SourceType.Google -> googleAcct; else -> null }, t == SourceType.ICloud, move)
                onDismiss()
            }, enabled = target != null && person.rawIds.isNotEmpty(), icon = Ic.Swap)
        }
    }
}

private data class DateRow(val kind: EventKind, val month: Int?, val day: Int, val year: Int?)

@Composable
fun AddPersonScreen(vm: AppViewModel, data: DataState, persons: List<Person>) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    var existing by remember { mutableStateOf(vm.addForContact) }
    var name by rememberSaveable { mutableStateOf(vm.addForContact?.second ?: "") }
    DisposableEffect(Unit) { onDispose { vm.addForContact = null } }
    var rows by remember { mutableStateOf(listOf(DateRow(EventKind.Birthday, null, 1, null))) }
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf(Category.Friends) }
    var target by remember { mutableStateOf<SourceType?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val googleAcct = data.accounts.firstOrNull { it.type == "com.google" }
    val icloudOn = s.icloudEmail != null
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) { existing = it.getLong(0) to it.getString(1); name = it.getString(1) }
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }!!
            val side = minOf(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(sq, 512.coerceAtMost(side), 512.coerceAtMost(side), true)
            val out = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            photoBitmap = scaled; photoBytes = out.toByteArray()
        }
    }
    pickingFor?.let { idx -> val cur = rows[idx]; WheelDatePicker(cur.month, cur.day, cur.year, { m, d, y -> rows = rows.mapIndexed { i, r -> if (i == idx) r.copy(month = m, day = d, year = y) else r } }, { pickingFor = null }) }
    val isNew = existing == null
    val initials = name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    GlassPage(
        bottomBar = {
            BigButton(if (isNew) "Save person" else "Save date", {
                val dates = rows.mapNotNull { r -> r.month?.let { DateEntry(r.kind, "", it, r.day, r.year?.takeIf { y -> y > 1700 }) } }
                if (existing != null) {
                    val p = persons.firstOrNull { it.contactId == existing!!.first } ?: Person("c:?", name, null, emptyList(), emptySet(), existing!!.first, emptyList())
                    dates.forEach { vm.addDate(p, it, null) }
                } else {
                    val t = target ?: SourceType.Phone
                    vm.addPerson(name.trim(), dates, when (t) { SourceType.Google -> googleAcct; else -> AccountRef(null, null) }, t == SourceType.ICloud, photoBytes, category)
                }
                vm.pop()
            }, Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 24.dp), enabled = rows.any { it.month != null } && name.isNotBlank())
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(Ic.Close, "Close", { vm.pop() })
                Text(if (isNew) "New person" else "Add a date", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp)) {
                        Box(Modifier.size(96.dp).clip(DesignShapes.Flower).background(pal.avatar(2)), contentAlignment = Alignment.Center) {
                            val b = photoBitmap
                            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(initials, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = pal.onAvatar)
                        }
                        if (isNew) Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.primary)
                            .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                            Icon(Ic.Camera, "Add photo", Modifier.size(20.dp), tint = c.onPrimary)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Name")
                        BdField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Full name", enabled = isNew)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Dates")
                    rows.forEachIndexed { i, r ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BdField(r.kind.name, {}, Modifier.weight(1f), leading = if (r.kind == EventKind.Anniversary) Ic.Champagne else Ic.Cake,
                                    onClick = { rows = rows.mapIndexed { j, x -> if (j == i) x.copy(kind = if (x.kind == EventKind.Birthday) EventKind.Anniversary else EventKind.Birthday) else x } })
                                BdField(dateLabel(r.month, r.day, r.year, false, short = true) ?: "Pick a date", {}, Modifier.weight(1.2f), leading = Ic.Calendar, onClick = { pickingFor = i })
                            }
                        }
                    }
                    Row(Modifier.clickable { rows = rows + DateRow(EventKind.Anniversary, null, 1, null) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Ic.Add, null, Modifier.size(20.dp), tint = c.primary)
                        Text("Add another date", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary)
                    }
                }
                if (isNew) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Category")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Category.entries.forEach { PillChip(it.name, category == it, { category = it }, style = ChipStyle.Category) } }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Save to")
                        val sel = target ?: SourceType.Phone
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DestCard("Phone", pal.avatar(1), RoundedCornerShape(12.dp), Ic.Phone, sel == SourceType.Phone, Modifier.weight(1f)) { target = SourceType.Phone }
                            DestCard("Google", pal.avatar(0), CircleShape, Ic.Cloud, sel == SourceType.Google, Modifier.weight(1f)) { if (googleAcct != null) target = SourceType.Google }
                            DestCard("iCloud", pal.avatar(2), RoundedCornerShape(12.dp), Ic.Cloud, sel == SourceType.ICloud, Modifier.weight(1f)) { if (icloudOn) target = SourceType.ICloud else vm.push(Dest.ICloud) }
                        }
                        Text(when (sel) {
                            SourceType.Google -> "Creates a contact in ${googleAcct?.name ?: "your Google account"}. It also appears in your Contacts app and on your other devices."
                            SourceType.ICloud -> "Creates a contact in ${s.icloudEmail}. It will sync to your Apple devices."
                            else -> "Creates a contact on this phone. It also appears in your Contacts app."
                        }, fontSize = 12.sp, lineHeight = 17.sp, color = c.onSurfaceVariant)
                    }
                    TextButton({ pickContact.launch(null) }) { Text("Add a date to an existing contact instead", fontWeight = FontWeight.Bold) }
                } else TextButton({ existing = null; name = "" }) { Text("Create a new person instead", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
