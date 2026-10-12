@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.birdayyy.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birdayyy.data.*
import app.birdayyy.reminders.ReminderScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val tokenRegex = Regex("\\{(name|age|nickname)\\}")

/** Highlights {name}-style placeholders like the design's white chips. */
private fun highlightTokens(text: String, bg: Color, weight: FontWeight = FontWeight.Bold): AnnotatedString = buildAnnotatedString {
    var last = 0
    tokenRegex.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(background = bg, fontWeight = weight)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

private class TokenTransformation(private val bg: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlightTokens(text.text, bg), OffsetMapping.Identity)
}

/** Template with its placeholders filled in and the filled-in values highlighted. */
private fun filledAnnotated(t: String, p: Person, turning: Int?, bg: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    tokenRegex.findAll(t).forEach { m ->
        append(t.substring(last, m.range.first))
        val v = when (m.groupValues[1]) { "age" -> turning?.toString() ?: ""; else -> p.firstName }
        withStyle(SpanStyle(background = bg, fontWeight = FontWeight.Bold)) { append(v) }
        last = m.range.last + 1
    }
    append(t.substring(last))
}

@Composable
fun WishSheet(vm: AppViewModel, e: UpcomingEvent, s: Settings) {
    val ctx = LocalContext.current
    val own = s.categoryFor(e.person.id)
    var category by remember { mutableStateOf(own) }
    val kindOk = { t: Template ->
        t.forKind == TemplateKind.Any || (t.forKind == TemplateKind.Birthday && e.date.kind == EventKind.Birthday) ||
            (t.forKind == TemplateKind.Anniversary && e.date.kind == EventKind.Anniversary)
    }
    val options = s.templates.filter { it.category == category && kindOk(it) }.sortedByDescending { it.isDefault }
    var selected by remember(category) { mutableStateOf(options.firstOrNull()?.id) }
    val dismiss = { vm.wishFor = null }
    val hl = if (isDark()) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .85f)
    BdSheet(dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(e.person, 48.dp)
                SheetHeader("Pick a template", "For ${e.person.firstName}'s ${e.date.title.lowercase()} · ${e.subtitle().lowercase()}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c -> PillChip(c.name, category == c, { category = c }, if (c == own) Ic.Star else null, ChipStyle.Choice) }
            }
            if (options.isEmpty()) Text("No templates for this category yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { t -> MessageOption(selected == t.id, filledAnnotated(t.text, e.person, e.turning, hl), { selected = t.id }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ dismiss(); vm.push(Dest.Templates) }) { Text("Manage templates", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("Opens in your messaging app,\nwhere you can edit it", fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            BigButton("Send via...", {
                val t = options.firstOrNull { it.id == selected } ?: return@BigButton
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, fillTemplate(t.text, e.person, e.turning))
                dismiss()
                ctx.startActivity(Intent.createChooser(send, "Send wish").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, Modifier.fillMaxWidth(), icon = Ic.Send, iconAtEnd = true, enabled = selected != null)
        }
    }
}

@Composable
fun RemindSheet(vm: AppViewModel, events: List<UpcomingEvent>) {
    val ctx = LocalContext.current
    val now = LocalDateTime.now()
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    class Opt(val key: String, val title: String, val sub: String, val at: LocalDateTime?, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    var custom by remember { mutableStateOf<LocalDateTime?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val s = vm.settings.value
    val opts = buildList {
        add(Opt("hour", "In 1 hour", now.plusHours(1).format(fmt), now.plusHours(1), Ic.Schedule))
        now.withHour(12).withMinute(0).takeIf { it.isAfter(now) }?.let { add(Opt("noon", "At noon", "12:00 PM today", it, Ic.Sun)) }
        now.withHour(18).withMinute(0).takeIf { it.isAfter(now) }?.let { add(Opt("eve", "This evening", "6:00 PM", it, Ic.Night)) }
        val tm = now.plusDays(1).withHour(s.hour).withMinute(s.minute)
        add(Opt("tom", "Tomorrow morning", "${tm.format(DateTimeFormatter.ofPattern("EEEE"))}, ${tm.format(fmt)}", tm, Ic.Calendar))
        add(Opt("pick", "Pick a time", custom?.format(DateTimeFormatter.ofPattern("EEE h:mm a")) ?: "", custom, Ic.Edit))
    }
    var chosen by remember { mutableStateOf(if (opts.any { it.key == "noon" }) "noon" else "hour") }
    val dismiss = { vm.remindFor = null }
    if (pickTime) {
        val st = rememberTimePickerState(now.hour, now.minute, false)
        AlertDialog({ pickTime = false }, confirmButton = {
            TextButton({
                var at = LocalDateTime.now().withHour(st.hour).withMinute(st.minute).withSecond(0).withNano(0)
                if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
                custom = at; chosen = "pick"; pickTime = false
            }) { Text("OK") }
        }, dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } }, text = { TimePicker(st) })
    }
    val pal = LocalPalette.current
    BdSheet(dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                events.take(2).forEachIndexed { i, e -> PersonAvatar(e.person, 44.dp, Modifier.offset(x = (-12 * i).dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), shape = CircleShape) }
                Spacer(Modifier.width(if (events.size > 1) 2.dp else 12.dp))
                SheetHeader("Remind me later", "About " + events.joinToString(" and ") { it.person.firstName })
            }
            opts.forEach { o ->
                OptionCard(chosen == o.key, o.title, o.sub.ifBlank { null }, o.icon, { if (o.key == "pick") pickTime = true else chosen = o.key }, badge = if (o.key == "noon") "Suggested" else null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(dismiss, Modifier.height(52.dp)) { Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                BigButton("Set reminder", {
                    opts.firstOrNull { it.key == chosen }?.let {
                        val at = it.at ?: return@let
                        ReminderScheduler.remindAt(ctx, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        vm.message = "We'll remind you ${it.title.lowercase()}"
                    }
                    dismiss()
                }, height = 52, enabled = opts.firstOrNull { it.key == chosen }?.at != null)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, s: Settings) {
    var timePick by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val debuggable = ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (timePick) {
        val st = rememberTimePickerState(s.hour, s.minute, false)
        AlertDialog({ timePick = false }, confirmButton = { TextButton({ vm.setReminderTime(st.hour, st.minute); timePick = false }) { Text("OK") } },
            dismissButton = { TextButton({ timePick = false }) { Text("Cancel") } }, text = { TimePicker(st) })
    }
    GlassPage(topBar = { BarTitle("Settings", onBack = { vm.pop() }) }) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = pad.calculateTopPadding() + 14.dp, start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Reminders")
            SettingsCard {
                SettingRow(Ic.Schedule, "Daily reminder", "Every day with a celebration", { timePick = true }, iconSize = 40,
                    trailing = { PillChip(java.time.LocalTime.of(s.hour, s.minute).format(DateTimeFormatter.ofPattern("h:mm a")), true, style = ChipStyle.Setting) })
                RowDivider()
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remind me about", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("Birthdays", s.birthdays, { vm.store.update { it.copy(birthdays = !it.birthdays) } }, style = ChipStyle.Setting)
                        PillChip("Anniversaries", s.anniversaries, { vm.store.update { it.copy(anniversaries = !it.anniversaries) } }, style = ChipStyle.Setting)
                        PillChip("Other dates", s.others, { vm.store.update { it.copy(others = !it.others) } }, style = ChipStyle.Setting)
                    }
                }
                RowDivider()
                Box {
                    SettingRow(Ic.Notifications, "Default style", "For people without a custom setting", { styleMenu = true }, iconSize = 40,
                        trailing = { PillChip(s.defaultStyle.title, false, style = ChipStyle.Setting) })
                    DropdownMenu(styleMenu, { styleMenu = false }) {
                        ReminderStyle.entries.forEach { st -> DropdownMenuItem({ Text(st.title) }, { vm.store.update { it.copy(defaultStyle = st) }; styleMenu = false }) }
                    }
                }
            }
            if (debuggable) SettingsCard(Modifier.padding(top = 6.dp)) {
                SettingRow(Ic.NotificationsActive, "Send a test reminder (debug build)", "Shows today's digest right now", {
                    Thread { app.birdayyy.reminders.DigestNotifier.post(ctx) }.start()
                }, iconSize = 40)
            }
            SectionLabel("Contact sources", Modifier.padding(top = 10.dp))
            SettingsCard {
                SettingRow(Ic.Cloud, "Phone, Google, iCloud", if (s.icloudEmail == null) "iCloud not connected" else "iCloud: ${s.icloudEmail}", { vm.push(Dest.Sources) }, iconSize = 40,
                    trailing = { Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
                RowDivider()
                SettingRow(Ic.Grid, "List mode", "Merge duplicates yourself",
                    { vm.store.update { it.copy(mode = if (it.mode == ListMode.Combined) ListMode.Separate else ListMode.Combined) } }, iconSize = 40,
                    trailing = { PillChip(s.mode.name, false, style = ChipStyle.Setting) })
            }
            SectionLabel("Appearance", Modifier.padding(top = 10.dp))
            SettingsCard {
                SettingRow(Ic.Sparkle, "Dynamic color", "Use your wallpaper's colors", iconSize = 40, trailing = {
                    Switch(s.dynamicColor, { v -> vm.store.update { it.copy(dynamicColor = v) } }, enabled = android.os.Build.VERSION.SDK_INT >= 31)
                })
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Birdayyy palette", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(DesignShapes.Cookie to 0, DesignShapes.Flower to 1, DesignShapes.Clover to 2, DesignShapes.Squircle to 3, CircleShape to 0).forEach { (sh, ci) ->
                            Box(Modifier.size(30.dp).clip(sh).background(pal.avatar(ci)))
                        }
                    }
                }
                RowDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Font", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FontChoice.entries.forEach { f ->
                            PillChip(f.label, s.font == f, { vm.store.update { it.copy(font = f) } }, style = ChipStyle.Setting, fontFamily = familyFor(f))
                        }
                    }
                }
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Theme", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { m -> PillChip(m.name, s.theme == m, { vm.store.update { it.copy(theme = m) } }, style = ChipStyle.Setting) }
                    }
                }
            }
            SettingsCard(Modifier.padding(top = 10.dp)) {
                SettingRow(Ic.Chat, "Message templates", "Friends, Family, Colleagues", { vm.push(Dest.Templates) }, iconSize = 40,
                    trailing = { Icon(Ic.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
        }
    }
}

@Composable
fun TemplatesScreen(vm: AppViewModel, s: Settings) {
    var cat by remember { mutableStateOf(Category.Friends) }
    val pal = LocalPalette.current
    val list = s.templates.filter { it.category == cat }
    val hl = if (isDark()) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .85f)
    val order = listOf(0, 1, 2, 1)
    GlassPage(
        topBar = {
            Column(Modifier.padding(bottom = 12.dp)) {
                BarTitle("Message templates", onBack = { vm.pop() })
                SegPills(Category.entries.map { it.name }, Category.entries.indexOf(cat), { cat = Category.entries[it] }, Modifier.padding(horizontal = 16.dp), solid = true)
            }
        },
        floating = {
            ExtendedFloatingActionButton({ vm.push(Dest.EditTemplate(null)) }, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
                shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Ic.Add, null); Spacer(Modifier.width(8.dp)); Text("New message", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 14.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (list.isEmpty()) item { Text("No templates yet. Tap New message to write one.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.tile(order[list.indexOf(t) % order.size])).clickable { vm.push(Dest.EditTemplate(t.id)) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(highlightTokens(t.text, hl), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (t.isDefault) Tag("Default for " + when (t.forKind) { TemplateKind.Birthday -> "birthdays"; TemplateKind.Anniversary -> "anniversaries"; else -> "all dates" }, icon = Ic.Star)
                        else Tag(if (t.forKind == TemplateKind.Any) "Any date" else t.forKind.name)
                        Spacer(Modifier.weight(1f))
                        Icon(Ic.Edit, "Edit", Modifier.padding(end = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EditTemplateScreen(vm: AppViewModel, id: String?, s: Settings) {
    val existing = s.templates.firstOrNull { it.id == id }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: Category.Friends) }
    var kind by remember { mutableStateOf(existing?.forKind ?: TemplateKind.Birthday) }
    var default by remember { mutableStateOf(existing?.isDefault ?: false) }
    val sample = Person("sample", "Maya Okafor", null, emptyList(), emptySet(), null, emptyList())
    val c = MaterialTheme.colorScheme
    GlassPage { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(Ic.Close, "Close", { vm.pop() })
                Text("Edit message", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                BigButton("Save", {
                    val t = Template(existing?.id ?: "t" + System.currentTimeMillis(), text.trim(), cat, kind, default)
                    vm.store.update { st ->
                        val cleared = st.templates.map { if (default && it.id != t.id && it.category == cat && it.forKind == kind) it.copy(isDefault = false) else it }
                        st.copy(templates = if (existing != null) cleared.map { if (it.id == t.id) t else it } else cleared + t)
                    }
                    vm.pop()
                }, enabled = text.isNotBlank(), height = 44)
            }
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    SectionLabel("Message", Modifier.padding(start = 0.dp, bottom = 6.dp))
                    BdField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = false, minHeight = 130, transformation = TokenTransformation(c.secondaryContainer))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("name", "age", "nickname").forEach { tok ->
                            Row(Modifier.height(34.dp).clip(RoundedCornerShape(12.dp)).background(c.secondaryContainer).clickable { text += "{$tok}" }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Ic.Add, null, Modifier.size(14.dp), tint = c.onSecondaryContainer)
                                Text(tok, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSecondaryContainer)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Category")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Category.entries.forEach { PillChip(it.name, cat == it, { cat = it }, style = ChipStyle.Category) } }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("For")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TemplateKind.entries.forEach { PillChip(if (it == TemplateKind.Any) "Any date" else it.name, kind == it, { kind = it }, style = ChipStyle.Category) } }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use as default", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Pre-selected for ${cat.name.lowercase()}' ${if (kind == TemplateKind.Anniversary) "anniversaries" else "birthdays"}".replace("friends'", "friends'"), fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                    Switch(default, { default = it })
                }
                Row(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x22602010), spotColor = Color(0x22602010)).clip(RoundedCornerShape(28.dp))
                    .background(if (isDark()) c.surfaceContainerHigh else Color.White.copy(alpha = .85f)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(sample, 48.dp, shape = DesignShapes.Cookie)
                    Column {
                        Text("PREVIEW FOR MAYA", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = c.onSurfaceVariant)
                        Text(fillTemplate(text, sample, 29), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                if (existing != null) TextButton({ vm.store.update { st -> st.copy(templates = st.templates.filter { it.id != existing.id }) }; vm.pop() },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.error)) {
                    Icon(Ic.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete message", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
