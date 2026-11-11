@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
    org.readium.r2.shared.DelicateReadiumApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package io.github.christiandonatti.fwip.book

import io.github.christiandonatti.fwip.reader.toggleRotationLock
import io.github.christiandonatti.fwip.ui.tick
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.BookSources
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.reader.InkFlash
import io.github.christiandonatti.fwip.reader.InkLook
import io.github.christiandonatti.fwip.reader.LightSettings
import io.github.christiandonatti.fwip.reader.ReaderKeys
import io.github.christiandonatti.fwip.reader.ReadingLightLayer
import io.github.christiandonatti.fwip.reader.ReadingStatus
import io.github.christiandonatti.fwip.reader.ReaderSettings
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.CircleIconButton
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.PillStyle
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign

/** A book opened by Readium, with its positions (about a page of text each) per chapter file. */
class OpenedBook(val publication: Publication, val positions: List<List<Locator>>) {
    val total: Int = positions.sumOf { it.size }.coerceAtLeast(1)
    /** Index of the first position of each reading-order item. */
    val starts: IntArray = IntArray(positions.size).also { a -> var n = 0; positions.forEachIndexed { i, l -> a[i] = n; n += l.size } }
}

private sealed interface BookLoad {
    data object Loading : BookLoad
    data class Ready(val book: OpenedBook) : BookLoad
    data class Failed(val message: String, val detail: String? = null, val canRetry: Boolean = false) : BookLoad
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Opens a book file with Readium (after converting it to EPUB if needed). */
suspend fun openBook(context: Context, comic: Comic): OpenedBook = withContext(Dispatchers.IO) {
    val file = BookSources.epubFor(context, comic)
    val http = DefaultHttpClient()
    val retriever = AssetRetriever(context.contentResolver, http)
    val opener = PublicationOpener(DefaultPublicationParser(context, http, retriever, pdfFactory = null))
    val asset = retriever.retrieve(file).getOrElse { throw java.io.IOException(it.message) }
    val publication = opener.open(asset, allowUserInteraction = false).getOrElse { throw java.io.IOException(it.message) }
    OpenedBook(publication, publication.positionsByReadingOrder())
}

/** Page and text colours for the current book theme, made e-ink-like when the e-ink look is on. */
internal fun pageColors(theme: BookTheme, ink: InkLook): Pair<Int, Int> = when {
    ink == InkLook.Off -> theme.background to theme.text
    theme.dark -> 0xFF1B1B1C.toInt() to 0xFFCFCCC4.toInt()
    else -> 0xFFE6E3DA.toInt() to 0xFF1E1E21.toInt()
}

internal fun preferencesFor(s: BookSettings, ink: InkLook, spread: Boolean? = null): EpubPreferences {
    val (bg, fg) = pageColors(s.theme, ink)
    val family = when (s.font) {
        BookFontKind.Original -> null
        BookFontKind.Literata -> FontFamily("Literata")
        BookFontKind.Bitter -> FontFamily("Bitter")
        BookFontKind.Atkinson -> FontFamily("Atkinson Hyperlegible")
        BookFontKind.Serif -> FontFamily.SERIF
        BookFontKind.Sans -> FontFamily.SANS_SERIF
        BookFontKind.Dyslexic -> FontFamily.OPEN_DYSLEXIC
        BookFontKind.Custom -> s.customFont?.let { FontFamily("Fwip user " + it) }
    }
    return EpubPreferences(
        backgroundColor = ReadiumColor(bg),
        textColor = ReadiumColor(fg),
        fontFamily = family,
        fontSize = s.fontSize.toDouble(),
        fontWeight = if (s.bold) 1.35 else null,
        lineHeight = if (s.publisherStyles) null else s.lineHeight.toDouble(),
        pageMargins = s.margins.toDouble(),
        textAlign = if (s.publisherStyles) null else if (s.justify) ReadiumTextAlign.JUSTIFY else ReadiumTextAlign.START,
        hyphens = if (s.publisherStyles) null else s.hyphens,
        // When pages turn like paper the turner must know where the spine is, so "automatic" is decided here.
        columnCount = when {
            spread != null -> if (spread) ColumnCount.TWO else ColumnCount.ONE
            s.columns == BookColumns.One -> ColumnCount.ONE
            s.columns == BookColumns.Two -> ColumnCount.TWO
            else -> ColumnCount.AUTO
        },
        publisherStyles = s.publisherStyles,
        scroll = s.turn == BookTurn.Scroll && !s.book3d,
    )
}

/** The navigator's configuration: Fwip's fonts (shipped and the user's), the selection menu. */
private fun navigatorConfiguration(context: Context, selection: ActionMode.Callback, twoColumns: Boolean): EpubNavigatorFragment.Configuration =
    EpubNavigatorFragment.Configuration {
        servedAssets = listOf("fonts/.*")
        // Readium CSS only splits into two columns on views wider than 60em. The 3D book's pages are
        // often narrower than that, so its two pages are asked for directly.
        if (twoColumns) {
            readiumCssRsProperties = org.readium.r2.navigator.epub.css.RsProperties(
                colCount = org.readium.r2.navigator.epub.css.ColCount.TWO,
                colWidth = org.readium.r2.navigator.epub.css.Length.Px(10.0),
            )
        }
        selectionActionModeCallback = selection
        fun shipped(family: String, file: String) = addFontFamilyDeclaration(FontFamily(family)) {
            addFontFace { addSource("fonts/$file-latin-400-normal.woff2"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.NORMAL) }
            addFontFace { addSource("fonts/$file-latin-400-italic.woff2"); setFontStyle(FontStyle.ITALIC); setFontWeight(FontWeight.NORMAL) }
            addFontFace { addSource("fonts/$file-latin-700-normal.woff2"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.BOLD) }
            addFontFace { addSource("fonts/$file-latin-700-italic.woff2"); setFontStyle(FontStyle.ITALIC); setFontWeight(FontWeight.BOLD) }
        }
        shipped("Literata", "literata")
        shipped("Bitter", "bitter")
        shipped("Atkinson Hyperlegible", "atkinson-hyperlegible")
        // The user's own fonts, inlined (web views cannot read the app's private files).
        UserFonts.list(context).filter { it.length() < (3 shl 20) }.forEach { f ->
            val data = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
            val url = AbsoluteUrl("data:" + UserFonts.mediaType(f) + ";base64," + data) ?: return@forEach
            addFontFamilyDeclaration(FontFamily("Fwip user " + f.name)) {
                addFontFace { addSource(url) }
            }
        }
    }

/** The book reader: Readium's EPUB navigator under Fwip's own controls, light and panels. */
@Composable
fun BookReaderScreen(
    comic: Comic,
    title: String,
    author: String?,
    startPage: Int,
    onClose: () -> Unit,
    onProgress: (page: Int, pageCount: Int) -> Unit,
    coverPath: String? = null,
    next: io.github.christiandonatti.fwip.data.LibraryItem? = null,
    onOpenNext: () -> Unit = {},
    seriesNote: String? = null,
) {
    val context = LocalContext.current
    val light = remember { LightSettings.get(context) }
    val settings = remember { BookSettings.get(context) }
    BackHandler(onBack = onClose)
    var attempt by remember(comic.id) { mutableStateOf(0) }
    val loaded by produceState<BookLoad>(BookLoad.Loading, comic.id, attempt) {
        value = BookLoad.Loading
        value = runCatching { openBook(context, comic) }.fold(
            { BookLoad.Ready(it) },
            { e ->
                val f = io.github.christiandonatti.fwip.ui.friendlyError(e, comic)
                // A Kindle book with DRM says so in its own words.
                if (e.message?.contains(str(R.string.mobi_drm)) == true) BookLoad.Failed(e.message!!, null, false)
                else BookLoad.Failed(f.message, f.detail, f.canRetry)
            },
        )
    }

    val current = loaded
    DisposableEffect(current) { onDispose { (current as? BookLoad.Ready)?.book?.publication?.close() } }
    val (bg, fg) = pageColors(settings.theme, light.ink)
    Box(Modifier.fillMaxSize().background(Color(bg))) {
        when (val l = loaded) {
            BookLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(fg))
            is BookLoad.Failed -> Column(
                Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color(fg), textAlign = TextAlign.Center)
                Text(l.message, style = MaterialTheme.typography.bodyLarge, color = Color(fg).copy(alpha = 0.8f), textAlign = TextAlign.Center)
                l.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(fg).copy(alpha = 0.55f), textAlign = TextAlign.Center, maxLines = 3) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (l.canRetry) PillButton(str(R.string.try_again), { attempt++ }, style = PillStyle.Amber)
                    PillButton(str(R.string.back), onClose, style = if (l.canRetry) PillStyle.Ghost else PillStyle.Solid)
                }
            }
            is BookLoad.Ready -> BookReader(comic, title, author, l.book, startPage, settings, light, onClose, onProgress, coverPath, next, onOpenNext, seriesNote)
        }
    }
}

@Composable
private fun BookReader(
    comic: Comic,
    title: String,
    author: String?,
    book: OpenedBook,
    startPage: Int,
    settings: BookSettings,
    light: LightSettings,
    onClose: () -> Unit,
    onProgress: (Int, Int) -> Unit,
    coverPath: String?,
    next: io.github.christiandonatti.fwip.data.LibraryItem?,
    onOpenNext: () -> Unit,
    seriesNote: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Past the last page: the end of the book, and what to read next.
    var showEnd by remember { mutableStateOf(false) }
    val turner = remember { PageTurner(scope) }
    val notes = remember(comic.id) { BookNotes.load(context, comic.id) }
    var notesVersion by remember { mutableIntStateOf(0) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var locator by remember { mutableStateOf<Locator?>(null) }
    var chrome by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<BookPanel?>(null) }
    var editing by remember { mutableStateOf<BookHighlight?>(null) }
    val speaker = remember { BookSpeaker(context, scope) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }
    val publication = book.publication
    val language = publication.metadata.languages.firstOrNull()

    // Where to start: the exact place saved last time, else the page the library knows.
    val initial = remember {
        notes.lastLocator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
            ?: book.positions.flatten().getOrNull(startPage)
    }

    val selection = remember {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                turner.selecting = true
                menu.add(0, 1, 0, str(R.string.book_highlight))
                menu.add(0, 2, 1, str(R.string.book_note))
                menu.add(0, 3, 2, str(R.string.book_copy))
                menu.add(0, 4, 3, str(R.string.book_define))
                menu.add(0, 5, 4, str(R.string.book_share))
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val nav = navigator ?: return false
                scope.launch {
                    val sel = nav.currentSelection() ?: return@launch
                    val text = sel.locator.text.highlight.orEmpty().trim()
                    when (item.itemId) {
                        1, 2 -> {
                            val h = BookHighlight(
                                id = java.util.UUID.randomUUID().toString(),
                                locator = sel.locator.toJSON().toString(),
                                text = text,
                                color = HIGHLIGHT_COLORS.first(),
                                note = "",
                                chapter = chapterTitle(publication, sel.locator).orEmpty(),
                                progression = sel.locator.locations.totalProgression ?: 0.0,
                                created = System.currentTimeMillis(),
                            )
                            notes.highlights += h
                            notes.save()
                            notesVersion++
                            if (item.itemId == 2) editing = h
                        }
                        3 -> {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText(title, text))
                        }
                        4 -> runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                                        .putExtra(Intent.EXTRA_PROCESS_TEXT, text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
                                    str(R.string.book_define),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        5 -> runCatching {
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "“$text”\n\n$title"), null)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    nav.clearSelection()
                }
                mode.finish()
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode) { turner.selecting = false }
        }
    }

    val ink = light.ink
    val reduceMotion = remember { io.github.christiandonatti.fwip.ui.A11y.reduceMotion(context) }
    val book3d = settings.book3d
    ImmersiveBars(visible = chrome || panel != null)
    val feedback = io.github.christiandonatti.fwip.reader.rememberTurnFeedback()
    val reader = remember { ReaderSettings.get(context) }
    turner.onTurned = { feedback.turn(reader.turnHaptic, reader.turnSound, reader.soundKind) }
    turner.enabled = (book3d || settings.turn == BookTurn.Paper) && !reduceMotion
    // Ambient sound of the scene, only while reading in 3D and the app is in front.
    val ambient = remember { io.github.christiandonatti.fwip.book.scene.AmbientSound() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) resumed = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) resumed = false
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); ambient.stop() }
    }
    ambient.volume = settings.ambientVolume
    ambient.duck = speaker.playing
    LaunchedEffect(book3d, settings.ambientSound, settings.scene, resumed) {
        if (book3d && settings.ambientSound && resumed) ambient.play(settings.scene.sound) else ambient.stop()
    }
    val cover = remember(coverPath) { coverPath?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() } }
    val board = remember(cover, settings.scene) { boardColor(cover, Color(settings.scene.cover)) }
    var pagePainted by remember { mutableStateOf(false) }
    LaunchedEffect(navigator) {
        if (navigator == null || pagePainted) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        turner.awaitPainted(6000)
        pagePainted = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.statusBarsIgnoringVisibility.getTop(density).toFloat()
        val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toFloat()
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val spread = when {
            book3d -> wide
            settings.columns == BookColumns.One -> false
            settings.columns == BookColumns.Two -> true
            else -> wide
        }
        val fraction = locator?.locations?.totalProgression?.toFloat() ?: 0f
        val bookPage = bookPageRect(wPx, hPx, topInset + with(density) { 22.dp.toPx() }, bottomInset + with(density) { 48.dp.toPx() }, settings.bookScale, spread)
        val edition = settings.bookEdition.resolve(comic = false, pages = book.total)
        val shape = remember(bookPage, spread, edition, (fraction * 200).toInt()) { BookShape(bookPage, spread, edition, fraction, book.total) }
        val sceneLight = if (settings.sceneLight) SceneLight.of(settings.scene) else SceneLight.Neutral.copy(fromDegrees = SceneLight.of(settings.scene).fromDegrees)
        val pageRect = if (book3d) {
            shape.content
        } else {
            // A paper margin above and below the text, as in a printed book; the bottom one holds the time left.
            androidx.compose.ui.geometry.Rect(0f, topInset + with(density) { 18.dp.toPx() }, wPx, hPx - bottomInset - with(density) { 30.dp.toPx() })
        }
        turner.pageRect = pageRect
        turner.spread = spread
        val prefs = preferencesFor(settings, ink, spread = if (turner.enabled || book3d) spread else null)
        val (pageBg, _) = pageColors(settings.theme, ink)

        if (book3d) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // A page can be pulled from anywhere around the book, not only on it.
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        var ours = false
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = { if (ours) turner.dragEnd(tracker.calculateVelocity().x, finished = true); ours = false },
                            onDragCancel = { if (ours) turner.dragEnd(0f, finished = false); ours = false },
                        ) { change, dx ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val r = turner.pageRect
                            val p = change.position
                            if (!ours) {
                                val start = p - Offset(dx, 0f)
                                ours = turner.dragStart(start.x - r.left, start.y.coerceIn(r.top, r.bottom) - r.top, dx)
                            }
                            if (ours) {
                                change.consume()
                                turner.dragMove(p.x - r.left, p.y.coerceIn(r.top, r.bottom) - r.top)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            val x = p.x / size.width
                            when {
                                panel != null -> panel = null
                                x < 0.3f -> turner.turnPage(false)
                                x > 0.7f -> turner.turnPage(true)
                                else -> chrome = !chrome
                            }
                        }
                    },
            ) {
                io.github.christiandonatti.fwip.book.scene.SceneBackground(
                    scene = settings.scene,
                    photo = settings.isPhoto(settings.scene) && io.github.christiandonatti.fwip.book.scene.scenePhotoExists(context, settings.scene),
                    motion = settings.sceneMotion && !reduceMotion && ink == InkLook.Off,
                    parallax = settings.parallax && !reduceMotion,
                )
            }
            BookBody(shape, board, Color(pageBg), sceneLight)
        }
        // A new font of the user's needs a new navigator to declare it; it opens where we were.
        val twoColumns = spread && (turner.enabled || book3d)
        key(UserFonts.version, twoColumns) {
            NavigatorHost(
                modifier = Modifier
                    .offset { IntOffset(pageRect.left.toInt(), pageRect.top.toInt()) }
                    .size(with(density) { pageRect.width.toDp() }, with(density) { pageRect.height.toDp() }),
                turner = turner,
                publication = publication,
                initial = remember { locator ?: initial },
                preferences = prefs,
                configuration = remember { navigatorConfiguration(context, selection, twoColumns) },
                onExternalLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                onReady = { navigator = it; turner.navigator = it },
            )
        }
        // While a page turns, the turn covers the pages anyway (and one layer less over it is easier on GPUs).
        if (book3d && turner.turn == null) BookFront(shape, sceneLight)
        TurnOverlay(turner, Color(pageBg))
        if (book3d) SceneLightLayer(shape, sceneLight, settings.sceneMotion && !reduceMotion && ink == InkLook.Off)
        if (book3d) {
            BookCover(
                shape, remember(cover) { cover?.asImageBitmap() }, board, ready = pagePainted, closed = showEnd,
                place = {
                    io.github.christiandonatti.fwip.book.scene.SceneBackground(
                        scene = settings.scene,
                        photo = settings.isPhoto(settings.scene) && io.github.christiandonatti.fwip.book.scene.scenePhotoExists(context, settings.scene),
                        motion = settings.sceneMotion && !reduceMotion && ink == InkLook.Off,
                        parallax = settings.parallax && !reduceMotion,
                    )
                },
            )
        }

        // ---------- What the navigator tells us ----------
        val nav = navigator
        LaunchedEffect(nav, prefs) { nav?.submitPreferences(prefs) }
        turner.onEnd = { showEnd = true }
        turner.isEnd = { atBookEnd(publication, it) }
        // A forward turn that went nowhere on the last page: the end of the book.
        fun forward(n: EpubNavigatorFragment, animated: Boolean) {
            val before = n.currentLocator.value
            if (!n.goForward(animated = animated)) { showEnd = true; return }
            scope.launch {
                kotlinx.coroutines.delay(700)
                val after = n.currentLocator.value
                if (samePlace(after, before) && atBookEnd(publication, before)) showEnd = true
            }
        }
        LaunchedEffect(nav) {
            val n = nav ?: return@LaunchedEffect
            n.addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val w = n.publicationView.width.coerceAtLeast(1)
                    val x = event.point.x / w
                    val side = when { x < 0.28f -> -1; x > 0.72f -> 1; else -> 0 }
                    val move = if (settings.turn == BookTurn.Scroll && !settings.book3d) 0 else reader.tapZones.move(side, rtl = false)
                    when {
                        panel != null -> panel = null
                        move != 0 && turner.enabled -> turner.turnPage(move > 0)
                        move > 0 -> forward(n, settings.turn == BookTurn.Slide && !reduceMotion)
                        move < 0 -> n.goBackward(animated = settings.turn == BookTurn.Slide && !reduceMotion)
                        else -> chrome = !chrome
                    }
                    return true
                }
            })
            n.addDecorationListener("highlights", object : DecorableNavigator.Listener {
                override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                    editing = notes.highlights.firstOrNull { it.id == event.decoration.id }
                    return editing != null
                }
            })
        }
        LaunchedEffect(nav) {
            val n = nav ?: return@LaunchedEffect
            var lastPos = -1
            var lastAt = 0L
            var lastSave = 0L
            n.currentLocator.collect { loc ->
                locator = loc
                val pos = positionIndex(book, loc)
                notes.setLast(loc.toJSON().toString())
                val now = System.currentTimeMillis()
                // Reading pace: forward steps a few seconds to a few minutes apart.
                if (lastPos >= 0 && pos > lastPos && pos - lastPos <= 3 && now - lastAt in 3_000L..240_000L) notes.addReading(pos - lastPos, now - lastAt)
                if (pos != lastPos) { lastPos = pos; lastAt = now }
                if (now - lastSave > 4_000) { notes.save(); lastSave = now }
                onProgress(pos, book.total)
            }
        }
        DisposableEffect(Unit) { onDispose { notes.save() } }
        // Highlights, and the paragraph being read aloud.
        LaunchedEffect(nav, notesVersion) {
            val n = nav ?: return@LaunchedEffect
            n.applyDecorations(notes.highlights.mapNotNull { h ->
                runCatching { Locator.fromJSON(JSONObject(h.locator)) }.getOrNull()?.let { Decoration(h.id, it, Decoration.Style.Highlight(h.color)) }
            }, "highlights")
        }
        LaunchedEffect(nav, speaker.current) {
            val n = nav ?: return@LaunchedEffect
            val at = speaker.current
            n.applyDecorations(listOfNotNull(at?.let { Decoration("tts", it, Decoration.Style.Underline(0xFFE0A030.toInt())) }), "tts")
            if (at != null) n.go(at, animated = false)
        }
        // Keys: volume (if allowed), keyboards and page turners.
        DisposableEffect(Unit) {
            ReaderKeys.active = ReaderSettings.get(context).volumeKeys
            ReaderKeys.reading = true
            onDispose { ReaderKeys.active = false; ReaderKeys.reading = false }
        }
        LaunchedEffect(nav) {
            val n = nav ?: return@LaunchedEffect
            ReaderKeys.events.collect { k ->
                val animated = settings.turn == BookTurn.Slide && !reduceMotion
                when {
                    turner.enabled -> turner.turnPage(k > 0)
                    k > 0 -> forward(n, animated)
                    else -> n.goBackward(animated = animated)
                }
            }
        }

        val pos = locator?.let { positionIndex(book, it) } ?: 0
        ReadingLightLayer(light)
        InkFlash(pos, light.inkFlash)

        // ---------- The quiet line at the bottom (tap to change what it shows) ----------
        if (!chrome && panel == null) {
            ReadingStatus(light.showClock, light.showBattery)
            Text(
                progressLabel(settings.progress, book, locator, notes),
                style = MaterialTheme.typography.labelSmall,
                color = if (book3d) Color.White.copy(alpha = 0.85f) else Color(pageColors(settings.theme, ink).second).copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(bottom = if (book3d) 10.dp else 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (book3d) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { settings.updateProgress(BookProgress.entries[(settings.progress.ordinal + 1) % BookProgress.entries.size]) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // ---------- Controls ----------
        AnimatedVisibility(
            visible = chrome && panel == null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val marked = remember(locator, notesVersion) { locator?.let { loc -> isMarked(notes, loc) } == true }
            val compact = maxWidth < 600.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(FwipColors.Panel.copy(alpha = 0.96f))
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onClose, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(locator?.let { chapterTitle(publication, it) } ?: author.orEmpty(), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val markView = LocalView.current
                val toggleMark = {
                    locator?.let { loc ->
                        markView.tick()
                        toggleMark(notes, publication, loc)
                        notesVersion++
                    }
                    Unit
                }
                CircleIconButton(Icons.Filled.List, str(R.string.book_contents), { panel = BookPanel.Contents }, size = 44.dp)
                if (compact) {
                    // A phone: only what is used most; the rest in a menu.
                    TextIconButton("Aa", str(R.string.book_text_settings)) { panel = BookPanel.Text }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        CircleIconButton(Icons.Filled.MoreVert, str(R.string.more_options), { menu = true }, size = 44.dp)
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = FwipColors.PanelHigh) {
                            DropdownMenuItem(text = { Text(str(R.string.book_search)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, onClick = { menu = false; panel = BookPanel.Search })
                            DropdownMenuItem(text = { Text(str(R.string.book_scene)) }, leadingIcon = { Text("3D", style = MaterialTheme.typography.labelMedium) }, onClick = { menu = false; panel = BookPanel.Scene })
                            DropdownMenuItem(text = { Text(str(R.string.book_listen)) }, leadingIcon = { Text("♪") }, onClick = { menu = false; panel = BookPanel.Listen })
                            val config = androidx.compose.ui.platform.LocalConfiguration.current
                            val rotationLocked = light.rotation != io.github.christiandonatti.fwip.reader.ScreenRotation.Auto
                            DropdownMenuItem(
                                text = { Text(str(if (rotationLocked) R.string.rotation_unlock else R.string.rotation_lock)) },
                                leadingIcon = { io.github.christiandonatti.fwip.reader.RotationIcon(LocalContentColor.current, rotationLocked) },
                                onClick = { menu = false; light.toggleRotationLock(config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) },
                            )
                            DropdownMenuItem(
                                text = { Text(str(if (marked) R.string.book_unmark else R.string.book_mark)) },
                                leadingIcon = { Icon(Icons.Filled.Star, null, tint = if (marked) FwipColors.Amber else LocalContentColor.current) },
                                onClick = { menu = false; toggleMark() },
                            )
                        }
                    }
                } else {
                    CircleIconButton(Icons.Filled.Search, str(R.string.book_search), { panel = BookPanel.Search }, size = 44.dp)
                    TextIconButton("Aa", str(R.string.book_text_settings)) { panel = BookPanel.Text }
                    TextIconButton("3D", str(R.string.book_scene)) { panel = BookPanel.Scene }
                    TextIconButton("♪", str(R.string.book_listen)) { panel = BookPanel.Listen }
                    io.github.christiandonatti.fwip.reader.RotationLockButton(light)
                    CircleIconButton(
                        Icons.Filled.Star,
                        str(if (marked) R.string.book_unmark else R.string.book_mark),
                        toggleMark,
                        size = 44.dp,
                        filled = marked,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = chrome && panel == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            val progression = locator?.locations?.totalProgression?.toFloat() ?: 0f
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(FwipColors.Panel.copy(alpha = 0.96f))
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dragging?.let { "${(it * 100).toInt()}%" } ?: progressLabel(BookProgress.Page, book, locator, notes),
                        style = MaterialTheme.typography.labelMedium,
                        color = FwipColors.TextSoft,
                        modifier = Modifier.weight(1f),
                    )
                    Text(progressLabel(BookProgress.ChapterTime, book, locator, notes), style = MaterialTheme.typography.labelMedium, color = FwipColors.TextMuted)
                }
                Slider(
                    value = dragging ?: progression,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        val target = dragging ?: return@Slider
                        dragging = null
                        scope.launch { publication.locateProgression(target.toDouble())?.let { navigator?.go(it) } }
                    },
                    colors = SliderDefaults.colors(thumbColor = FwipColors.Amber, activeTrackColor = FwipColors.Amber, inactiveTrackColor = FwipColors.Text.copy(alpha = 0.16f)),
                )
            }
        }

        // ---------- Panels ----------
        panel?.let { p ->
            BookPanelHost(
                panel = p,
                onDismiss = { panel = null },
                book = book,
                title = title,
                author = author,
                notes = notes,
                notesVersion = notesVersion,
                onNotesChanged = { notesVersion++ },
                locator = locator,
                settings = settings,
                light = light,
                speaker = speaker,
                language = language,
                onGo = { target ->
                    panel = null
                    chrome = false
                    navigator?.go(target)
                },
                onGoLink = { link ->
                    panel = null
                    chrome = false
                    navigator?.go(link)
                },
                onEditHighlight = { editing = it },
            )
        }
        editing?.let { h ->
            HighlightDialog(
                highlight = h,
                onSave = { updated ->
                    val i = notes.highlights.indexOfFirst { it.id == updated.id }
                    if (i >= 0) notes.highlights[i] = updated
                    notes.save(); notesVersion++; editing = null
                },
                onDelete = {
                    notes.highlights.removeAll { it.id == h.id }
                    notes.save(); notesVersion++; editing = null
                },
                onDismiss = { editing = null },
            )
        }
        val endCard by produceState(false, showEnd, book3d) {
            if (showEnd && book3d) kotlinx.coroutines.delay(1300)
            value = showEnd
        }
        if (showEnd && endCard) {
            io.github.christiandonatti.fwip.reader.EndCard(
                title = title,
                next = next,
                onNext = { showEnd = false; onOpenNext() },
                onLibrary = onClose,
                onDismiss = { showEnd = false },
                seriesNote = seriesNote,
                byline = author,
                veil = if (book3d) 0.72f else 0.94f,
            )
        }
        // The first time: where to tap, the long press on a word, the corner to drag.
        val uiTips = remember { io.github.christiandonatti.fwip.ui.UiSettings.get(context) }
        if (!uiTips.bookTips && !io.github.christiandonatti.fwip.ui.A11y.screenReader(context)) {
            io.github.christiandonatti.fwip.reader.BookReaderTips(turner.enabled) { uiTips.setBookTipsSeen() }
        }
    }
}

/** Hosts Readium's navigator fragment inside Compose. */
@Composable
private fun NavigatorHost(
    modifier: Modifier,
    turner: PageTurner,
    publication: Publication,
    initial: Locator?,
    preferences: EpubPreferences,
    configuration: EpubNavigatorFragment.Configuration,
    onExternalLink: (AbsoluteUrl) -> Unit,
    onReady: (EpubNavigatorFragment) -> Unit,
) {
    val activity = LocalContext.current.activity() as? FragmentActivity ?: return
    val tag = remember { "fwip-epub-" + System.nanoTime() }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val container = FragmentContainerView(ctx).apply {
                id = View.generateViewId()
                doOnAttach {
                    val fm = activity.supportFragmentManager
                    fm.fragmentFactory = EpubNavigatorFactory(publication, EpubNavigatorFactory.Configuration(defaults = EpubDefaults(pageMargins = 1.0)))
                        .createFragmentFactory(
                            initialLocator = initial,
                            initialPreferences = preferences,
                            listener = object : EpubNavigatorFragment.Listener {
                                override fun onExternalLinkActivated(url: AbsoluteUrl) = onExternalLink(url)
                            },
                            configuration = configuration,
                        )
                    fm.commitNow { add(id, EpubNavigatorFragment::class.java, android.os.Bundle(), tag) }
                    (fm.findFragmentByTag(tag) as? EpubNavigatorFragment)?.let(onReady)
                }
            }
            // Swipes go to the page turner when pages turn like paper; everything else to the text.
            TouchGate(ctx).apply {
                this.turner = turner
                addView(container, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            val fm = activity.supportFragmentManager
            fm.findFragmentByTag(tag)?.let { f -> runCatching { fm.beginTransaction().remove(f).commitNowAllowingStateLoss() } }
            fm.fragmentFactory = FragmentFactory()
        }
    }
}

/** The status and navigation bars show only with the controls. */
@Composable
private fun ImmersiveBars(visible: Boolean) {
    val view = LocalView.current
    val activity = view.context.activity() ?: return
    DisposableEffect(visible) {
        val c = WindowCompat.getInsetsController(activity.window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars()) } }
}

@Composable
private fun TextIconButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(0.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .semantics { contentDescription = description }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Colours for highlights: yellow, green, blue, pink. */
val HIGHLIGHT_COLORS = listOf(0xFFF9E07D.toInt(), 0xFFADF77B.toInt(), 0xFF7CC6F7.toInt(), 0xFFF7A1C4.toInt())

/** The position (0-based, like a page) of [loc] in the book. */
internal fun positionIndex(book: OpenedBook, loc: Locator): Int {
    loc.locations.position?.let { return (it - 1).coerceIn(0, book.total - 1) }
    val p = loc.locations.totalProgression ?: return 0
    return (p * (book.total - 1)).toInt().coerceIn(0, book.total - 1)
}

/** The reading-order item [loc] is in. */
internal fun resourceIndex(book: OpenedBook, loc: Locator): Int {
    val href = loc.href.removeFragment().toString()
    return book.publication.readingOrder.indexOfFirst { it.url().removeFragment().toString() == href }
}

/** The table of contents entry for [loc]: the last one starting in its chapter file. */
/** In the last chapter: with a turn that went nowhere, that is the last page (two pages on screen start at 0.5 or less). */
internal fun atBookEnd(publication: Publication, loc: Locator?): Boolean {
    loc ?: return false
    val last = publication.readingOrder.lastOrNull()?.url()?.removeFragment()?.toString() ?: return false
    return loc.href.removeFragment().toString() == last
}

/** The same page, even if the navigator handed out a fresh locator for it. */
internal fun samePlace(a: Locator?, b: Locator?): Boolean {
    if (a == null || b == null) return a == b
    return a.href.removeFragment() == b.href.removeFragment() &&
        kotlin.math.abs((a.locations.progression ?: 0.0) - (b.locations.progression ?: 0.0)) < 1e-6
}

internal fun chapterTitle(publication: Publication, loc: Locator): String? {
    val href = loc.href.removeFragment().toString()
    fun flatten(links: List<Link>): List<Link> = links.flatMap { listOf(it) + flatten(it.children) }
    return flatten(publication.tableOfContents).lastOrNull { it.url().removeFragment().toString() == href }?.title ?: loc.title
}

internal fun isMarked(notes: BookNotes, loc: Locator): Boolean {
    val p = loc.locations.totalProgression ?: return false
    return notes.marks.any { kotlin.math.abs(it.progression - p) < 0.0015 }
}

internal fun toggleMark(notes: BookNotes, publication: Publication, loc: Locator) {
    val p = loc.locations.totalProgression ?: 0.0
    if (!notes.marks.removeAll { kotlin.math.abs(it.progression - p) < 0.0015 }) {
        notes.marks += BookMark(java.util.UUID.randomUUID().toString(), loc.toJSON().toString(), chapterTitle(publication, loc).orEmpty(), p, System.currentTimeMillis())
    }
    notes.save()
}

/** The bottom line: time left in the chapter or book (at your pace), the page, or the percentage. */
internal fun progressLabel(mode: BookProgress, book: OpenedBook, loc: Locator?, notes: BookNotes): String {
    if (loc == null) return ""
    val pos = positionIndex(book, loc)
    val speed = notes.speed()
    return when (mode) {
        BookProgress.Page -> str(R.string.book_page_of, pos + 1, book.total)
        BookProgress.Percent -> "${((loc.locations.totalProgression ?: 0.0) * 100).toInt()}%"
        BookProgress.BookTime -> str(R.string.book_left_in_book, BookNotes.timeLabel((book.total - 1 - pos) / speed, str(R.string.book_less_minute)))
        BookProgress.ChapterTime -> {
            val r = resourceIndex(book, loc)
            if (r < 0) return str(R.string.book_page_of, pos + 1, book.total)
            val end = book.starts[r] + book.positions[r].size - 1
            str(R.string.book_left_in_chapter, BookNotes.timeLabel((end - pos).coerceAtLeast(0) / speed, str(R.string.book_less_minute)))
        }
    }
}
