package io.github.christiandonatti.fwip.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set
    internal var size = IntSize.Zero

    val isZoomed: Boolean get() = scale > 1.02f

    internal fun clamp(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Zooms by [factor] keeping the screen point [focus] (relative to the top-left) under the finger. */
    internal fun zoomBy(factor: Float, focus: Offset) {
        val newScale = (scale * factor).coerceIn(MIN, MAX)
        val c = focus - Offset(size.width / 2f, size.height / 2f)
        val ratio = newScale / scale
        offset = clamp(c - (c - offset) * ratio, newScale)
        scale = newScale
    }

    internal fun panBy(delta: Offset) {
        offset = clamp(offset + delta, scale)
    }

    /** Smoothly moves to [target] scale centred on [focus]. */
    suspend fun animateTo(target: Float, focus: Offset? = null) {
        val startScale = scale
        val startOffset = offset
        val endOffset = if (focus == null || target <= 1f) {
            Offset.Zero
        } else {
            val c = focus - Offset(size.width / 2f, size.height / 2f)
            clamp(c - (c - startOffset) * (target / startScale), target)
        }
        val anim = Animatable(0f)
        anim.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f)) {
            val t = value
            scale = startScale + (target - startScale) * t
            offset = startOffset + (endOffset - startOffset) * t
        }
    }

    fun snapReset() {
        scale = 1f
        offset = Offset.Zero
    }

    companion object {
        const val MIN = 1f
        const val MAX = 5f
        const val DOUBLE_TAP = 2.5f
    }
}

/**
 * Pinch, pan and double-tap zoom. With one finger at normal size the touches are left
 * untouched so the pager underneath can still swipe between pages.
 */
@Composable
fun Zoomable(
    state: ZoomState,
    onTap: (Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .onSizeChanged { state.size = it }
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || state.isZoomed) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoom != 1f && centroid != Offset.Unspecified) state.zoomBy(zoom, centroid)
                            state.panBy(pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (state.scale < 1.05f && state.scale != 1f) {
                        scope.launch { state.animateTo(1f) }
                    }
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        scope.launchZoomToggle(state, pos)
                    },
                    onTap = { pos -> onTap(pos, state.size) },
                )
            }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x
                translationY = state.offset.y
            },
            content = content,
        )
    }
}

private fun CoroutineScope.launchZoomToggle(state: ZoomState, pos: Offset) {
    launch {
        if (state.isZoomed) state.animateTo(1f) else state.animateTo(ZoomState.DOUBLE_TAP, pos)
    }
}

@Composable
fun rememberZoomState(key: Any?): ZoomState = remember(key) { ZoomState() }
