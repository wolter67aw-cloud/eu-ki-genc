package eu.kigenc.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kigenc.app.ui.theme.AppDesign
import eu.kigenc.app.ui.theme.DESIGNS
import eu.kigenc.app.ui.theme.DesignFont
import kotlin.math.cos
import kotlin.math.sin

/** Auswahl der App-Designs als „Synthesizer-Skins“ mit Live-Vorschau. */
@Composable
fun DesignPicker(selectedId: String, enabled: Boolean, onSelect: (AppDesign) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(DESIGNS, key = { it.id }) { d ->
            DesignCard(d, selected = enabled && d.id == selectedId) { onSelect(d) }
        }
    }
}

private fun AppDesign.fontFamily(): FontFamily = when (font) {
    DesignFont.MONO -> FontFamily.Monospace
    DesignFont.SERIF -> FontFamily.Serif
    DesignFont.CONDENSED -> FontFamily.SansSerif
    DesignFont.DEFAULT -> FontFamily.Default
}

@Composable
private fun DesignCard(d: AppDesign, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(maxOf(4, d.corner).dp)
    Column(Modifier.width(156.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(shape)
                .background(d.backgroundBrush)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else d.textMuted.copy(alpha = 0.35f), shape)
                .clickable(onClick = onClick)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // „Display“ des Geräts
                Box(
                    Modifier.fillMaxWidth().height(30.dp)
                        .clip(RoundedCornerShape((d.corner * 0.5f).dp))
                        .background(d.surface)
                        .border(1.dp, d.primary.copy(alpha = 0.5f), RoundedCornerShape((d.corner * 0.5f).dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("KI-GENC", color = d.primary, fontFamily = d.fontFamily(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                    d.symbol?.let {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(it), null,
                            Modifier.align(Alignment.CenterEnd).size(22.dp).clip(RoundedCornerShape(5.dp))
                        )
                    }
                }
                // Drehregler
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Knob(d, d.primary, 0.72f)
                    Knob(d, d.secondary, 0.4f)
                    Knob(d, d.tertiary, 0.58f)
                }
                // Fader
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    listOf(0.8f, 0.45f, 0.65f, 0.3f, 0.9f, 0.55f).forEachIndexed { i, v ->
                        val c = listOf(d.primary, d.secondary, d.tertiary)[i % 3]
                        Box(
                            Modifier.weight(1f).fillMaxHeight(v)
                                .clip(RoundedCornerShape((d.corner * 0.25f).dp))
                                .background(c.copy(alpha = 0.85f))
                        )
                    }
                }
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, "Ausgewählt", tint = d.primary, modifier = Modifier.align(Alignment.BottomEnd).size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(d.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(d.tagline, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Knob(d: AppDesign, accent: Color, value: Float) {
    Canvas(Modifier.size(36.dp)) {
        val stroke = 4.dp.toPx()
        val r = size.minDimension / 2 - stroke
        val topLeft = Offset(center.x - r, center.y - r)
        // Skala (Hintergrund) und Wert (Akzent) – 270°-Bogen wie bei echten Reglern
        drawArc(d.textMuted.copy(alpha = 0.3f), 135f, 270f, false, topLeft, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(accent, 135f, 270f * value, false, topLeft, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(d.surface, r - stroke * 1.2f, center)
        val angle = Math.toRadians((135 + 270 * value).toDouble())
        val inner = r - stroke * 2.2f
        drawLine(accent, center, Offset(center.x + (cos(angle) * inner).toFloat(), center.y + (sin(angle) * inner).toFloat()), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Auswahl des Symbols auf dem Startbildschirm. */
@Composable
fun AppIconPicker(currentId: String, onSelect: (eu.kigenc.app.data.AppIcon) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(eu.kigenc.app.data.AppIcons.all, key = { it.id }) { icon ->
            val selected = icon.id == currentId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp).clickable { onSelect(icon) }
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(icon.preview), icon.label,
                    Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                        .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(icon.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
