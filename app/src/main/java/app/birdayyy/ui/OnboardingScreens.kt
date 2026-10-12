@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.birdayyy.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birdayyy.data.*
import app.birdayyy.reminders.ReminderScheduler

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var why by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
        if (Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.canScheduleExact(ctx)) {
            runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
        }
        vm.push(Dest.Sources)
    }
    if (why) AlertDialog({ why = false }, confirmButton = { TextButton({ why = false }) { Text("Got it") } },
        title = { Text("Why these permissions?") },
        text = { Text("Contacts: to find birthdays and anniversaries, and to add people you create. Notifications: for the daily digest. Alarms and reminders: so it arrives at the exact time you choose. Contacts stay on your phone.") })
    val pal = LocalPalette.current
    GlassPage(
        overlay = { haze ->
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    .shadow(10.dp, RoundedCornerShape(36.dp), ambientColor = Color(0x33602010), spotColor = Color(0x33602010))
                    .glass(haze, RoundedCornerShape(36.dp))
                    .border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(36.dp))
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Birdayyy", Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 5.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Never miss a birthday again.", fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                Text("We collect birthdays, anniversaries and other dates from your contacts and nudge you at 7 AM.",
                    fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermRow(Ic.People, "Contacts", "Read names, photos and dates on this phone", required = true)
                    PermRow(Ic.Notifications, "Notifications", "Your daily digest of who is celebrating")
                    PermRow(Ic.Schedule, "Alarms and reminders", "So it arrives right at 7:00, not later")
                }
                BigButton("Allow and continue", {
                    val perms = buildList {
                        add(Manifest.permission.READ_CONTACTS); add(Manifest.permission.WRITE_CONTACTS); add(Manifest.permission.GET_ACCOUNTS)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    launcher.launch(perms.toTypedArray())
                }, Modifier.fillMaxWidth())
                TextButton({ why = true }, Modifier.fillMaxWidth()) { Text("Why do you need these?", fontWeight = FontWeight.Bold) }
            }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            fun place(x: Int, y: Int) = Modifier.offset(x.dp, y.dp)
            ShapeBadge(place(28, 84), 132, DesignShapes.Cookie, pal.avatar(1), Ic.Cake, 56)
            ShapeBadge(place(178, 52), 118, DesignShapes.Flower, pal.avatar(0), Ic.Gift, 50)
            ShapeBadge(place(262, 178), 104, DesignShapes.Clover, pal.avatar(2), Ic.Heart, 44)
            ShapeBadge(place(58, 236), 92, DesignShapes.Squircle, pal.avatar(3), Ic.Champagne, 40)
            ShapeBadge(place(166, 196), 76, DesignShapes.Sunny, pal.avatar(1), Ic.Star, 34)
        }
    }
}

@Composable
private fun ShapeBadge(modifier: Modifier, size: Int, shape: Shape, color: Color, icon: ImageVector, iconSize: Int) {
    Box(modifier.size(size.dp).clip(shape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = LocalPalette.current.onAvatar, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, sub: String, required: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (required) Text("Required", Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Back button on the left and a small glass chip on the right, as in the first-run screens. */
@Composable
private fun StepHeader(onBack: () -> Unit, chip: String?) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(Ic.ChevronLeft, "Back", onBack)
        Spacer(Modifier.weight(1f))
        if (chip != null) Text(chip, Modifier.clip(CircleShape).background(chipBg()).padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SourcesScreen(vm: AppViewModel, s: Settings, data: DataState) {
    val ctx = LocalContext.current
    val onboarding = !s.onboarded
    val googleAccounts = data.accounts.filter { it.type == "com.google" }
    val pal = LocalPalette.current
    GlassPage(
        bottomBar = {
            BigButton(if (onboarding) "Show my birthdays" else "Done", { if (onboarding) vm.finishOnboarding() else vm.pop() },
                Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 24.dp))
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            StepHeader({ vm.pop() }, if (onboarding) "Step 2 of 2" else null)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Where should we look for dates?", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(2.dp))
                SourceCard(pal.tile(1), DesignShapes.Cookie, Color.White.copy(alpha = .2f), Ic.Phone, "Phone contacts", "On this device. Always the default.") {
                    Switch(s.phoneOn, { v -> vm.store.update { it.copy(phoneOn = v) } })
                }
                SourceCard(pal.tile(1), DesignShapes.Flower, Color.White.copy(alpha = .2f), Ic.Cloud, "Google",
                    if (googleAccounts.isEmpty()) "No Google account on this phone yet" else googleAccounts.joinToString { it.name.orEmpty() }) {
                    if (googleAccounts.isEmpty()) BigButton("Add", {
                        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_ADD_ACCOUNT).putExtra(AndroidSettings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))) }
                    }, tonal = true, height = 40)
                    else Switch(s.googleOn, { v -> vm.store.update { it.copy(googleOn = v) } })
                }
                SourceCard(pal.tile(2), DesignShapes.Clover, pal.avatar(2), Ic.Cloud, "iCloud", s.icloudEmail ?: "Not connected") {
                    if (s.icloudEmail == null) BigButton("Connect", { vm.push(Dest.ICloud) }, tonal = true, height = 40)
                    else Switch(s.icloudOn, { v -> vm.store.update { it.copy(icloudOn = v) } })
                }
                if (s.icloudEmail != null) TextButton({ vm.disconnectICloud() }) { Text("Disconnect iCloud") }

                Text("How should people be listed?", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCard(Modifier.weight(1f), s.mode == ListMode.Combined, Ic.Merge, "Combined", "One list from every source") { vm.store.update { it.copy(mode = ListMode.Combined) } }
                    ModeCard(Modifier.weight(1f), s.mode == ListMode.Separate, Ic.Columns, "Separate", "A tab for each source") { vm.store.update { it.copy(mode = ListMode.Separate) } }
                }
                Text("Spot a duplicate? Merge two entries or move a contact to another source later, from their page.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceCard(color: Color, badgeShape: Shape, badgeColor: Color, icon: ImageVector, title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(badgeShape).background(badgeColor), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = LocalPalette.current.onAvatar)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ModeCard(modifier: Modifier, selected: Boolean, icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(if (selected) c.primaryContainer else c.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, c.primary, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (selected) c.onPrimaryContainer else c.onSurface)
            if (selected) Icon(Ic.Check, null, tint = c.onPrimaryContainer)
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (selected) c.onPrimaryContainer else c.onSurface)
        Text(sub, fontSize = 12.sp, color = if (selected) c.onPrimaryContainer else c.onSurface)
    }
}

@Composable
private fun FieldLabel(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun ICloudScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pal = LocalPalette.current
    val ink = Color(0xFF4A0E04)
    GlassPage(
        bottomBar = {
            BigButton(if (busy) "Connecting…" else "Connect iCloud", {
                busy = true; error = null
                vm.connectICloud(email, pw) { err -> busy = false; error = err; if (err == null) vm.pop() }
            }, Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 24.dp), enabled = !busy && email.contains("@") && pw.length >= 8)
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            StepHeader({ vm.pop() }, "iCloud · CardDAV")
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connect iCloud", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                Text("Apple has no sign-in for Android, so you make a one-time app-specific password. About a minute.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StepCard(pal.tile(2), DesignShapes.Cookie, pal.avatar(2), 1, "Open your Apple ID page", "Sign-In and Security, then App-Specific Passwords.") {
                    BigButton("appleid.apple.com", { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://account.apple.com/account/manage"))) } },
                        icon = null, container = ink, height = 40)
                }
                StepCard(pal.tile(1), DesignShapes.Flower, pal.avatar(1), 2, "Generate one named Birdayyy", "Copy the code. It looks like abcd-efgh-ijkl-mnop.") {}
                StepCard(pal.tile(0), DesignShapes.Clover, pal.avatar(0), 3, "Paste it here", null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { FieldLabel("Apple ID email"); BdField(email, { email = it }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { FieldLabel("App-specific password"); BdField(pw, { pw = it }, Modifier.fillMaxWidth(), password = true) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Ic.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text("Read and write access to contacts, so you can add birthdays from the app. Stored encrypted on this phone, never on our servers.",
                        fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StepCard(color: Color, badgeShape: Shape, badgeColor: Color, n: Int, title: String, sub: String?, extra: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).clip(badgeShape).background(badgeColor), contentAlignment = Alignment.Center) {
            Text("$n", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = LocalPalette.current.onAvatar)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
        }
    }
}

/** Shown when Contacts permission is blocked. */
@Composable
fun PermissionScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val c = MaterialTheme.colorScheme
    val warn = if (isDark()) c.secondaryContainer else Color(0xFFFFE9B0)
    val warnInk = if (isDark()) c.onSecondaryContainer else Color(0xFF3D2E00)
    GlassPage(
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton({ vm.push(Dest.AddPerson) }) {
                    Icon(Ic.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Continue and add people by hand", fontWeight = FontWeight.Bold)
                }
                Text("You can connect Google or iCloud later in Settings.", fontSize = 12.sp, color = c.onSurfaceVariant)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Ic.Lock, Modifier.size(68.dp), size = 68, radius = 24)
            Text("Birdayyy needs a couple of permissions", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
            Text("Without these, there is nothing to show and nothing to remind you about.", fontSize = 15.sp, color = c.onSurfaceVariant)
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.errorContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
                        Icon(Ic.People, null, tint = Color(0xFFBA1A1A))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Contacts are blocked", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onErrorContainer)
                        Text("Needed to find birthdays and anniversaries.", fontSize = 13.sp, color = c.onErrorContainer)
                    }
                }
                StepLine(1, "Tap Open settings, then Permissions", c.onErrorContainer)
                StepLine(2, "Choose Contacts and select Allow", c.onErrorContainer)
                BigButton("Open app settings", { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) },
                    container = Color(0xFFBA1A1A), height = 48)
            }
            if (Build.VERSION.SDK_INT >= 33) WarnCard(warn, warnInk, Ic.NotificationsOff, "Notifications are off", "You would miss the 7 AM digest.", "Turn on") { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
            if (Build.VERSION.SDK_INT >= 31) WarnCard(warn, warnInk, Ic.Schedule, "Exact alarms are off", "Reminders may arrive late.", "Allow") {
                runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
            }
        }
    }
}

@Composable
private fun StepLine(n: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
            Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF410002))
        }
        Text(text, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun WarnCard(bg: Color, ink: Color, icon: ImageVector, title: String, sub: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .65f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = ink) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ink)
            Text(sub, fontSize = 13.sp, color = ink)
        }
        BigButton(action, onClick, container = Color(0xFF3D2E00), height = 40, hPad = 16)
    }
}

@Composable
fun EmptyStateText(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center)
        Text(body, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
