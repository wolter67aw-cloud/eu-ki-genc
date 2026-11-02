package eu.kigenc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import eu.kigenc.app.ui.theme.EuBlue
import eu.kigenc.app.ui.theme.EuGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * Europaflagge nach den offiziellen Proportionen:
 * Seitenverhältnis 3:2, zwölf goldene Sterne auf einem Kreis mit Radius = 1/3 der Flaggenhöhe,
 * jeder Stern mit Radius = 1/18 der Höhe, eine Spitze zeigt nach oben.
 */
@Composable
fun EuFlag(modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(3.dp))
    ) {
        drawRect(EuBlue)
        val h = size.height
        val cx = size.width / 2f
        val cy = h / 2f
        val ring = h / 3f
        val starRadius = h / 18f
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0 - 90.0)
            drawPath(
                starPath(cx + ring * cos(angle).toFloat(), cy + ring * sin(angle).toFloat(), starRadius),
                EuGold
            )
        }
    }
}

private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val inner = r * 0.382f
    for (k in 0 until 10) {
        val radius = if (k % 2 == 0) r else inner
        val angle = Math.toRadians(-90.0 + k * 36.0)
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
