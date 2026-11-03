package eu.kigenc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// EU-Farben (für die Europaflagge)
val EuBlue = Color(0xFF003399)
val EuGold = Color(0xFFFFCC00)

// Farben des KI-GenC-Logos: dunkles Nachtblau, Cyan → Blau → Violett
val LogoNavy = Color(0xFF00081B)
val LogoNavyMid = Color(0xFF06103A)
val LogoNavyPurple = Color(0xFF150B3E)
val LogoCyan = Color(0xFF22C3F5)
val LogoBlue = Color(0xFF3B7BF2)
val LogoPurple = Color(0xFF8B5CF6)

/** Verlauf in den Logo-Farben für den App-Hintergrund. */
val LogoBackgroundBrush = Brush.verticalGradient(listOf(LogoNavy, LogoNavyMid, LogoNavyPurple))

/** Verlauf für Akzente (z. B. Buttons, Überschriften) wie im Logo. */
val LogoAccentBrush = Brush.linearGradient(listOf(LogoCyan, LogoBlue, LogoPurple))

private val BrandColors = darkColorScheme(
    primary = LogoCyan,
    onPrimary = Color(0xFF00202E),
    primaryContainer = Color(0xFF1A3C93),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF22104F),
    secondaryContainer = Color(0xFF3A2A86),
    onSecondaryContainer = Color(0xFFEDE4FF),
    tertiary = LogoBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF173A7A),
    onTertiaryContainer = Color(0xFFD9E4FF),
    // Hintergrund transparent – darunter liegt der Logo-Verlauf.
    background = Color.Transparent,
    onBackground = Color(0xFFE6EAF5),
    surface = Color.Transparent,
    onSurface = Color(0xFFE6EAF5),
    surfaceVariant = Color(0xFF1A2350),
    onSurfaceVariant = Color(0xFFA9B3D1),
    surfaceTint = LogoCyan,
    surfaceContainerLowest = Color(0xFF03081A),
    surfaceContainerLow = Color(0xFF0A1230),
    surfaceContainer = Color(0xFF0F1838),
    surfaceContainerHigh = Color(0xFF162049),
    surfaceContainerHighest = Color(0xFF1F2A5A),
    outline = Color(0xFF4F5B88),
    outlineVariant = Color(0xFF2A3463),
    inverseSurface = Color(0xFFE6EAF5),
    inverseOnSurface = Color(0xFF0A1230),
    inversePrimary = Color(0xFF00658A),
)

/** true, wenn das Logo-Design (dunkler Verlauf) aktiv ist – sonst Material-You-Farben. */
val LocalBrandTheme = staticCompositionLocalOf { true }

@Composable
fun KiTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> BrandColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBrandTheme provides !useDynamic) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** App-Hintergrund: Logo-Verlauf oder (bei Material You) die normale Hintergrundfarbe. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val brand = LocalBrandTheme.current
    val modifier = if (brand) Modifier.fillMaxSize().background(LogoBackgroundBrush)
    else Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    Box(modifier, content = content)
}
