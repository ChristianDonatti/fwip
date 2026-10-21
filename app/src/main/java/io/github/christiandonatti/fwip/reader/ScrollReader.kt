package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * Vertical scrolling, for webtoons and other long strips: every page at the full width of the
 * screen, one under the other with no gaps, scrolled with the finger. Side taps (and the volume
 * keys) move most of a screen down or up.
 */
@Composable
fun ScrollReader(
    host: ReaderHost,
    startSpread: Int,
    commands: Flow<NavCommand>,
    onSpreadChanged: (Int) -> Unit,
    onTap: (Offset, IntSize) -> Unit,
    onEnd: () -> Unit,
) {
    val spreads = host.spreads
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startSpread.coerceIn(0, spreads.lastIndex.coerceAtLeast(0)))
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { onSpreadChanged(it) }
    }
    // Scrolling past the last page shows the end card, as turning past it does.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward }
            .distinctUntilChanged()
            .drop(1)
            .collect { atEnd -> if (atEnd) onEnd() }
    }
    LaunchedEffect(listState, commands) {
        commands.collect { cmd ->
            when (cmd) {
                is NavCommand.Go -> {
                    if (cmd.delta > 0 && !listState.canScrollForward) onEnd()
                    else listState.animateScrollBy(cmd.delta * viewport.height * 0.85f)
                }
                is NavCommand.Seek -> listState.scrollToItem(cmd.spread.coerceIn(0, spreads.lastIndex))
            }
        }
    }
    // Decode what comes next before the finger gets there.
    LaunchedEffect(listState.firstVisibleItemIndex, host) {
        val first = listState.firstVisibleItemIndex
        for (n in first + 1..first + 3) {
            spreads.getOrNull(n)?.firstOrNull()?.let { host.cache.get(it, host.decodeW, host.decodeW * STRIP) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FwipColors.ReaderBg)
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) { detectTapGestures(onTap = { pos -> onTap(pos, viewport) }) },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(spreads.size, key = { spreads[it].first() }) { i ->
                StripPage(spreads[i].first(), host)
            }
        }
    }
}

/** Tall strips may be many screens high; they are decoded up to this many widths tall. */
private const val STRIP = 16

@Composable
private fun StripPage(page: Int, host: ReaderHost) {
    val w = host.decodeW
    val bitmap by produceState<Bitmap?>(host.cache.peek(page, w, w * STRIP), page, w) {
        if (value == null) value = host.cache.get(page, w, w * STRIP)
    }
    val b = bitmap
    if (b != null) {
        Image(
            b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(b.width / b.height.toFloat().coerceAtLeast(1f)),
        )
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = FwipColors.TextMuted, strokeWidth = 2.dp)
        }
    }
}
