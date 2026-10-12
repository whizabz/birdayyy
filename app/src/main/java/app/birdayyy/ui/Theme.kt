@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.birdayyy.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import app.birdayyy.R
import androidx.compose.ui.platform.LocalContext
import app.birdayyy.data.ThemeMode

@Immutable
class Palette(val tiles: List<Color>, val avatars: List<Color>, val confetti: List<Color>, val onAvatar: Color) {
    fun tile(i: Int) = tiles[Math.floorMod(i, tiles.size)]
    fun avatar(i: Int) = avatars[Math.floorMod(i, avatars.size)]
}

val LocalPalette = staticCompositionLocalOf { lightPalette }

private val lightPalette = Palette(
    tiles = listOf(Color(0xFFFFDAD2), Color(0xFFFFE08A), Color(0xFFEBD0F5), Color(0xFFBFEBDD), Color(0xFFF5E3D8)),
    avatars = listOf(Color(0xFFF2886F), Color(0xFFF4B93D), Color(0xFFB98AD6), Color(0xFF6FC3A5)),
    confetti = listOf(Color(0xFFF2886F), Color(0xFFF4B93D), Color(0xFFB98AD6), Color(0xFF6FC3A5)),
    onAvatar = Color(0xFF2A1A18),
)
private val darkPalette = Palette(
    tiles = listOf(Color(0xFF5C2A1F), Color(0xFF554312), Color(0xFF4A2D5C), Color(0xFF1E4B3F), Color(0xFF3A2A26)),
    avatars = lightPalette.avatars,
    confetti = lightPalette.confetti,
    onAvatar = Color(0xFF2A1A18),
)

private val Light = lightColorScheme(
    primary = Color(0xFFBF3A24), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2), onPrimaryContainer = Color(0xFF4A0E04),
    secondary = Color(0xFF7A5C00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A), onSecondaryContainer = Color(0xFF3D2E00),
    tertiary = Color(0xFF7A4A8A), tertiaryContainer = Color(0xFFEBD0F5), onTertiaryContainer = Color(0xFF33113F),
    background = Color(0xFFFFF8F3), onBackground = Color(0xFF2A1A18),
    surface = Color(0xFFFFF8F3), onSurface = Color(0xFF2A1A18), onSurfaceVariant = Color(0xFF6B524C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1E8), surfaceContainer = Color(0xFFFBEBE1),
    surfaceContainerHigh = Color(0xFFF3DCCF), surfaceContainerHighest = Color(0xFFEBD2C4),
    outline = Color(0xFF8D6F68), outlineVariant = Color(0x33000000),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A5), onPrimary = Color(0xFF561E12),
    primaryContainer = Color(0xFF7A2A1A), onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFF4B93D), onSecondary = Color(0xFF3D2E00),
    secondaryContainer = Color(0xFF554312), onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFFE2B6F0), tertiaryContainer = Color(0xFF4A2D5C), onTertiaryContainer = Color(0xFFEBD0F5),
    background = Color(0xFF1B1210), onBackground = Color(0xFFFFE9E3),
    surface = Color(0xFF1B1210), onSurface = Color(0xFFFFE9E3), onSurfaceVariant = Color(0xFFD0B3AB),
    surfaceContainerLowest = Color(0xFF140C0A), surfaceContainerLow = Color(0xFF231815), surfaceContainer = Color(0xFF281A17),
    surfaceContainerHigh = Color(0xFF33231F), surfaceContainerHighest = Color(0xFF3E2D29),
    outline = Color(0xFFA08780), outlineVariant = Color(0x33FFFFFF),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

val RobotoFlex = FontFamily((100..1000 step 100).map { w ->
    Font(R.font.roboto_flex, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

private fun typography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), displayMedium = t.displayMedium.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        displaySmall = t.displaySmall.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), headlineLarge = t.headlineLarge.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        headlineMedium = t.headlineMedium.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), headlineSmall = t.headlineSmall.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        titleLarge = t.titleLarge.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), titleMedium = t.titleMedium.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        titleSmall = t.titleSmall.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), bodyLarge = t.bodyLarge.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        bodyMedium = t.bodyMedium.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), bodySmall = t.bodySmall.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        labelLarge = t.labelLarge.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), labelMedium = t.labelMedium.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
        labelSmall = t.labelSmall.copy(fontFamily = RobotoFlex, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
    )
}

@Composable
fun BirdayyyTheme(theme: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val ctx = LocalContext.current
    val useDynamic = dynamic && Build.VERSION.SDK_INT >= 31
    val scheme: ColorScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(ctx)
        useDynamic -> dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val palette = if (useDynamic) {
        Palette(
            tiles = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest),
            avatars = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary.copy(alpha = .7f)),
            confetti = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer),
            onAvatar = scheme.surface,
        )
    } else if (dark) darkPalette else lightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = remember { typography() }, content = content)
    }
}
