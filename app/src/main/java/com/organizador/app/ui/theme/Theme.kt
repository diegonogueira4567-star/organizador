package com.organizador.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.organizador.domain.model.AppSettings
import com.organizador.domain.model.Density as DensityPref
import com.organizador.domain.model.ThemeMode

/** Espaçamentos derivados da preferência de densidade. */
data class Spacing(val itemVertical: Dp, val screen: Dp, val card: Dp, val gap: Dp)

val LocalSpacing = staticCompositionLocalOf { Spacing(10.dp, 16.dp, 14.dp, 12.dp) }
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

object PriorityColors {
    val p1 = Color(0xFFD1453B)
    val p2 = Color(0xFFEB8909)
    val p3 = Color(0xFF246FE0)
    val p4 = Color(0xFF8A8A8A)
}

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f).compositeOver(Color.White),
    onPrimaryContainer = Color(0xFF3A0A05),
    secondary = Color(0xFF5F6368),
    background = Color(0xFFFCFAF8),
    onBackground = Color(0xFF202020),
    surface = Color(0xFFFCFAF8),
    onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFF2EFED),
    onSurfaceVariant = Color(0xFF666666),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F5F3),
    surfaceContainer = Color(0xFFF3F0EE),
    surfaceContainerHigh = Color(0xFFEDEAE8),
    surfaceContainerHighest = Color(0xFFE7E4E2),
    outline = Color(0xFFD6D2CF),
    outlineVariant = Color(0xFFE8E4E1),
    error = Color(0xFFB3261E),
)

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.25f).compositeOver(Color(0xFF1E1E1E)),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFB0B0B0),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceContainerLowest = Color(0xFF181818),
    surfaceContainerLow = Color(0xFF242424),
    surfaceContainer = Color(0xFF282828),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF353535),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFF2B8B5),
)

@Composable
fun OrganizadorTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = Color(settings.accentColor)
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }
    val spacing = when (settings.density) {
        DensityPref.COMPACT -> Spacing(6.dp, 12.dp, 10.dp, 8.dp)
        DensityPref.COMFORTABLE -> Spacing(10.dp, 16.dp, 14.dp, 12.dp)
        DensityPref.SPACIOUS -> Spacing(14.dp, 20.dp, 18.dp, 16.dp)
    }
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalAppSettings provides settings,
        // Tamanho de texto do app multiplica o do sistema (acessibilidade preservada).
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * settings.fontScale),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
