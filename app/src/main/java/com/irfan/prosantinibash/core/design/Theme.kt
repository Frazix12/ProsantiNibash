package com.irfan.prosantinibash.core.design

import android.os.Build
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.irfan.prosantinibash.core.settings.*

private val lightColors = lightColorScheme(
    primary = Color(0xFF24694F), onPrimary = Color.White,
    primaryContainer = Color(0xFFC6ECD9), onPrimaryContainer = Color(0xFF073C29),
    secondary = Color(0xFF506358), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8DF), onSecondaryContainer = Color(0xFF263B30),
    tertiary = Color(0xFF805C20), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2AD), onTertiaryContainer = Color(0xFF483300),
    surfaceContainerLow = Color(0xFFF0F4EE), surfaceContainerHigh = Color(0xFFE4EAE2),
    onSurface = Color(0xFF1A211B), onSurfaceVariant = Color(0xFF465248), outline = Color(0xFF738076), outlineVariant = Color(0xFFC6D1C5),
    background = Color(0xFFF7FAF6),
    surface = Color(0xFFF7FAF6), surfaceContainer = Color(0xFFEDF2EB),
)
private val darkColors = darkColorScheme(
    primary = Color(0xFF91D5AF), onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF174D36), onPrimaryContainer = Color(0xFFC6ECD9),
    secondary = Color(0xFFB7CCBE), onSecondary = Color(0xFF23372B),
    secondaryContainer = Color(0xFF354B3D), onSecondaryContainer = Color(0xFFDCE8DF),
    tertiary = Color(0xFFE8C17B), onTertiary = Color(0xFF442E00),
    tertiaryContainer = Color(0xFF614619), onTertiaryContainer = Color(0xFFFFE2AD),
    surfaceContainerLow = Color(0xFF182019), surfaceContainerHigh = Color(0xFF29332B),
    onSurface = Color(0xFFE0E8DF), onSurfaceVariant = Color(0xFFBFCBBF), outline = Color(0xFF8A978C), outlineVariant = Color(0xFF404D42),
    background = Color(0xFF101510),
    surface = Color(0xFF101510), surfaceContainer = Color(0xFF1D241E),
)

/** One restrained Material theme; dynamic colors are optional on Android 12+. */
@Composable
fun ProsantiTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColors else lightColors
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
}
