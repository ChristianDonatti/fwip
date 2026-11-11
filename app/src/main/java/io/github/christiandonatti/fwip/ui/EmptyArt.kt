package io.github.christiandonatti.fwip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/** Small drawings for places with nothing to show yet, in the colours of the theme. */
enum class EmptyKind { Shelf, Search, Done }

@Composable
fun EmptyArt(kind: EmptyKind, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val ink = FwipColors.Text
    val soft = FwipColors.TextMuted
    val accent = FwipColors.Amber
    Canvas(modifier.size(size, size * 0.78f).clearAndSetSemantics { }) {
        val u = this.size.width / 100f
        when (kind) {
            EmptyKind.Shelf -> shelf(u, ink, soft, accent)
            EmptyKind.Search -> search(u, ink, soft, accent)
            EmptyKind.Done -> done(u, ink, soft, accent)
        }
    }
}

private fun DrawScope.book(x: Float, y: Float, w: Float, h: Float, color: Color, u: Float, band: Color? = null) {
    drawRoundRect(color, Offset(x, y - h), Size(w, h), CornerRadius(1.6f * u))
    if (band != null) drawRect(band, Offset(x, y - h * 0.78f), Size(w, h * 0.10f))
}

/** A shelf with a few books and room for more. */
private fun DrawScope.shelf(u: Float, ink: Color, soft: Color, accent: Color) {
    val floor = 64f * u
    drawRoundRect(soft.copy(alpha = 0.35f), Offset(8f * u, floor), Size(84f * u, 3.5f * u), CornerRadius(2f * u))
    book(16f * u, floor, 9f * u, 40f * u, ink.copy(alpha = 0.85f), u, accent)
    book(27f * u, floor, 7f * u, 34f * u, accent, u)
    book(36f * u, floor, 10f * u, 44f * u, ink.copy(alpha = 0.55f), u, ink.copy(alpha = 0.9f))
    rotate(-16f, Offset(57f * u, floor)) { book(49f * u, floor, 8f * u, 38f * u, ink.copy(alpha = 0.7f), u, accent) }
    // The book that is not there yet.
    drawRoundRect(
        soft.copy(alpha = 0.7f), Offset(68f * u, floor - 40f * u), Size(11f * u, 40f * u), CornerRadius(1.6f * u),
        style = Stroke(1.4f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 3f * u))),
    )
    drawLine(accent, Offset(73.5f * u, floor - 25f * u), Offset(73.5f * u, floor - 15f * u), 1.6f * u, StrokeCap.Round)
    drawLine(accent, Offset(68.5f * u, floor - 20f * u), Offset(78.5f * u, floor - 20f * u), 1.6f * u, StrokeCap.Round)
}

/** A page under a magnifying glass. */
private fun DrawScope.search(u: Float, ink: Color, soft: Color, accent: Color) {
    drawRoundRect(ink.copy(alpha = 0.10f), Offset(20f * u, 8f * u), Size(44f * u, 60f * u), CornerRadius(3f * u))
    drawRoundRect(soft.copy(alpha = 0.6f), Offset(20f * u, 8f * u), Size(44f * u, 60f * u), CornerRadius(3f * u), style = Stroke(1.2f * u))
    for (i in 0 until 5) {
        val w = if (i == 4) 18f else 32f
        drawLine(soft.copy(alpha = 0.55f), Offset(26f * u, (18f + i * 9f) * u), Offset((26f + w) * u, (18f + i * 9f) * u), 1.6f * u, StrokeCap.Round)
    }
    val c = Offset(62f * u, 46f * u)
    drawCircle(accent.copy(alpha = 0.18f), 15f * u, c)
    drawCircle(accent, 15f * u, c, style = Stroke(2.6f * u))
    drawLine(accent, Offset(73f * u, 57f * u), Offset(84f * u, 68f * u), 3.6f * u, StrokeCap.Round)
}

/** A neat pile of books, all read. */
private fun DrawScope.done(u: Float, ink: Color, soft: Color, accent: Color) {
    val colors = listOf(ink.copy(alpha = 0.55f), accent, ink.copy(alpha = 0.85f))
    val widths = listOf(56f, 48f, 52f)
    val lefts = listOf(22f, 27f, 24f)
    for (i in 0 until 3) {
        val y = (62f - i * 12f) * u
        drawRoundRect(colors[i], Offset(lefts[i] * u, y - 10f * u), Size(widths[i] * u, 10f * u), CornerRadius(2f * u))
        drawRect(Color.White.copy(alpha = 0.55f), Offset((lefts[i] + widths[i] - 9f) * u, y - 8f * u), Size(7.5f * u, 6f * u))
    }
    drawRoundRect(soft.copy(alpha = 0.35f), Offset(12f * u, 62f * u), Size(76f * u, 3f * u), CornerRadius(2f * u))
    val c = Offset(76f * u, 20f * u)
    drawCircle(accent, 11f * u, c)
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x - 5f * u, c.y + 0.5f * u); lineTo(c.x - 1.5f * u, c.y + 4f * u); lineTo(c.x + 5.5f * u, c.y - 4f * u)
    }
    drawPath(p, Color(0xFF1B1C22), style = Stroke(2.6f * u, cap = StrokeCap.Round))
}
