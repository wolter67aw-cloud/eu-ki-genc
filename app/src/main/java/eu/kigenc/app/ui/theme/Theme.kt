package eu.kigenc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

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

enum class DesignFont { DEFAULT, MONO, SERIF, CONDENSED }

/**
 * Ein App-Design („Skin“) – wie die austauschbaren Oberflächen von Synthesizer-Plugins:
 * Hintergrundverlauf, Akzentfarben, Eckenform (weich ↔ Hardware-kantig) und Schrift.
 */
data class AppDesign(
    val id: String,
    val name: String,
    val tagline: String,
    val dark: Boolean,
    val background: List<Color>,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    /** Grundfarbe für Karten und Flächen. */
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    /** Eckenradius in dp für mittlere Elemente (0 = kantig wie ein Hardware-Gerät). */
    val corner: Int,
    val font: DesignFont = DesignFont.DEFAULT,
    /** Eigene Akzentfarben für Verläufe (z. B. Regenbogen); sonst Primär/Tertiär/Sekundär. */
    val accents: List<Color>? = null,
    /** Symbol des Designs (Startseite, Vorschau); null = KI-GenC-Logo. */
    @androidx.annotation.DrawableRes val symbol: Int? = null,
    /** Passendes App-Symbol (siehe data/AppIcons). */
    val iconId: String = "standard",
) {
    val backgroundBrush: Brush get() = Brush.verticalGradient(background)
    val accentBrush: Brush get() = Brush.linearGradient(accents ?: listOf(primary, tertiary, secondary))
}

val DESIGNS = listOf(
    AppDesign(
        "original", "KI-GenC Original", "Nachtblau mit Cyan & Violett – die Logo-Farben", true,
        listOf(LogoNavy, LogoNavyMid, LogoNavyPurple), LogoCyan, Color(0xFFA78BFA), LogoBlue,
        Color(0xFF0A1230), Color(0xFFE6EAF5), Color(0xFFA9B3D1), 16,
    ),
    AppDesign(
        "neon", "Neon Synthwave", "80er-Retro: Pink- und Cyan-Leuchten auf Tiefviolett", true,
        listOf(Color(0xFF0D0221), Color(0xFF1B0B3A), Color(0xFF2E0F4F)), Color(0xFFFF2E97), Color(0xFF00F0FF), Color(0xFFFFD319),
        Color(0xFF1A0838), Color(0xFFF7E9FF), Color(0xFFC3A6E0), 14,
    ),
    AppDesign(
        "analog", "Analog Vintage", "Holz, Creme und Bernstein wie ein alter Analog-Synth", true,
        listOf(Color(0xFF1E140C), Color(0xFF2B1D12), Color(0xFF3A2717)), Color(0xFFFF9F1C), Color(0xFFE8D5B5), Color(0xFFD9541E),
        Color(0xFF2C1E13), Color(0xFFF3E6D0), Color(0xFFC2AE90), 8, DesignFont.SERIF,
    ),
    AppDesign(
        "graphite", "Studio Graphite", "Gebürstetes Metall mit grünen LED-Akzenten", true,
        listOf(Color(0xFF111214), Color(0xFF1A1C1F), Color(0xFF24272B)), Color(0xFF9EFF3E), Color(0xFF4FD1FF), Color(0xFFFF5C5C),
        Color(0xFF1C1E21), Color(0xFFE8EAED), Color(0xFF9AA0A6), 4, DesignFont.CONDENSED,
    ),
    AppDesign(
        "terminal", "Retro Terminal", "Phosphor-Grün auf Schwarz, Schreibmaschinenschrift", true,
        listOf(Color(0xFF000000), Color(0xFF020A04), Color(0xFF04140A)), Color(0xFF33FF66), Color(0xFF00CC44), Color(0xFFFFB000),
        Color(0xFF041008), Color(0xFFB8FFC8), Color(0xFF5FAF72), 2, DesignFont.MONO,
    ),
    AppDesign(
        "sunset", "Sunset Wave", "Warmer Verlauf von Violett über Magenta zu Orange", true,
        listOf(Color(0xFF1A0B2E), Color(0xFF3B0F3A), Color(0xFF5A1A2E)), Color(0xFFFF7A45), Color(0xFFFF3D8B), Color(0xFFFFC857),
        Color(0xFF2A0E33), Color(0xFFFFE9E0), Color(0xFFE0A9B5), 22,
    ),
    AppDesign(
        "gold", "Midnight Gold", "Tiefschwarz mit Gold und Silber – edel wie Studio-Hardware", true,
        listOf(Color(0xFF050505), Color(0xFF0E0D0A), Color(0xFF17140C)), Color(0xFFE5C07B), Color(0xFFC9CCD1), Color(0xFFB08A3E),
        Color(0xFF13110D), Color(0xFFF2EBDD), Color(0xFFADA394), 6, DesignFont.SERIF,
    ),
    AppDesign(
        "ocean", "Deep Ocean", "Petrol und Türkis mit Korallen-Akzent", true,
        listOf(Color(0xFF001219), Color(0xFF002A32), Color(0xFF003B46)), Color(0xFF2EC4B6), Color(0xFF94D2BD), Color(0xFFEE9B00),
        Color(0xFF00222A), Color(0xFFE0F4F1), Color(0xFF8DB8B3), 18,
    ),
    AppDesign(
        "genc", "GenC Hologramm", "Schillernd wie ein Hologramm – Cyan, Violett, Magenta", true,
        listOf(Color(0xFF0A0F2E), Color(0xFF2A1660), Color(0xFF0E3E6C)), Color(0xFF3EE6FF), Color(0xFFFF5BD8), Color(0xFF8B7BFF),
        Color(0xFF141A44), Color(0xFFEAF2FF), Color(0xFFA8B4E0), 16,
        accents = listOf(Color(0xFF3EE6FF), Color(0xFF8B7BFF), Color(0xFFFF5BD8)), symbol = eu.kigenc.app.R.drawable.symbol_genc, iconId = "genc",
    ),
    AppDesign(
        "vielfalt", "Vielfalt", "Bunt und offen – viele Farben, ein Miteinander", true,
        listOf(Color(0xFF1B1B2F), Color(0xFF2A2440), Color(0xFF162447)), Color(0xFFFFB84D), Color(0xFF4ECDC4), Color(0xFFB45CFF),
        Color(0xFF232338), Color(0xFFF1EEFF), Color(0xFFB3ADCF), 18,
        accents = listOf(Color(0xFFFF6B6B), Color(0xFFFFB84D), Color(0xFFFFE66D), Color(0xFF4ECDC4), Color(0xFF4D96FF), Color(0xFFB45CFF)),
        symbol = eu.kigenc.app.R.drawable.symbol_vielfalt, iconId = "vielfalt",
    ),
    AppDesign(
        "vegan", "Vegan", "Frisches Blattgrün, Limette und Getreide", true,
        listOf(Color(0xFF0B2A1A), Color(0xFF123D24), Color(0xFF1A4D2E)), Color(0xFF7ED957), Color(0xFFC9F27B), Color(0xFFE0B15A),
        Color(0xFF12301F), Color(0xFFE8F7E4), Color(0xFFA6C9A0), 20,
        symbol = eu.kigenc.app.R.drawable.symbol_vegan, iconId = "vegan",
    ),
    AppDesign(
        "pride", "Pride", "Regenbogenfarben auf Nachtviolett", true,
        listOf(Color(0xFF140A24), Color(0xFF1E1235), Color(0xFF120A1E)), Color(0xFFFF6FB5), Color(0xFF4D9BFF), Color(0xFFFFD23F),
        Color(0xFF1E1430), Color(0xFFFFF1FA), Color(0xFFC9B3D6), 22,
        accents = listOf(Color(0xFFE40303), Color(0xFFFF8C00), Color(0xFFFFED00), Color(0xFF008026), Color(0xFF004DFF), Color(0xFF750787)),
        symbol = eu.kigenc.app.R.drawable.symbol_pride, iconId = "pride",
    ),
    AppDesign(
        "zweisamkeit", "Zweisamkeit", "Weinrot, Rosé und Gold – für zwei", true,
        listOf(Color(0xFF2A0A14), Color(0xFF4A0F24), Color(0xFF3A0A1C)), Color(0xFFF2C76E), Color(0xFFF7A8B8), Color(0xFFE86A8A),
        Color(0xFF3A0F1E), Color(0xFFFFEFF2), Color(0xFFD9A7B2), 16, DesignFont.SERIF,
        symbol = eu.kigenc.app.R.drawable.symbol_zweisamkeit, iconId = "zweisamkeit",
    ),
    AppDesign(
        "klassisch", "Klassisch", "Schlicht und hell – Weiß mit Indigo", false,
        listOf(Color(0xFFFFFFFF), Color(0xFFF4F5F8), Color(0xFFECEEF3)), Color(0xFF3F51B5), Color(0xFF607D8B), Color(0xFF2196F3),
        Color(0xFFFFFFFF), Color(0xFF1A1C22), Color(0xFF5F6470), 12,
        symbol = eu.kigenc.app.R.drawable.symbol_klassisch, iconId = "klassisch",
    ),
    AppDesign(
        "arctic", "Arctic Glass", "Hell und klar: Eisweiß mit kühlem Blau", false,
        listOf(Color(0xFFF7FAFD), Color(0xFFEAF1F9), Color(0xFFDCE7F5)), Color(0xFF0068C9), Color(0xFF00897B), Color(0xFF5B5BD6),
        Color(0xFFFFFFFF), Color(0xFF14202E), Color(0xFF55657A), 20,
    ),
)

fun designById(id: String?): AppDesign = DESIGNS.firstOrNull { it.id == id } ?: DESIGNS[0]

private fun onColor(c: Color) = if (c.luminance() > 0.45f) Color(0xFF0B0B0F) else Color.White

fun AppDesign.colorScheme(): ColorScheme {
    // Abgestufte Flächen: im dunklen Design etwas heller, im hellen etwas kühler/dunkler als die Grundfläche
    fun container(f: Float) = lerp(surface, if (dark) text else Color(0xFF8FA3BF), f)
    val primaryContainer = lerp(surface, primary, if (dark) 0.35f else 0.18f)
    val secondaryContainer = lerp(surface, secondary, if (dark) 0.3f else 0.18f)
    val tertiaryContainer = lerp(surface, tertiary, if (dark) 0.3f else 0.18f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onColor(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = text,
        secondary = secondary, onSecondary = onColor(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = text,
        tertiary = tertiary, onTertiary = onColor(tertiary),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = text,
        // Hintergrund transparent – darunter liegt der Verlauf des Designs.
        background = Color.Transparent, onBackground = text,
        surface = Color.Transparent, onSurface = text,
        surfaceVariant = container(0.12f), onSurfaceVariant = textMuted,
        surfaceTint = primary,
        surfaceContainerLowest = lerp(surface, background.first(), 0.5f),
        surfaceContainerLow = surface,
        surfaceContainer = container(0.04f),
        surfaceContainerHigh = container(0.08f),
        surfaceContainerHighest = container(0.12f),
        outline = lerp(surface, textMuted, 0.7f),
        outlineVariant = lerp(surface, textMuted, 0.3f),
        inverseSurface = text, inverseOnSurface = surface,
        inversePrimary = lerp(primary, surface, 0.4f),
    )
}

fun AppDesign.shapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape((corner * 0.4f).dp),
    small = RoundedCornerShape((corner * 0.6f).dp),
    medium = RoundedCornerShape(corner.dp),
    large = RoundedCornerShape((corner * 1.3f).dp),
    extraLarge = RoundedCornerShape((corner * 1.7f).dp),
)

private fun Typography.withFont(family: FontFamily?): Typography {
    if (family == null) return this
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

fun AppDesign.typography(): Typography = Typography().withFont(
    when (font) {
        DesignFont.DEFAULT -> null
        DesignFont.MONO -> FontFamily.Monospace
        DesignFont.SERIF -> FontFamily.Serif
        DesignFont.CONDENSED -> FontFamily.SansSerif
    }
)

/** Gewähltes Design (für Hintergrund und Vorschauen). */
val LocalDesign = staticCompositionLocalOf { DESIGNS[0] }

/** true, wenn ein eigenes Design (Verlauf) aktiv ist – sonst Material-You-Farben. */
val LocalBrandTheme = staticCompositionLocalOf { true }

/** Verlauf für Akzente (z. B. Buttons, Überschriften) im aktuellen Design. */
val LogoAccentBrush = Brush.linearGradient(listOf(LogoCyan, LogoBlue, LogoPurple))

@Composable
fun KiTheme(dynamicColor: Boolean = false, designId: String? = null, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val design = designById(designId)
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> design.colorScheme()
    }
    CompositionLocalProvider(LocalBrandTheme provides !useDynamic, LocalDesign provides design) {
        MaterialTheme(
            colorScheme = scheme,
            typography = if (useDynamic) Typography() else design.typography(),
            shapes = if (useDynamic) Shapes() else design.shapes(),
            content = content,
        )
    }
}

/** App-Hintergrund: Verlauf des Designs oder (bei Material You) die normale Hintergrundfarbe. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val brand = LocalBrandTheme.current
    val modifier = if (brand) Modifier.fillMaxSize().background(LocalDesign.current.backgroundBrush)
    else Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    Box(modifier, content = content)
}
