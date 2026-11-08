package hu.ulpifex.fulesebreszto.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import hu.ulpifex.fulesebreszto.data.ThemeMode

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF9EEFFD),
    secondary = Color(0xFF8ECFF2),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF1D4A5E),
    onSecondaryContainer = Color(0xFFC7E8FF),
    background = Color(0xFF0F1B26),
    onBackground = Color(0xFFE1E8EE),
    surface = Color(0xFF0F1B26),
    onSurface = Color(0xFFE1E8EE),
    surfaceVariant = Color(0xFF243645),
    onSurfaceVariant = Color(0xFFB7C6D1),
    surfaceContainerLowest = Color(0xFF0B151E),
    surfaceContainerLow = Color(0xFF14232F),
    surfaceContainer = Color(0xFF182936),
    surfaceContainerHigh = Color(0xFF1E303E),
    surfaceContainerHighest = Color(0xFF263A49),
    outline = Color(0xFF7A8C99),
    outlineVariant = Color(0xFF34495A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF5C0011),
    errorContainer = Color(0xFF8C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EEFFD),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF00658B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7E8FF),
    onSecondaryContainer = Color(0xFF001E2D),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171D1F),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171D1F),
    surfaceVariant = Color(0xFFDBE4E7),
    onSurfaceVariant = Color(0xFF3F484B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F7),
    surfaceContainer = Color(0xFFE9EFF1),
    surfaceContainerHigh = Color(0xFFE3E9EC),
    surfaceContainerHighest = Color(0xFFDDE4E6),
    outline = Color(0xFF6F797B),
    outlineVariant = Color(0xFFBFC8CB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Semantic status colours that Material 3 does not provide. */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val DarkStatus = StatusColors(
    success = Color(0xFF7DCB8C),
    successContainer = Color(0xFF1C3B26),
    onSuccessContainer = Color(0xFFB9F0C3),
    warning = Color(0xFFFFC14D),
    warningContainer = Color(0xFF4A3500),
    onWarningContainer = Color(0xFFFFDEA6),
)

private val LightStatus = StatusColors(
    success = Color(0xFF2E7D32),
    successContainer = Color(0xFFD6F2D9),
    onSuccessContainer = Color(0xFF0B3A12),
    warning = Color(0xFF9A6200),
    warningContainer = Color(0xFFFFE7B8),
    onWarningContainer = Color(0xFF3A2600),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Large time digits used on cards and in the editor. */
val TimeTextStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = 0.sp)

@Composable
fun FulesTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
