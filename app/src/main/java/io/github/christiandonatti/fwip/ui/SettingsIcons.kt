package io.github.christiandonatti.fwip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Line icons for the settings groups, drawn so they match the app's own folder and stack icons. */
enum class GroupGlyph { Folder, Server, Palette, Panels, Book, Sun, Sync, Wifi, Disk, Bell, Chart, Info }

@Composable
fun GroupIcon(glyph: GroupGlyph, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = w / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
        fun rrect(x: Float, y: Float, rw: Float, rh: Float, r: Float) =
            drawRoundRect(color, p(x, y), Size(rw * s, rh * s), CornerRadius(r * s), style = stroke)
        when (glyph) {
            GroupGlyph.Folder -> {
                val path = Path().apply {
                    moveTo(3 * s, 7 * s); lineTo(3 * s, 18 * s); quadraticTo(3 * s, 20 * s, 5 * s, 20 * s)
                    lineTo(19 * s, 20 * s); quadraticTo(21 * s, 20 * s, 21 * s, 18 * s); lineTo(21 * s, 9 * s)
                    quadraticTo(21 * s, 7 * s, 19 * s, 7 * s); lineTo(12 * s, 7 * s); lineTo(10 * s, 4.5f * s)
                    lineTo(5 * s, 4.5f * s); quadraticTo(3 * s, 4.5f * s, 3 * s, 7 * s); close()
                }
                drawPath(path, color, style = stroke)
            }
            GroupGlyph.Server -> {
                rrect(3.5f, 4f, 17f, 6.5f, 2f)
                rrect(3.5f, 13.5f, 17f, 6.5f, 2f)
                drawCircle(color, 1.2f * s, p(7.5f, 7.25f))
                drawCircle(color, 1.2f * s, p(7.5f, 16.75f))
                line(12f, 7.25f, 17f, 7.25f)
                line(12f, 16.75f, 17f, 16.75f)
            }
            GroupGlyph.Palette -> {
                val path = Path().apply {
                    moveTo(12 * s, 3 * s)
                    cubicTo(6.5f * s, 3 * s, 3 * s, 7 * s, 3 * s, 12 * s)
                    cubicTo(3 * s, 17 * s, 7 * s, 21 * s, 12 * s, 21 * s)
                    cubicTo(13.5f * s, 21 * s, 14 * s, 20 * s, 13.5f * s, 18.8f * s)
                    cubicTo(13 * s, 17.5f * s, 13.8f * s, 16 * s, 15.5f * s, 16 * s)
                    lineTo(17.5f * s, 16 * s)
                    cubicTo(19.5f * s, 16 * s, 21 * s, 14.5f * s, 21 * s, 12 * s)
                    cubicTo(21 * s, 7 * s, 17 * s, 3 * s, 12 * s, 3 * s); close()
                }
                drawPath(path, color, style = stroke)
                drawCircle(color, 1.4f * s, p(8f, 11f))
                drawCircle(color, 1.4f * s, p(11f, 7.5f))
                drawCircle(color, 1.4f * s, p(15.5f, 8.5f))
            }
            GroupGlyph.Panels -> {
                rrect(3.5f, 3.5f, 17f, 17f, 2.5f)
                line(3.5f, 11f, 20.5f, 11f)
                line(11f, 3.5f, 11f, 11f)
                line(14f, 11f, 12f, 20.5f)
            }
            GroupGlyph.Book -> {
                val left = Path().apply {
                    moveTo(12 * s, 6.5f * s); cubicTo(10 * s, 5 * s, 6.5f * s, 4.5f * s, 3 * s, 5 * s)
                    lineTo(3 * s, 18.5f * s); cubicTo(6.5f * s, 18 * s, 10 * s, 18.5f * s, 12 * s, 20 * s)
                }
                val right = Path().apply {
                    moveTo(12 * s, 6.5f * s); cubicTo(14 * s, 5 * s, 17.5f * s, 4.5f * s, 21 * s, 5 * s)
                    lineTo(21 * s, 18.5f * s); cubicTo(17.5f * s, 18 * s, 14 * s, 18.5f * s, 12 * s, 20 * s)
                }
                drawPath(left, color, style = stroke)
                drawPath(right, color, style = stroke)
                line(12f, 6.5f, 12f, 20f)
            }
            GroupGlyph.Sun -> {
                drawCircle(color, 4f * s, p(12f, 12f), style = stroke)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    line(12f + 7f * cos(a).toFloat(), 12f + 7f * sin(a).toFloat(), 12f + 9.3f * cos(a).toFloat(), 12f + 9.3f * sin(a).toFloat())
                }
            }
            GroupGlyph.Sync -> {
                drawArc(color, 200f, 150f, false, p(4f, 4f), Size(16 * s, 16 * s), style = stroke)
                drawArc(color, 20f, 150f, false, p(4f, 4f), Size(16 * s, 16 * s), style = stroke)
                // Arrow heads at the end of each arc.
                line(19.4f, 7.8f, 19.8f, 4.6f); line(19.4f, 7.8f, 16.3f, 7.2f)
                line(4.6f, 16.2f, 4.2f, 19.4f); line(4.6f, 16.2f, 7.7f, 16.8f)
            }
            GroupGlyph.Wifi -> {
                drawArc(color, 225f, 90f, false, p(2f, 6f), Size(20 * s, 20 * s), style = stroke)
                drawArc(color, 225f, 90f, false, p(5.5f, 9.5f), Size(13 * s, 13 * s), style = stroke)
                drawArc(color, 225f, 90f, false, p(9f, 13f), Size(6 * s, 6 * s), style = stroke)
                drawCircle(color, 1.4f * s, p(12f, 19.5f))
            }
            GroupGlyph.Disk -> {
                rrect(3.5f, 5f, 17f, 14f, 3f)
                line(3.5f, 14f, 20.5f, 14f)
                drawCircle(color, 1.2f * s, p(16.5f, 16.6f))
                line(7f, 16.6f, 11f, 16.6f)
            }
            GroupGlyph.Bell -> {
                val path = Path().apply {
                    moveTo(6 * s, 16.5f * s); lineTo(6 * s, 11 * s)
                    cubicTo(6 * s, 7.5f * s, 8.5f * s, 5 * s, 12 * s, 5 * s)
                    cubicTo(15.5f * s, 5 * s, 18 * s, 7.5f * s, 18 * s, 11 * s)
                    lineTo(18 * s, 16.5f * s); lineTo(19.5f * s, 18 * s); lineTo(4.5f * s, 18 * s); close()
                }
                drawPath(path, color, style = stroke)
                line(10.5f, 20.5f, 13.5f, 20.5f)
                line(12f, 3f, 12f, 5f)
            }
            GroupGlyph.Chart -> {
                line(5f, 20f, 5f, 13f)
                line(10f, 20f, 10f, 8f)
                line(15f, 20f, 15f, 11f)
                line(20f, 20f, 20f, 4.5f)
            }
            GroupGlyph.Info -> {
                drawCircle(color, 9f * s, p(12f, 12f), style = stroke)
                line(12f, 11f, 12f, 16.5f)
                drawCircle(color, 1.2f * s, p(12f, 7.8f))
            }
        }
    }
}
