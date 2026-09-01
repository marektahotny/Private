package sk.planx4.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Same palette as the "Plán X4" concept artifact, so the app looks like the mockups it was
// designed from. Named tokens rather than raw colors sprinkled through the UI code.

private val LaserRed = Color(0xFFD8391F)
private val LaserRedDark = Color(0xFFFF6A4D)
private val TealAccent = Color(0xFF2B6E5E)
private val TealAccentDark = Color(0xFF4FBBA0)

private val LightColors = lightColorScheme(
    primary = LaserRed,
    onPrimary = Color(0xFFFFF9F6),
    secondary = TealAccent,
    onSecondary = Color(0xFFF2FBF8),
    background = Color(0xFFF3F4EE),
    onBackground = Color(0xFF1B2430),
    surface = Color(0xFFFFFFFE),
    onSurface = Color(0xFF1B2430),
    surfaceVariant = Color(0xFFE9EAE2),
    onSurfaceVariant = Color(0xFF55605A),
    outline = Color(0xFFA9AF9F)
)

private val DarkColors = darkColorScheme(
    primary = LaserRedDark,
    onPrimary = Color(0xFF1B0E08),
    secondary = TealAccentDark,
    onSecondary = Color(0xFF08221C),
    background = Color(0xFF10151C),
    onBackground = Color(0xFFE9EBE3),
    surface = Color(0xFF171E27),
    onSurface = Color(0xFFE9EBE3),
    surfaceVariant = Color(0xFF1E2630),
    onSurfaceVariant = Color(0xFF9AA5A0),
    outline = Color(0xFF3C4A57)
)

private val PlanX4Typography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
)

@Composable
fun PlanX4Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = PlanX4Typography, content = content)
}
