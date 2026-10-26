package io.github.christiandonatti.fwip.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** Reader built on a horizontal pager, with the "Girar" (3D rotation) and "Deslizar" turns. */
@Composable
fun PagerReader(
    host: ReaderHost,
    style: TurnStyle,
    startSpread: Int,
    commands: Flow<NavCommand>,
    onSpreadChanged: (Int) -> Unit,
    onZoomState: (ZoomState) -> Unit,
    onTap: (Offset, IntSize, ZoomState) -> Unit,
    onDoubleTap: (Offset, ZoomState) -> Boolean,
    onEnd: () -> Unit,
) {
    val spreads = host.spreads
    val pagerState = rememberPagerState(initialPage = startSpread.coerceIn(0, spreads.lastIndex)) { spreads.size }
    var currentZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { onSpreadChanged(it) }
    }
    LaunchedEffect(pagerState, commands) {
        commands.collect { cmd ->
            when (cmd) {
                is NavCommand.Go -> {
                    if (cmd.delta > 0 && pagerState.currentPage == spreads.lastIndex) {
                        onEnd()
                    } else {
                        val target = (pagerState.currentPage + cmd.delta).coerceIn(0, spreads.lastIndex)
                        if (target != pagerState.currentPage) pagerState.animateScrollToPage(target, animationSpec = androidx.compose.animation.core.tween((380 * ReaderLook.speed).toInt()))
                    }
                }
                is NavCommand.Seek -> pagerState.scrollToPage(cmd.spread.coerceIn(0, spreads.lastIndex))
            }
        }
    }
    // Pre-decode neighbours so turning never shows a spinner.
    LaunchedEffect(pagerState.settledPage, host) {
        val s = pagerState.settledPage
        host.cache.hold((s - 1..s + 1).flatMap { spreads.getOrNull(it).orEmpty() }.toSet())
        for (n in listOf(s + 1, s - 1, s + 2)) {
            spreads.getOrNull(n)?.forEach { host.cache.get(it, host.wFor(it), host.decodeH) }
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = host.rtl,
        beyondViewportPageCount = 1,
        userScrollEnabled = !currentZoomed,
        key = { spreads[it].first() },
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val zoom = remember { ZoomState() }
        val isCurrent = pagerState.settledPage == index
        LaunchedEffect(isCurrent) { if (isCurrent) onZoomState(zoom) }
        LaunchedEffect(zoom.isZoomed, isCurrent) { if (isCurrent) currentZoomed = zoom.isZoomed }
        LaunchedEffect(pagerState.settledPage) { if (pagerState.settledPage != index) zoom.snapReset() }

        Box(
            Modifier
                .fillMaxSize()
                .turnEffect(pagerState, index, style, host.rtl, host.double)
                // Opaque like paper, so the page underneath never shows through the margins.
                .background(FwipColors.ReaderBg)
        ) {
            Zoomable(
                state = zoom,
                onTap = { pos, size -> onTap(pos, size, zoom) },
                onDoubleTap = { pos -> onDoubleTap(pos, zoom) },
                modifier = Modifier.fillMaxSize(),
            ) {
                SpreadView(spreads[index], host.double, host.rtl, host.cache, host.decodeW, host.decodeH, isWide = host::isWide)
            }
        }
    }
}

/**
 * Page turn animation driven by the pager's scroll position.
 * Girar: the page you leave rotates over its spine, revealing the next one underneath.
 * Deslizar: the next page slides over the current one, which sinks back slightly.
 */
private fun Modifier.turnEffect(
    pagerState: PagerState,
    index: Int,
    style: TurnStyle,
    rtl: Boolean,
    double: Boolean,
): Modifier {
    val rotate = style == TurnStyle.Flip && !double
    fun offsetOf(): Float = ((index - pagerState.currentPage) - pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
    return this
        .zIndex(
            offsetOf().let { o ->
                // Rotate: the page turning away on top, then the current one, then the next.
                // Slide: the incoming page over the current one, which sits over the previous.
                if (rotate) (if (o < 0f) 2f else 1f - o) else (if (o > 0f) 2f else 1f + o)
            }
        )
        .graphicsLayer {
            val o = offsetOf()
            val dir = if (rtl) -1f else 1f
            // Undo the pager's own sliding so we control where the page sits.
            val visual = o * size.width * dir
            cameraDistance = 14f * density * 160f
            if (rotate) {
                translationX = -visual
                if (o < 0f) {
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                    rotationY = o * 180f * dir
                    alpha = if (abs(o) < 0.5f) 1f else 0f
                }
            } else if (o < 0f) {
                translationX = -visual
                val t = abs(o)
                scaleX = 1f - 0.06f * t
                scaleY = 1f - 0.06f * t
                alpha = 1f - 0.5f * t
            }
        }
        .drawWithContent {
            drawContent()
            val o = offsetOf()
            if (o == 0f) return@drawWithContent
            if (rotate) {
                if (o < 0f) {
                    val t = (abs(o) * 2f).coerceAtMost(1f)
                    drawRect(
                        Brush.horizontalGradient(
                            if (rtl) listOf(Color.Black.copy(alpha = 0.05f * t), Color.Black.copy(alpha = 0.45f * t))
                            else listOf(Color.Black.copy(alpha = 0.45f * t), Color.Black.copy(alpha = 0.05f * t))
                        )
                    )
                } else {
                    drawRect(Color.Black.copy(alpha = 0.4f * o))
                }
            } else if (o > 0f) {
                val w = 48f
                val left = if (rtl) size.width else -w
                val shade = Color.Black.copy(alpha = 0.35f * (1f - o))
                drawRect(
                    Brush.horizontalGradient(
                        if (rtl) listOf(shade, Color.Transparent) else listOf(Color.Transparent, shade),
                        startX = left,
                        endX = left + w,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(w, size.height),
                )
            }
        }
}
