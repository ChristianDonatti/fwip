@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
    org.readium.r2.shared.DelicateReadiumApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package io.github.christiandonatti.fwip.book

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
    data class Failed(val message: String) : BookLoad
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

internal fun preferencesFor(s: BookSettings, ink: InkLook): EpubPreferences {
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
        columnCount = when (s.columns) { BookColumns.Auto -> ColumnCount.AUTO; BookColumns.One -> ColumnCount.ONE; BookColumns.Two -> ColumnCount.TWO },
        publisherStyles = s.publisherStyles,
        scroll = s.turn == BookTurn.Scroll,
    )
}

/** The navigator's configuration: Fwip's fonts (shipped and the user's), the selection menu. */
private fun navigatorConfiguration(context: Context, selection: ActionMode.Callback): EpubNavigatorFragment.Configuration =
    EpubNavigatorFragment.Configuration {
        servedAssets = listOf("fonts/.*")
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
) {
    val context = LocalContext.current
    val light = remember { LightSettings.get(context) }
    val settings = remember { BookSettings.get(context) }
    BackHandler(onBack = onClose)
    val loaded by produceState<BookLoad>(BookLoad.Loading, comic.id) {
        value = runCatching { openBook(context, comic) }.fold(
            { BookLoad.Ready(it) },
            { BookLoad.Failed(str(R.string.error_open_file) + "\n" + (it.message ?: it.javaClass.simpleName)) },
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
                Text(l.message, style = MaterialTheme.typography.bodyMedium, color = Color(fg).copy(alpha = 0.7f), textAlign = TextAlign.Center)
                PillButton(str(R.string.back), onClose)
            }
            is BookLoad.Ready -> BookReader(comic, title, author, l.book, startPage, settings, light, onClose, onProgress)
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }

    val ink = light.ink
    val prefs = preferencesFor(settings, ink)
    val reduceMotion = remember { io.github.christiandonatti.fwip.ui.A11y.reduceMotion(context) }
    ImmersiveBars(visible = chrome || panel != null)

    Box(Modifier.fillMaxSize()) {
        // A new font of the user's needs a new navigator to declare it; it opens where we were.
        key(UserFonts.version) {
            NavigatorHost(
                // A paper margin above and below the text, as in a printed book; the bottom one holds the time left.
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                    .padding(top = 18.dp, bottom = 30.dp),
                publication = publication,
                initial = remember { locator ?: initial },
                preferences = prefs,
                configuration = remember { navigatorConfiguration(context, selection) },
                onExternalLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                onReady = { navigator = it },
            )
        }

        // ---------- What the navigator tells us ----------
        val nav = navigator
        LaunchedEffect(nav, prefs) { nav?.submitPreferences(prefs) }
        LaunchedEffect(nav) {
            val n = nav ?: return@LaunchedEffect
            val reader = ReaderSettings.get(context)
            n.addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val w = n.publicationView.width.coerceAtLeast(1)
                    val x = event.point.x / w
                    val side = when { x < 0.28f -> -1; x > 0.72f -> 1; else -> 0 }
                    val move = if (settings.turn == BookTurn.Scroll) 0 else reader.tapZones.move(side, rtl = false)
                    when {
                        panel != null -> panel = null
                        move > 0 -> n.goForward(animated = settings.turn == BookTurn.Slide && !reduceMotion)
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
                if (k > 0) n.goForward(animated = animated) else n.goBackward(animated = animated)
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
                color = Color(pageColors(settings.theme, ink).second).copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                CircleIconButton(Icons.Filled.List, str(R.string.book_contents), { panel = BookPanel.Contents }, size = 44.dp)
                CircleIconButton(Icons.Filled.Search, str(R.string.book_search), { panel = BookPanel.Search }, size = 44.dp)
                TextIconButton("Aa", str(R.string.book_text_settings)) { panel = BookPanel.Text }
                TextIconButton("♪", str(R.string.book_listen)) { panel = BookPanel.Listen }
                CircleIconButton(
                    Icons.Filled.Star,
                    str(if (marked) R.string.book_unmark else R.string.book_mark),
                    {
                        locator?.let { loc ->
                            toggleMark(notes, publication, loc)
                            notesVersion++
                        }
                    },
                    size = 44.dp,
                    filled = marked,
                )
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
    }
}

/** Hosts Readium's navigator fragment inside Compose. */
@Composable
private fun NavigatorHost(
    modifier: Modifier,
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
            FragmentContainerView(ctx).apply {
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
