@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.christiandonatti.fwip.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.data.ComicSource
import io.github.christiandonatti.fwip.data.ComicSources
import io.github.christiandonatti.fwip.ui.CircleIconButton
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.PillStyle
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface Loaded {
    data object Loading : Loaded
    data class Ready(val source: ComicSource) : Loaded
    data class Failed(val message: String) : Loaded
}

@Composable
fun ReaderScreen(
    comic: Comic,
    startPage: Int,
    onClose: () -> Unit,
    onProgress: (page: Int, pageCount: Int) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { ReaderSettings(context) }

    val loaded by produceState<Loaded>(Loaded.Loading, comic.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { ComicSources.open(context, comic) }.fold(
                onSuccess = { src -> if (src.pageCount > 0) Loaded.Ready(src) else { src.close(); Loaded.Failed("Este arquivo não tem páginas de imagem.") } },
                onFailure = { Loaded.Failed("Não foi possível abrir este arquivo.\n${it.message ?: it.javaClass.simpleName}") },
            )
        }
    }
    DisposableEffect(loaded) {
        onDispose { (loaded as? Loaded.Ready)?.source?.close() }
    }

    BackHandler(onBack = onClose)
    KeepScreenOn()

    Box(Modifier.fillMaxSize().background(FwipColors.ReaderBg)) {
        when (val l = loaded) {
            Loaded.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = FwipColors.Text)
            is Loaded.Failed -> Column(
                Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(comic.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(l.message, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted, textAlign = TextAlign.Center)
                PillButton("Voltar", onClose)
            }
            is Loaded.Ready -> Reader(comic, l.source, startPage, settings, onClose, onProgress)
        }
    }
}

/** Groups page indices into what is shown at once: single pages, or a cover and then pairs. */
private fun buildSpreads(pageCount: Int, double: Boolean): List<List<Int>> {
    if (!double) return (0 until pageCount).map { listOf(it) }
    val out = mutableListOf(listOf(0))
    var i = 1
    while (i < pageCount) {
        out += if (i + 1 < pageCount) listOf(i, i + 1) else listOf(i)
        i += 2
    }
    return out
}

@Composable
private fun Reader(
    comic: Comic,
    source: ComicSource,
    startPage: Int,
    settings: ReaderSettings,
    onClose: () -> Unit,
    onProgress: (Int, Int) -> Unit,
) {
    val cache = remember(source) { PageCache(source) }
    DisposableEffect(cache) { onDispose { cache.clear() } }

    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val double = when (settings.spreadMode) {
        SpreadMode.Single -> false
        SpreadMode.Double -> true
        SpreadMode.Auto -> landscape && config.smallestScreenWidthDp >= 600
    }
    val spreads = remember(source.pageCount, double) { buildSpreads(source.pageCount, double) }
    val rtl = settings.mangaMode

    val startSpread = remember(spreads) {
        spreads.indexOfFirst { startPage in it }.coerceAtLeast(0)
    }
    // Keep the same page in view when the layout switches between single and double.
    var lastPage by remember { mutableStateOf(startPage.coerceIn(0, source.pageCount - 1)) }
    val pagerState = rememberPagerState(initialPage = startSpread) { spreads.size }
    LaunchedEffect(spreads) {
        val target = spreads.indexOfFirst { lastPage in it }.coerceAtLeast(0)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    LaunchedEffect(pagerState, spreads) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { s ->
            val pages = spreads.getOrNull(s) ?: return@collect
            lastPage = pages.first()
            onProgress(pages.last(), source.pageCount)
        }
    }

    var chrome by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    ImmersiveMode(visibleBars = chrome || showSettings)

    val scope = rememberCoroutineScope()
    fun go(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, spreads.lastIndex)
        if (target != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(target) }
    }

    // Volume keys: down = next page, up = previous.
    DisposableEffect(settings.volumeKeys) {
        ReaderKeys.active = settings.volumeKeys
        onDispose { ReaderKeys.active = false }
    }
    LaunchedEffect(pagerState, spreads) {
        ReaderKeys.events.collect { go(it) }
    }

    var currentZoomed by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.roundToInt()
        val hPx = with(density) { maxHeight.toPx() }.roundToInt()

        HorizontalPager(
            state = pagerState,
            reverseLayout = rtl,
            beyondViewportPageCount = 1,
            userScrollEnabled = !currentZoomed,
            key = { spreads[it].first() * 2 + if (double) 1 else 0 },
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val zoom = rememberZoomState(index to double)
            val isCurrent = pagerState.currentPage == index
            LaunchedEffect(zoom.isZoomed, isCurrent) { if (isCurrent) currentZoomed = zoom.isZoomed }
            LaunchedEffect(pagerState.settledPage) { if (pagerState.settledPage != index) zoom.snapReset() }

            Box(
                Modifier
                    .fillMaxSize()
                    .turnEffect(pagerState, index, settings.turnStyle, rtl, double)
                    // Opaque like paper, so the page underneath never shows through the margins.
                    .background(FwipColors.ReaderBg)
            ) {
                Zoomable(
                    state = zoom,
                    onTap = { pos, size ->
                        val x = pos.x / size.width.coerceAtLeast(1)
                        when {
                            x < 0.28f -> go(if (rtl) 1 else -1)
                            x > 0.72f -> go(if (rtl) -1 else 1)
                            else -> chrome = !chrome
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Spread(
                        pages = spreads[index],
                        cache = cache,
                        rtl = rtl,
                        maxW = if (spreads[index].size == 2) wPx / 2 else wPx,
                        maxH = hPx,
                    )
                }
            }
        }

        // Pre-decode the next and previous spreads so turning never shows a spinner.
        LaunchedEffect(pagerState.settledPage, spreads, wPx, hPx) {
            val s = pagerState.settledPage
            for (n in listOf(s + 1, s - 1, s + 2)) {
                val pages = spreads.getOrNull(n) ?: continue
                val w = if (pages.size == 2) wPx / 2 else wPx
                pages.forEach { cache.get(it, w, hPx) }
            }
        }

        ReaderChrome(
            visible = chrome,
            comic = comic,
            pageLabel = spreads.getOrNull(pagerState.currentPage)?.let { p ->
                if (p.size == 2) "${p[0] + 1}-${p[1] + 1} de ${source.pageCount}" else "${p[0] + 1} de ${source.pageCount}"
            } ?: "",
            spreadIndex = pagerState.currentPage,
            spreadCount = spreads.size,
            rtl = rtl,
            onSeek = { scope.launch { pagerState.scrollToPage(it) } },
            onBack = onClose,
            onSettings = { showSettings = true },
        )
    }

    if (showSettings) {
        SettingsDialog(settings, onDismiss = { showSettings = false })
    }
}

/**
 * Page turn animation driven by the pager's scroll position.
 * Flip: the page you leave rotates over its spine like paper, revealing the next one underneath.
 * Slide: the next page slides over the current one, which sinks back slightly.
 */
private fun Modifier.turnEffect(
    pagerState: PagerState,
    index: Int,
    style: TurnStyle,
    rtl: Boolean,
    double: Boolean,
): Modifier = this
    .zIndex(
        run {
            // Flip: the page being turned away sits on top, then the current one, then the next.
            // Slide: the incoming page slides over the current one, which sits over the previous.
            val o = ((index - pagerState.currentPage) - pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
            if (style == TurnStyle.Flip && !double) (if (o < 0f) 2f else 1f - o) else (if (o > 0f) 2f else 1f + o)
        }
    )
    .graphicsLayer {
        val o = ((index - pagerState.currentPage) - pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
        val dir = if (rtl) -1f else 1f
        // Undo the pager's own sliding so we control where the page sits.
        val visual = o * size.width * dir
        cameraDistance = 14f * density * 160f
        if (style == TurnStyle.Flip && !double) {
            translationX = -visual
            if (o < 0f) {
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                rotationY = o * 180f * dir
                alpha = if (abs(o) < 0.5f) 1f else 0f
            } else {
                rotationY = 0f
                alpha = 1f
            }
        } else {
            if (o < 0f) {
                // Page being covered: stays put, sinks a little.
                translationX = -visual
                val t = abs(o)
                scaleX = 1f - 0.06f * t
                scaleY = 1f - 0.06f * t
                alpha = 1f - 0.5f * t
            } else {
                translationX = 0f
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
            }
        }
    }
    .drawWithContent {
        drawContent()
        val o = ((index - pagerState.currentPage) - pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
        if (o == 0f) return@drawWithContent
        if (style == TurnStyle.Flip && !double) {
            if (o < 0f) {
                // Light falls off as the page lifts away.
                val t = (abs(o) * 2f).coerceAtMost(1f)
                drawRect(
                    Brush.horizontalGradient(
                        if (rtl) listOf(Color.Black.copy(alpha = 0.05f * t), Color.Black.copy(alpha = 0.45f * t))
                        else listOf(Color.Black.copy(alpha = 0.45f * t), Color.Black.copy(alpha = 0.05f * t))
                    )
                )
            } else {
                // The page underneath is in the shadow of the one turning.
                drawRect(Color.Black.copy(alpha = 0.4f * o))
            }
        } else if (o > 0f) {
            // Shadow cast by the sliding page onto the one beneath.
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
                size = androidx.compose.ui.geometry.Size(w, size.height),
            )
        }
    }

@Composable
private fun Spread(pages: List<Int>, cache: PageCache, rtl: Boolean, maxW: Int, maxH: Int) {
    val ordered = if (rtl) pages.reversed() else pages
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ordered.forEachIndexed { i, page ->
            val align = when {
                ordered.size == 1 -> Alignment.Center
                i == 0 -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = align) {
                PageImage(page, cache, maxW, maxH)
                if (ordered.size == 2) {
                    // Soft shadow along the spine of a double page.
                    Canvas(Modifier.matchParentSize()) {
                        val w = 36.dp.toPx()
                        val atRight = i == 0
                        drawRect(
                            Brush.horizontalGradient(
                                if (atRight) listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)) else listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                                startX = if (atRight) size.width - w else 0f,
                                endX = if (atRight) size.width else w,
                            ),
                            topLeft = Offset(if (atRight) size.width - w else 0f, 0f),
                            size = androidx.compose.ui.geometry.Size(w, size.height),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageImage(index: Int, cache: PageCache, maxW: Int, maxH: Int) {
    var bitmap by remember(index, maxW, maxH) { mutableStateOf(cache.peek(index, maxW, maxH)) }
    var failed by remember(index, maxW, maxH) { mutableStateOf(false) }
    LaunchedEffect(index, maxW, maxH) {
        if (bitmap == null) {
            val b = cache.get(index, maxW, maxH)
            if (b == null) failed = true else bitmap = b
        }
    }
    val b = bitmap
    if (b == null && failed) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Não foi possível carregar a página ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = FwipColors.TextSoft,
                textAlign = TextAlign.Center,
            )
            cache.errorFor(index)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, textAlign = TextAlign.Center)
            }
        }
    } else if (b == null) {
        CircularProgressIndicator(Modifier.size(28.dp), color = FwipColors.TextMuted, strokeWidth = 2.dp)
    } else {
        val image = remember(b) { b.asImageBitmap() }
        Image(
            image,
            contentDescription = "Página ${index + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1), matchHeightConstraintsFirst = true),
        )
    }
}

@Composable
private fun ReaderChrome(
    visible: Boolean,
    comic: Comic,
    pageLabel: String,
    spreadIndex: Int,
    spreadCount: Int,
    rtl: Boolean,
    onSeek: (Int) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xD9121318))
                    .border(1.dp, FwipColors.Stroke, CircleShape)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.Filled.ArrowBack, "Voltar para a biblioteca", onBack, size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(comic.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pageLabel, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
                CircleIconButton(Icons.Filled.Settings, "Ajustes de leitura", onSettings, size = 44.dp)
            }
        }
        AnimatedVisibility(
            visible = visible && spreadCount > 1,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var preview by remember { mutableStateOf<Int?>(null) }
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 900.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xE6121318))
                    .border(1.dp, FwipColors.Stroke, CircleShape)
                    .padding(start = 24.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PageScrubber(
                    value = spreadIndex,
                    count = spreadCount,
                    rtl = rtl,
                    onPreview = { preview = it },
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(preview ?: spreadIndex) + 1} / $spreadCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = FwipColors.TextSoft,
                )
            }
        }
    }
}

/** Slim page scrubber: tap or drag anywhere on the track to jump. */
@Composable
private fun PageScrubber(
    value: Int,
    count: Int,
    rtl: Boolean,
    onPreview: (Int?) -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = (count - 1).coerceAtLeast(1)
    fun toIndex(f: Float) = ((if (rtl) 1f - f else f) * last).roundToInt().coerceIn(0, last)
    fun toFraction(i: Int) = (i.toFloat() / last).let { if (rtl) 1f - it else it }

    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: toFraction(value)

    BoxWithConstraints(
        modifier
            .height(44.dp)
            .pointerInput(count, rtl) {
                detectTapGestures { pos -> onSeek(toIndex((pos.x / size.width).coerceIn(0f, 1f))) }
            }
            .pointerInput(count, rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { pos -> drag = (pos.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        onPreview(toIndex(f))
                    },
                    onDragEnd = {
                        drag?.let { onSeek(toIndex(it)) }
                        drag = null
                        onPreview(null)
                    },
                    onDragCancel = {
                        drag = null
                        onPreview(null)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = if (drag != null) 22.dp else 16.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
        )
        Box(
            Modifier
                .align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(if (rtl) 1f - fraction else fraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(FwipColors.Amber)
        )
        Box(
            Modifier
                .offset(x = (maxWidth - thumb) * fraction)
                .size(thumb)
                .clip(CircleShape)
                .background(FwipColors.Amber)
        )
    }
}

@Composable
private fun SettingsDialog(settings: ReaderSettings, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF16171E))
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ajustes de leitura", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Filled.Close, "Fechar", onDismiss, size = 40.dp)
            }
            Choice("Virada de página", TurnStyle.entries, settings.turnStyle, { it.label }, settings::updateTurn)
            Choice("Páginas na tela", SpreadMode.entries, settings.spreadMode, { it.label }, settings::updateSpread)
            Toggle("Modo mangá", "Lê da direita para a esquerda", settings.mangaMode, settings::updateManga)
            Toggle("Teclas de volume", "Viram a página para frente e para trás", settings.volumeKeys, settings::updateVolume)
        }
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .clip(CircleShape)
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { o ->
                val on = o == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (on) FwipColors.Text else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onPick(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FwipColors.Amber, checkedThumbColor = FwipColors.AmberInk),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Hides the status and navigation bars while reading; a swipe from the edge brings them back. */
@Composable
private fun ImmersiveMode(visibleBars: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(visibleBars) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visibleBars) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
