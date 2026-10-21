package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
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
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.data.ComicSource
import io.github.christiandonatti.fwip.data.ComicSources
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
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
    /** Pages that are a double-page image and stand alone across the whole screen. */
    val wide: Set<Int> = emptySet(),
) {
    fun isWide(page: Int) = page in wide

    /** Decode width for a page: wide pages in double mode get the full screen width. */
    fun wFor(page: Int) = if (double && page in wide) decodeW * 2 else decodeW
}

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
    next: LibraryItem?,
    onOpenNext: () -> Unit,
    startPage: Int,
    onClose: () -> Unit,
    onProgress: (page: Int, pageCount: Int) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { ReaderSettings(context) }

    val loaded by produceState<Loaded>(Loaded.Loading, comic.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { ComicSources.open(context, comic) }.fold(
                onSuccess = { src -> if (src.pageCount > 0) Loaded.Ready(src) else { src.close(); Loaded.Failed(str(R.string.error_no_pages)) } },
                onFailure = { Loaded.Failed(str(R.string.error_open_file) + "\n" + (it.message ?: it.javaClass.simpleName)) },
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
    KeepScreenOn(settings.keepScreenOn)
    ReaderComfort(settings)

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
                PillButton(str(R.string.back), onClose)
            }
            is Loaded.Ready -> Reader(comic, title, meta, next, onOpenNext, l.source, startPage, settings, onClose, onProgress)
        }
    }
}

@Composable
private fun Reader(
    comic: Comic,
    title: String,
    meta: ComicMeta?,
    next: LibraryItem?,
    onOpenNext: () -> Unit,
    source: ComicSource,
    startPage: Int,
    settings: ReaderSettings,
    onClose: () -> Unit,
    onProgress: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val cache = remember(source, settings.cropMargins) { PageCache(source, settings.cropMargins, PageCache.budgetFor(context)) }
    DisposableEffect(cache) { onDispose { cache.clear() } }

    // Webtoons: pages much taller than wide always scroll vertically.
    var strips by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        if (comic.remote != null) return@LaunchedEffect
        strips = withContext(Dispatchers.IO) {
            val sizes = (0 until minOf(3, source.pageCount)).mapNotNull { runCatching { source.pageSize(it) }.getOrNull() }
            sizes.isNotEmpty() && sizes.all { it.height > it.width * 2.2f }
        }
    }
    val scroll = settings.turnStyle == TurnStyle.Scroll || strips

    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val double = !scroll && when (settings.spreadMode) {
        SpreadMode.Single -> false
        SpreadMode.Double -> true
        SpreadMode.Auto -> landscape && config.smallestScreenWidthDp >= 600
    }
    // Manga flagged in ComicInfo.xml reads right to left whatever the global setting says.
    val rtl = settings.mangaMode || meta?.rightToLeft == true
    // Wide (double-page) images: known from ComicInfo.xml and earlier visits, then found as pages are measured.
    val wideStore = remember { WidePages(context) }
    var wide by remember(source) { mutableStateOf(wideStore.load(comic.id).first + meta?.doublePages.orEmpty()) }
    LaunchedEffect(source) {
        val (known, done) = wideStore.load(comic.id)
        // Measuring every page of a streamed book would download all of it up front.
        if (done || comic.remote != null) return@LaunchedEffect
        val found = (known + meta?.doublePages.orEmpty()).toMutableSet()
        withContext(Dispatchers.IO) {
            for (i in 0 until source.pageCount) {
                if (!isActive) return@withContext
                val size = runCatching { source.pageSize(i) }.getOrNull() ?: continue
                if (size.width > size.height * 1.1f && found.add(i)) {
                    val snapshot = found.toSet()
                    withContext(Dispatchers.Main) { wide = snapshot }
                }
            }
            if (isActive) wideStore.save(comic.id, found, done = true)
        }
    }
    val spreads = remember(source.pageCount, double, wide) { buildSpreads(source.pageCount, double, wide) }

    // The first page in view, kept across layout changes (rotation, single/double).
    var lastPage by rememberSaveable { mutableIntStateOf(startPage.coerceIn(0, source.pageCount - 1)) }

    var chrome by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    val bookmarkStore = remember { io.github.christiandonatti.fwip.data.BookmarkStore.get(context) }
    var marks by remember(comic.id) { mutableStateOf(bookmarkStore.pages(comic.id)) }
    ImmersiveMode(visibleBars = chrome || showSettings)

    val scope = rememberCoroutineScope()
    val commands = remember { MutableSharedFlow<NavCommand>(extraBufferCapacity = 8) }
    val store = remember { BalloonStore.get(context) }
    // Detections are relative to the page as shown, so cropped and uncropped pages are kept apart.
    val storeKey = if (settings.cropMargins) comic.id + "#crop" else comic.id

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val host = remember(spreads, double, rtl, cache, wPx, hPx, wide) {
            ReaderHost(spreads, double, rtl, cache, if (double) wPx / 2 else wPx, hPx, wide)
        }
        val startSpread = remember(host) { spreads.indexOfFirst { lastPage in it }.coerceAtLeast(0) }
        var spread by remember(host) { mutableIntStateOf(startSpread) }
        var zoom by remember { mutableStateOf<ZoomState?>(null) }

        var showEnd by remember { mutableStateOf(false) }

        // ---------- Guided reading: panel by panel, or balloon by balloon ----------
        var guide by rememberSaveable { mutableStateOf<GuideKind?>(null) }
        LaunchedEffect(scroll) { if (scroll) guide = null }
        var stop by remember { mutableIntStateOf(-1) }
        var pending by remember { mutableStateOf<PendingStop?>(null) }

        /** What the model and the gutter cutter found on each page of the current spread; null while working. */
        val analysis by produceState<List<Pair<PlacedPage, List<Detection>>>?>(null, host, spread) {
            value = null
            val pages = spreads[spread]
            // Outside guided reading this only serves double taps, so let page turns finish first.
            if (guide == null) delay(700)
            val found = store.detectionsFor(storeKey, pages) { p -> cache.get(p, host.wFor(p), host.decodeH) }
            val placed = SpreadLayout.place(pages, double, rtl, wPx.toFloat(), hPx.toFloat(), isWide = host::isWide, sizeOf = { p ->
                cache.peek(p, host.wFor(p), host.decodeH)?.let { IntSize(it.width, it.height) }
            })
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
                store.detectionsFor(storeKey, next) { p -> cache.get(p, host.wFor(p), host.decodeH) }
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
            } else {
                showEnd = true
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
            stop = -1
            if (list.isEmpty()) {
                pending = null
                return@LaunchedEffect
            }
            delay(120)
            // Zoom first, then clear: clearing restarts this effect (pending is one of its keys),
            // which would cancel it before the zoom if done earlier.
            zoomToStop(if (p == PendingStop.First) 0 else list.lastIndex)
            pending = null
        }

        // ---------- Navigation & input ----------
        val feedback = rememberTurnFeedback()
        val turnFeedback = { feedback.turn(settings.turnHaptic, settings.turnSound) }
        val onSpreadChanged: (Int) -> Unit = { s ->
            // The paper reader announces its turns as they start; the others once they land.
            if (!scroll && settings.turnStyle != TurnStyle.Curl && spreads.getOrNull(s)?.firstOrNull() != lastPage) turnFeedback()
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
                val side = when {
                    x < 0.28f -> -1
                    x > 0.72f -> 1
                    else -> 0
                }
                when (val move = settings.tapZones.move(side, rtl)) {
                    0 -> chrome = !chrome
                    else -> commands.tryEmit(NavCommand.Go(move))
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

        val flatZoom = remember { ZoomState() }
        // The paper reader follows layout changes itself; the others start over with them.
        val readerKey: Any = if (scroll || settings.turnStyle != TurnStyle.Curl) host else listOf(double, rtl, cache, wPx, hPx)
        key(readerKey, settings.turnStyle, scroll) {
            if (scroll) {
                ScrollReader(
                    host = host,
                    startSpread = spread,
                    commands = commands,
                    onSpreadChanged = onSpreadChanged,
                    onTap = { pos, size -> onTap(pos, size, flatZoom) },
                    onEnd = { showEnd = true },
                )
            } else if (settings.turnStyle == TurnStyle.Curl) {
                CurlReader(
                    host = host,
                    startSpread = spread,
                    commands = commands,
                    onSpreadChanged = onSpreadChanged,
                    onZoomState = { zoom = it },
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                    onEnd = { showEnd = true },
                    onTurn = turnFeedback,
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
                    onEnd = { showEnd = true },
                )
            }
        }

        // Warm night filter over the pages (the controls stay true to colour).
        if (settings.warmth > 0f) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    androidx.compose.ui.graphics.Color(0xFFFFA24A).copy(alpha = 0.45f * settings.warmth),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Multiply,
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
                if (p.size == 2) str(R.string.pages_x_y_of_n, p[0] + 1, p[1] + 1, source.pageCount) else str(R.string.page_x_of_n, p[0] + 1, source.pageCount)
            } ?: "",
            spreadIndex = spread,
            spreadCount = spreads.size,
            rtl = rtl,
            guide = guide,
            onSeek = { commands.tryEmit(NavCommand.Seek(it)) },
            onBack = onClose,
            onSettings = { showSettings = true },
            onGuide = { kind -> setGuide(if (guide == kind) null else kind) },
            bookmarked = spreads.getOrNull(spread)?.any { it in marks } == true,
            onBookmark = {
                val pages = spreads.getOrNull(spread).orEmpty()
                marks = if (pages.any { it in marks }) marks - pages.toSet() else marks + pages.first()
                bookmarkStore.set(comic.id, marks)
            },
            onPages = { showPages = true },
            guidedAvailable = !scroll,
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
                    list.isEmpty() -> if (panelsMode) str(R.string.whole_page) else str(R.string.no_balloons)
                    stop >= 0 -> str(if (panelsMode) R.string.panel_x_of_y else R.string.balloon_x_of_y, stop + 1, list.size)
                    panelsMode -> if (list.size == 1) str(R.string.whole_page) else str(R.string.n_panels, list.size)
                    else -> if (list.size == 1) str(R.string.one_balloon) else str(R.string.n_balloons, list.size)
                },
                busy = list == null,
                busyLabel = if (panelsMode) str(R.string.finding_panels) else str(R.string.finding_balloons),
                rtl = rtl,
                onPrev = ::prevStop,
                onNext = ::nextStop,
                onExit = { setGuide(null) },
            )
        }

        if (showPages) {
            PageGrid(
                pageCount = source.pageCount,
                cache = cache,
                current = spreads.getOrNull(spread)?.first() ?: 0,
                bookmarks = marks,
                rtl = rtl,
                onPick = { page ->
                    showPages = false
                    chrome = false
                    val target = spreads.indexOfFirst { page in it }
                    if (target >= 0) commands.tryEmit(NavCommand.Seek(target))
                },
                onDismiss = { showPages = false },
            )
        }

        if (showEnd) {
            EndCard(
                title = title,
                next = next,
                onNext = { showEnd = false; onOpenNext() },
                onLibrary = onClose,
                onDismiss = { showEnd = false },
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
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Brightness and rotation lock apply only while the reader is open. */
@Composable
private fun ReaderComfort(settings: ReaderSettings) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(settings.brightness) {
        val attrs = activity.window.attributes
        attrs.screenBrightness = if (settings.brightness < 0f) {
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            settings.brightness.coerceIn(0.01f, 1f)
        }
        activity.window.attributes = attrs
        onDispose {
            val a = activity.window.attributes
            a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = a
        }
    }
    DisposableEffect(settings.rotation) {
        activity.requestedOrientation = when (settings.rotation) {
            ScreenRotation.Auto -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenRotation.Portrait -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenRotation.Landscape -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

/** Remembers which pages of each comic are wide double-page images, so later visits lay out instantly. */
private class WidePages(context: Context) {
    private val dir = java.io.File(context.filesDir, "wide").apply { mkdirs() }

    private fun file(id: String) = java.io.File(
        dir,
        java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) } + ".txt",
    )

    /** Known wide pages, and whether every page has already been measured. */
    fun load(id: String): Pair<Set<Int>, Boolean> = runCatching {
        val lines = file(id).readLines()
        val pages = lines.firstOrNull().orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        pages to (lines.getOrNull(1) == "done")
    }.getOrDefault(emptySet<Int>() to false)

    fun save(id: String, pages: Set<Int>, done: Boolean) {
        runCatching { file(id).writeText(pages.sorted().joinToString(",") + "\n" + (if (done) "done" else "")) }
    }
}
