package eu.kigenc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val EuBlue = Color(0xFF003399)
val EuGold = Color(0xFFFFCC00)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A47C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF6E5D00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE170),
    onSecondaryContainer = Color(0xFF221B00),
    tertiary = Color(0xFF006A62),
    tertiaryContainer = Color(0xFF9EF2E6),
    background = Color(0xFFFBFCFF),
    surface = Color(0xFFFBFCFF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5FC),
    surfaceContainer = Color(0xFFEEF0F8),
    surfaceContainerHigh = Color(0xFFE8EAF3),
    surfaceContainerHighest = Color(0xFFE2E4ED),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF00287D),
    primaryContainer = Color(0xFF0B3AAF),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = EuGold,
    onSecondary = Color(0xFF3A3000),
    secondaryContainer = Color(0xFF534600),
    onSecondaryContainer = Color(0xFFFFE170),
    tertiary = Color(0xFF82D5CA),
    tertiaryContainer = Color(0xFF005049),
    background = Color(0xFF11131A),
    surface = Color(0xFF11131A),
    surfaceContainerLowest = Color(0xFF0C0E14),
    surfaceContainerLow = Color(0xFF191B22),
    surfaceContainer = Color(0xFF1D1F27),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
)

@Composable
fun KiTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
