package io.github.christiandonatti.fwip.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.christiandonatti.fwip.balloons.BalloonStore
import io.github.christiandonatti.fwip.balloons.Detection
import io.github.christiandonatti.fwip.balloons.panelOrder
import io.github.christiandonatti.fwip.balloons.readingOrder
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.data.ComicMeta
import io.github.christiandonatti.fwip.data.ComicSource
import io.github.christiandonatti.fwip.data.ComicSources
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Everything the page views need to draw a comic, fixed for a given layout. */
@Stable
class ReaderHost(
    val spreads: List<List<Int>>,
    val double: Boolean,
    val rtl: Boolean,
    val cache: PageCache,
    /** Size pages are decoded at: half the screen width per page in double mode. */
    val decodeW: Int,
    val decodeH: Int,
)

sealed interface NavCommand {
    data class Go(val delta: Int) : NavCommand
    data class Seek(val spread: Int) : NavCommand
}

private sealed interface Loaded {
    data object Loading : Loaded
    data class Ready(val source: ComicSource) : Loaded
    data class Failed(val message: String) : Loaded
}

private enum class PendingStop { First, Last }

enum class GuideKind { Panels, Balloons }

@Composable
fun ReaderScreen(
    comic: Comic,
    title: String,
    meta: ComicMeta?,
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
    // Capture the value: reading `loaded` inside onDispose would see the newer state and close
    // the source that was just opened.
    val current = loaded
    DisposableEffect(current) {
        onDispose { (current as? Loaded.Ready)?.source?.close() }
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
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(l.message, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted, textAlign = TextAlign.Center)
                PillButton("Voltar", onClose)
            }
            is Loaded.Ready -> Reader(comic, title, meta, l.source, startPage, settings, onClose, onProgress)
        }
    }
}

@Composable
private fun Reader(
    comic: Comic,
    title: String,
    meta: ComicMeta?,
    source: ComicSource,
    startPage: Int,
    settings: ReaderSettings,
    onClose: () -> Unit,
    onProgress: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val cache = remember(source) { PageCache(source) }
    DisposableEffect(cache) { onDispose { cache.clear() } }

    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val double = when (settings.spreadMode) {
        SpreadMode.Single -> false
        SpreadMode.Double -> true
        SpreadMode.Auto -> landscape && config.smallestScreenWidthDp >= 600
    }
    // Manga flagged in ComicInfo.xml reads right to left whatever the global setting says.
    val rtl = settings.mangaMode || meta?.rightToLeft == true
    val spreads = remember(source.pageCount, double) { buildSpreads(source.pageCount, double) }

    // The first page in view, kept across layout changes (rotation, single/double).
    var lastPage by rememberSaveable { mutableIntStateOf(startPage.coerceIn(0, source.pageCount - 1)) }

    var chrome by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    ImmersiveMode(visibleBars = chrome || showSettings)

    val scope = rememberCoroutineScope()
    val commands = remember { MutableSharedFlow<NavCommand>(extraBufferCapacity = 8) }
    val store = remember { BalloonStore.get(context) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val host = remember(spreads, double, rtl, cache, wPx, hPx) {
            ReaderHost(spreads, double, rtl, cache, if (double) wPx / 2 else wPx, hPx)
        }
        val startSpread = remember(host) { spreads.indexOfFirst { lastPage in it }.coerceAtLeast(0) }
        var spread by remember(host) { mutableIntStateOf(startSpread) }
        var zoom by remember(host) { mutableStateOf<ZoomState?>(null) }

        // ---------- Guided reading: panel by panel, or balloon by balloon ----------
        var guide by rememberSaveable { mutableStateOf<GuideKind?>(null) }
        var stop by remember { mutableIntStateOf(-1) }
        var pending by remember { mutableStateOf<PendingStop?>(null) }

        /** What the model and the gutter cutter found on each page of the current spread; null while working. */
        val analysis by produceState<List<Pair<PlacedPage, List<Detection>>>?>(null, host, spread) {
            value = null
            val pages = spreads[spread]
            // Outside guided reading this only serves double taps, so let page turns finish first.
            if (guide == null) delay(700)
            val found = store.detectionsFor(comic.id, pages) { p -> cache.get(p, host.decodeW, host.decodeH) }
            val placed = SpreadLayout.place(pages, double, rtl, wPx.toFloat(), hPx.toFloat()) { p ->
                cache.peek(p, host.decodeW, host.decodeH)?.let { IntSize(it.width, it.height) }
            }
            value = placed.map { it to found[it.page].orEmpty() }
        }

        fun toReader(pp: PlacedPage, r: android.graphics.RectF) = Rect(
            pp.rect.left + r.left * pp.rect.width,
            pp.rect.top + r.top * pp.rect.height,
            pp.rect.left + r.right * pp.rect.width,
            pp.rect.top + r.bottom * pp.rect.height,
        )

        val balloons = remember(analysis, rtl) {
            analysis?.flatMap { (pp, d) -> readingOrder(d, rtl).map { toReader(pp, it) } }
        }
        // A page without detectable panels is a single stop showing the whole page.
        val panels = remember(analysis, rtl) {
            analysis?.flatMap { (pp, d) -> panelOrder(d, rtl).map { toReader(pp, it) }.ifEmpty { listOf(pp.rect) } }
        }
        val stops: List<Rect>? = when (guide) {
            GuideKind.Panels -> panels
            GuideKind.Balloons -> balloons
            null -> null
        }

        // While reading guided, look ahead so the next page is ready instantly.
        LaunchedEffect(guide, spread, host) {
            if (guide == null) return@LaunchedEffect
            spreads.getOrNull(spread + 1)?.let { next ->
                store.detectionsFor(comic.id, next) { p -> cache.get(p, host.decodeW, host.decodeH) }
            }
        }

        fun zoomToStop(i: Int) {
            val list = stops ?: return
            val z = zoom ?: return
            if (i !in list.indices) return
            stop = i
            scope.launch {
                if (guide == GuideKind.Panels) z.animateToRect(list[i], fill = 0.97f, maxScale = 3.5f)
                else z.animateToRect(list[i])
            }
        }

        fun showWholePage() {
            stop = -1
            zoom?.let { z -> scope.launch { z.animateTo(1f) } }
        }

        fun nextStop() {
            val list = stops ?: return
            if (stop + 1 < list.size) {
                zoomToStop(stop + 1)
            } else if (spread < spreads.lastIndex) {
                pending = PendingStop.First
                showWholePage()
                commands.tryEmit(NavCommand.Go(1))
            }
        }

        fun prevStop() {
            when {
                stop > 0 -> zoomToStop(stop - 1)
                stop == 0 -> showWholePage()
                spread > 0 -> {
                    pending = PendingStop.Last
                    commands.tryEmit(NavCommand.Go(-1))
                }
            }
        }

        fun setGuide(kind: GuideKind?) {
            guide = kind
            chrome = false
            stop = -1
            if (kind != null) {
                pending = PendingStop.First
            } else {
                pending = null
                showWholePage()
            }
        }

        // After a turn (or when guided reading starts), go straight to the first or last stop.
        LaunchedEffect(stops, zoom, pending) {
            val list = stops ?: return@LaunchedEffect
            val p = pending ?: return@LaunchedEffect
            if (zoom == null) return@LaunchedEffect
            pending = null
            stop = -1
            if (list.isEmpty()) return@LaunchedEffect
            delay(120)
            zoomToStop(if (p == PendingStop.First) 0 else list.lastIndex)
        }

        // ---------- Navigation & input ----------
        val onSpreadChanged: (Int) -> Unit = { s ->
            if (s != spread) {
                stop = -1
                if (guide != null && pending == null) pending = PendingStop.First
            }
            spread = s
            spreads.getOrNull(s)?.let { pages ->
                lastPage = pages.first()
                onProgress(pages.last(), source.pageCount)
            }
        }

        val onTap: (Offset, IntSize, ZoomState) -> Unit = { pos, size, z ->
            val x = pos.x / size.width.coerceAtLeast(1)
            if (guide != null) {
                // Tap anywhere to move on; the edge where reading comes from goes back.
                val back = if (rtl) x > 0.75f else x < 0.25f
                val list = stops
                val layoutPos = z.toLayout(pos)
                val hit = if (guide == GuideKind.Balloons) list?.indexOfFirst { it.inflate(12f).contains(layoutPos) } ?: -1 else -1
                when {
                    back -> prevStop()
                    hit >= 0 && hit != stop -> zoomToStop(hit)
                    else -> nextStop()
                }
            } else {
                when {
                    x < 0.28f -> commands.tryEmit(NavCommand.Go(if (rtl) 1 else -1))
                    x > 0.72f -> commands.tryEmit(NavCommand.Go(if (rtl) -1 else 1))
                    else -> chrome = !chrome
                }
            }
        }

        // Double tap on a balloon zooms right into it; anywhere else it is a normal zoom.
        val onDoubleTap: (Offset, ZoomState) -> Boolean = handler@{ pos, z ->
            if (z.isZoomed) return@handler false
            val list = balloons ?: return@handler false
            val layoutPos = z.toLayout(pos)
            val hit = list.indexOfFirst { it.inflate(16f).contains(layoutPos) }
            if (hit < 0) return@handler false
            if (guide == GuideKind.Balloons) stop = hit
            scope.launch { z.animateToRect(list[hit]) }
            true
        }

        // Volume keys: down = forward, up = back. In balloon mode they step through balloons.
        DisposableEffect(settings.volumeKeys) {
            ReaderKeys.active = settings.volumeKeys
            onDispose { ReaderKeys.active = false }
        }
        LaunchedEffect(host) {
            ReaderKeys.events.collect { d ->
                if (guide != null) {
                    if (d > 0) nextStop() else prevStop()
                } else {
                    commands.tryEmit(NavCommand.Go(d))
                }
            }
        }

        key(host, settings.turnStyle) {
            if (settings.turnStyle == TurnStyle.Curl) {
                CurlReader(
                    host = host,
                    startSpread = spread,
                    commands = commands,
                    onSpreadChanged = onSpreadChanged,
                    onZoomState = { zoom = it },
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                )
            } else {
                PagerReader(
                    host = host,
                    style = settings.turnStyle,
                    startSpread = spread,
                    commands = commands,
                    onSpreadChanged = onSpreadChanged,
                    onZoomState = { zoom = it },
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                )
            }
        }

        // Spotlight on the panel or balloon being read.
        val spotAlpha by animateFloatAsState(if (guide != null && stop >= 0) 1f else 0f, label = "spot")
        val target = stops?.getOrNull(stop)
        val z = zoom
        if (target != null && z != null) {
            GuideSpotlight(
                Rect(z.toScreen(target.topLeft), z.toScreen(target.bottomRight)),
                spotAlpha,
                panel = guide == GuideKind.Panels,
            )
        }

        ReaderChrome(
            visible = chrome,
            title = title,
            pageLabel = spreads.getOrNull(spread)?.let { p ->
                if (p.size == 2) "${p[0] + 1}-${p[1] + 1} de ${source.pageCount}" else "${p[0] + 1} de ${source.pageCount}"
            } ?: "",
            spreadIndex = spread,
            spreadCount = spreads.size,
            rtl = rtl,
            guide = guide,
            onSeek = { commands.tryEmit(NavCommand.Seek(it)) },
            onBack = onClose,
            onSettings = { showSettings = true },
            onGuide = { kind -> setGuide(if (guide == kind) null else kind) },
        )

        AnimatedVisibility(
            visible = guide != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val list = stops
            val panelsMode = guide == GuideKind.Panels
            GuideBar(
                label = when {
                    list == null -> ""
                    list.isEmpty() -> if (panelsMode) "Página inteira" else "Nenhum balão nesta página"
                    stop >= 0 -> (if (panelsMode) "Quadro" else "Balão") + " ${stop + 1} de ${list.size}"
                    panelsMode -> if (list.size == 1) "Página inteira" else "${list.size} quadros"
                    else -> if (list.size == 1) "1 balão" else "${list.size} balões"
                },
                busy = list == null,
                busyLabel = if (panelsMode) "Procurando quadros" else "Procurando balões",
                rtl = rtl,
                onPrev = ::prevStop,
                onNext = ::nextStop,
                onExit = { setGuide(null) },
            )
        }
    }

    if (showSettings) {
        SettingsDialog(settings, onDismiss = { showSettings = false })
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
