package app.truenasmobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.truenasmobile.data.ThemeMode

// A storage-appliance palette: deep teal-blue primary, cyan accents.
private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF006B5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF2DA),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C1F),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F7),
    surfaceContainer = Color(0xFFEBEEF1),
    surfaceContainerHigh = Color(0xFFE5E8EB),
    surfaceContainerHighest = Color(0xFFE0E3E6),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85CFFF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323F),
    secondaryContainer = Color(0xFF374956),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFF80D5BE),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005143),
    onTertiaryContainer = Color(0xFF9CF2DA),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1418),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF252B2F),
    surfaceContainerHighest = Color(0xFF30363A),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    error = Color(0xFFFFB4AB),
)

/** Health colours, tuned per theme so they stay readable. */
@Immutable
data class StatusColors(
    val good: Color,
    val warn: Color,
    val bad: Color,
    val info: Color,
    val idle: Color,
    val cpu: Color,
    val rx: Color,
    val tx: Color,
    val arc: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF1B8A4B),
    warn = Color(0xFFB26A00),
    bad = Color(0xFFC62828),
    info = Color(0xFF0B6BCB),
    idle = Color(0xFF6B7680),
    cpu = Color(0xFF0B79B8),
    rx = Color(0xFF00897B),
    tx = Color(0xFF7E57C2),
    arc = Color(0xFF26A69A),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF5DD68C),
    warn = Color(0xFFFFB74D),
    bad = Color(0xFFFF6B6B),
    info = Color(0xFF64B5F6),
    idle = Color(0xFF8E99A3),
    cpu = Color(0xFF4FC3F7),
    rx = Color(0xFF4DD0B8),
    tx = Color(0xFFB39DDB),
    arc = Color(0xFF4DB6AC),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object AppColors {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}

@Composable
fun AppTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF181818),
            surfaceContainerHighest = Color(0xFF202020),
        )
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
