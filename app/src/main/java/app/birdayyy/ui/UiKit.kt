@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.birdayyy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun isDark() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Translucent chip/segment background, like the design's rgba(255,255,255,.55). */
@Composable
fun chipBg(): Color = if (isDark()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.6f)

@Composable
fun fieldBg(): Color = if (isDark()) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFFFFBF8)

/** Page with confetti background and frosted-glass top/bottom bars that blur whatever scrolls beneath. */
@Composable
fun GlassPage(
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floating: (@Composable BoxScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.(HazeState) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val haze = rememberHazeState()
    val density = LocalDensity.current
    var top by remember { mutableStateOf(0.dp) }
    var bottom by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Confetti()
            content(PaddingValues(top = top, bottom = bottom))
        }
        if (topBar != null) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().glass(haze).onSizeChanged { top = with(density) { it.height.toDp() } }) {
                Column(Modifier.statusBarsPadding()) { topBar() }
            }
        } else top = 0.dp
        if (bottomBar != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().glass(haze).onSizeChanged { bottom = with(density) { it.height.toDp() } }) {
                Column(Modifier.navigationBarsPadding()) { bottomBar() }
            }
        } else bottom = 0.dp
        floating?.invoke(this)
        overlay?.invoke(this, haze)
    }
    }
}

@Composable
fun GlassCircleButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).shadow(6.dp, CircleShape, ambientColor = Color(0x33602010), spotColor = Color(0x33602010))
            .clip(CircleShape).background(chipBg()).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description) }
}

@Composable
fun BarTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Ic.ChevronLeft, "Back") }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = modifier.padding(horizontal = 4.dp))

@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Int = 44, radius: Int = 16,
             container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.size(size.dp).clip(RoundedCornerShape(radius.dp)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content)
    }
}

/** Design `.card`: rounded 28, tonal surface, rows separated by hairlines. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 6.dp), content = content)
}

@Composable
fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun SettingRow(
    icon: ImageVector?, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, iconSize: Int = 44,
    iconContent: (@Composable () -> Unit)? = null, trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (iconContent != null) iconContent() else if (icon != null) IconTile(icon, size = iconSize, radius = if (iconSize < 44) 14 else 16)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

enum class ChipStyle { Filter, Category, Setting, Choice }

@Composable
fun PillChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null, icon: ImageVector? = null, style: ChipStyle = ChipStyle.Filter) {
    val c = MaterialTheme.colorScheme
    val (bg, fg, border) = when (style) {
        ChipStyle.Filter -> Triple(if (selected) c.primary else chipBg(), if (selected) c.onPrimary else c.onSurface, null)
        ChipStyle.Category -> Triple(if (selected) c.primaryContainer else c.surfaceContainer, if (selected) c.onPrimaryContainer else c.onSurfaceVariant,
            if (selected) BorderStroke(1.5.dp, c.primary) else null)
        ChipStyle.Setting -> Triple(if (selected) c.primaryContainer else chipBg(), if (selected) c.onPrimaryContainer else c.onSurface,
            if (selected) BorderStroke(1.5.dp, c.primary) else null)
        ChipStyle.Choice -> Triple(if (selected) c.primary else c.surfaceContainer, if (selected) c.onPrimary else c.onSurfaceVariant, null)
    }
    val h = when (style) { ChipStyle.Filter -> 34.dp; ChipStyle.Category -> 36.dp; ChipStyle.Setting -> 30.dp; ChipStyle.Choice -> 36.dp }
    Row(
        Modifier.height(h).clip(CircleShape).background(bg).then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Text(text, fontSize = if (style == ChipStyle.Category || style == ChipStyle.Choice) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Small translucent tag, e.g. "Birthday" or "in 2 days". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(if (filled) c.primary else c.surface.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = if (filled) c.onPrimary else c.onSurface)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (filled) c.onPrimary else c.onSurface, maxLines = 1)
    }
}

@Composable
fun SegPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, solid: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(CircleShape).background(if (solid) chipBg() else c.surfaceContainer).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (on) (if (solid) c.primary else c.primaryContainer) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(o, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    color = if (on) (if (solid) c.onPrimary else c.onPrimaryContainer) else c.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tonal: Boolean = false, enabled: Boolean = true,
              container: Color? = null, content: Color? = null, height: Int = 56, iconAtEnd: Boolean = false, hPad: Int = 24) {
    val c = MaterialTheme.colorScheme
    val colors = when {
        container != null -> ButtonDefaults.buttonColors(containerColor = container, contentColor = content ?: c.onPrimary)
        tonal -> ButtonDefaults.buttonColors(containerColor = chipBg(), contentColor = c.onSurface)
        else -> ButtonDefaults.buttonColors()
    }
    Button(onClick, modifier.heightIn(min = height.dp), enabled = enabled, colors = colors, contentPadding = PaddingValues(horizontal = hPad.dp)) {
        if (icon != null && !iconAtEnd) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (icon != null && iconAtEnd) { Spacer(Modifier.width(8.dp)); Icon(icon, null, Modifier.size(20.dp)) }
    }
}

@Composable
fun SheetHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 2.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
        if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Radio(selected: Boolean, size: Int = 22) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(size.dp).border(2.dp, if (selected) c.primary else c.onSurfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size((size / 2 - 1).dp).clip(CircleShape).background(c.primary))
    }
}

/** Selectable option row used in sheets (radio on the right, icon tile on the left). */
@Composable
fun OptionCard(selected: Boolean, title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit, badge: String? = null) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (selected) c.primaryContainer else c.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, c.primary, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconTile(icon, container = if (selected) c.primary else c.surfaceContainerHigh, content = if (selected) c.onPrimary else c.onSurface)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (badge != null) Text(badge, Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 10.dp, vertical = 2.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A0E04))
            }
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.onSurfaceVariant)
        }
        Radio(selected)
    }
}

/** Template/message option: radio on the left, regular text. */
@Composable
fun MessageOption(selected: Boolean, text: androidx.compose.ui.text.AnnotatedString, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (selected) c.primaryContainer else c.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, c.primary, RoundedCornerShape(22.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 1.dp)) { Radio(selected, 20) }
        Text(text, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Design `.area` text field: rounded, near-white, with a coral line along the bottom edge. */
@Composable
fun BdField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false, enabled: Boolean = true, leading: ImageVector? = null,
    minHeight: Int = 52, transformation: VisualTransformation = VisualTransformation.None, onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, focusRequester: androidx.compose.ui.focus.FocusRequester? = null, outlined: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (outlined) 28.dp else 22.dp)
    Box(
        modifier.clip(shape).then(if (outlined) Modifier.background(fieldBg()).border(1.5.dp, c.outline, shape) else Modifier.background(c.primary))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.then(if (outlined) Modifier else Modifier.padding(bottom = 2.dp).clip(shape).background(fieldBg())).heightIn(min = minHeight.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leading != null) Icon(leading, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, fontSize = 16.sp, color = c.onSurfaceVariant.copy(alpha = 0.7f))
                BasicTextField(
                    value, onValueChange, Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    enabled = enabled && onClick == null, singleLine = singleLine,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (isDark()) c.onSurface else Color(0xFF2A1A18)),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (password) PasswordVisualTransformation() else transformation,
                )
            }
            trailing?.invoke()
        }
    }
}

/** Destination option (Phone / Google / iCloud) used by Add person and Move or copy. */
@Composable
fun DestCard(label: String, badgeColor: Color, badgeShape: Shape, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) c.primaryContainer else c.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, c.primary, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(36.dp).clip(badgeShape).background(badgeColor), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = LocalPalette.current.onAvatar)
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.onPrimaryContainer else c.onSurface)
    }
}

/** Bottom sheet that opens fully expanded and looks like the design's frosted sheet. */
@Composable
fun BdSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    ModalBottomSheet(
        onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, content = content,
    )
