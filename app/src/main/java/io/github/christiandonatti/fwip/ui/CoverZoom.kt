package io.github.christiandonatti.fwip.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/** A cover flying between its place on the shelf and the reader. */
data class CoverZoom(val comicId: String, val coverPath: String, val shelfRect: Rect, val opening: Boolean)

private val Glide = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Draws the cover growing from the shelf to fill the screen (and fading into the first page),
 * or shrinking back into its place when the reader closes. Calls [onDone] when it lands.
 */
@Composable
fun CoverZoomOverlay(zoom: CoverZoom, onDone: () -> Unit) {
    val image = remember(zoom.coverPath) {
        runCatching { BitmapFactory.decodeFile(zoom.coverPath)?.asImageBitmap() }.getOrNull()
    }
    if (image == null) {
        LaunchedEffect(zoom) { onDone() }
        return
    }
    val t = remember(zoom) { Animatable(0f) }
    // Once it has landed it draws nothing, whatever happens to the caller's state.
    val landed = remember(zoom) { androidx.compose.runtime.mutableStateOf(false) }
    val done by androidx.compose.runtime.rememberUpdatedState(onDone)
    LaunchedEffect(zoom) {
        t.animateTo(1f, tween(if (zoom.opening) 460 else 400, easing = Glide))
        landed.value = true
        done()
    }
    if (landed.value) return
    Canvas(Modifier.fillMaxSize()) {
        // Where the page will be: the cover fitted to the screen height, centred.
        val ratio = image.width.toFloat() / image.height
        val h = min(size.height, size.width / ratio)
        val w = h * ratio
        val full = Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h))
        val v = t.value
        // Opening grows shelf -> screen; closing shrinks screen -> shelf.
        val p = if (zoom.opening) v else 1f - v
        val r = lerp(zoom.shelfRect, full, p)
        val corner = 16.dp(this.density) * (1f - p)
        val alpha = if (zoom.opening) {
            // Hand over to the real page in the last stretch.
            if (v < 0.72f) 1f else (1f - (v - 0.72f) / 0.28f).coerceIn(0f, 1f)
        } else {
            // Appear over the closing reader, then land exactly on the shelf cover.
            (v / 0.18f).coerceIn(0f, 1f)
        }
        val path = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(corner, corner)))
        }
        clipPath(path) {
            // Crop the picture like the shelf does (centre crop to the tile's shape).
            val target = r.width / r.height.coerceAtLeast(1f)
            val src = if (ratio > target) {
                val sw = (image.height * target).roundToInt().coerceIn(1, image.width)
                IntOffset((image.width - sw) / 2, 0) to IntSize(sw, image.height)
            } else {
                val sh = (image.width / target).roundToInt().coerceIn(1, image.height)
                IntOffset(0, (image.height - sh) / 2) to IntSize(image.width, sh)
            }
            drawImage(
                image,
                srcOffset = src.first,
                srcSize = src.second,
                dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha,
            )
        }
    }
}

private fun Int.dp(density: Float) = this * density
