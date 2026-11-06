@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.christiandonatti.fwip.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.reader.Choice
import io.github.christiandonatti.fwip.reader.InkLook
import io.github.christiandonatti.fwip.reader.LevelSlider
import io.github.christiandonatti.fwip.reader.LightSettings
import io.github.christiandonatti.fwip.reader.Section
import io.github.christiandonatti.fwip.reader.Toggle
import io.github.christiandonatti.fwip.reader.settingAnchor
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.CircleIconButton
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.PillStyle
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.search

enum class BookPanel { Text, Contents, Search, Listen }

/** A sheet at the bottom of the reader (beside it on a wide tablet), with a veil behind. */
@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxH = maxHeight * 0.82f
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(FwipColors.Panel)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(top = 8.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(CircleShape).background(FwipColors.Stroke))
                Row(Modifier.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    actions()
                    CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 40.dp)
                }
                content()
            }
        }
    }
}

@Composable
internal fun BookPanelHost(
    panel: BookPanel,
    onDismiss: () -> Unit,
    book: OpenedBook,
    title: String,
    author: String?,
    notes: BookNotes,
    notesVersion: Int,
    onNotesChanged: () -> Unit,
    locator: Locator?,
    settings: BookSettings,
    light: LightSettings,
    speaker: BookSpeaker,
    language: String?,
    onGo: (Locator) -> Unit,
    onGoLink: (Link) -> Unit,
    onEditHighlight: (BookHighlight) -> Unit,
) {
    when (panel) {
        BookPanel.Text -> Sheet(str(R.string.book_text_settings), onDismiss) { TextPanel(settings, light) }
        BookPanel.Contents -> ContentsPanel(book, title, author, notes, notesVersion, onNotesChanged, locator, onDismiss, onGo, onGoLink, onEditHighlight)
        BookPanel.Search -> Sheet(str(R.string.book_search), onDismiss) { SearchPanel(book, onGo) }
        BookPanel.Listen -> Sheet(str(R.string.book_listen), onDismiss) { ListenPanel(book, locator, speaker, settings, language) }
    }
}

// ---------------------------------------------------------------- Aa

@Composable
private fun TextPanel(settings: BookSettings, light: LightSettings) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
    ) { BookTextSettings(settings, light, inReader = true) }
}

/** Book look and layout: in the reader's Aa panel and in Settings > Books. */
@Composable
fun BookTextSettings(settings: BookSettings, light: LightSettings, inReader: Boolean = false) {
    val context = LocalContext.current
    var fonts by remember { mutableStateOf(UserFonts.list(context)) }
    var added by remember { mutableStateOf(false) }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            UserFonts.add(context, uri)?.let { name ->
                fonts = UserFonts.list(context)
                settings.updateFont(BookFontKind.Custom, name)
                added = true
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Themes: little pages in their colours.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().settingAnchor("bookTheme")) {
            BookTheme.entries.forEach { t ->
                val (bg, fg) = pageColors(t, light.ink)
                val on = t == settings.theme
                Column(
                    Modifier.weight(1f).clickable(role = Role.RadioButton) { settings.updateTheme(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(bg))
                            .border(if (on) 2.5.dp else 1.dp, if (on) FwipColors.Amber else FwipColors.Stroke, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = Color(fg), fontSize = 18.sp) }
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (on) FwipColors.Text else FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (light.ink != InkLook.Off) Text(str(R.string.book_ink_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)

        // Size: two buttons and the value, as on e-readers.
        Row(Modifier.settingAnchor("bookSize"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.book_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            SizeButton("A", 14) { settings.updateFontSize(settings.fontSize - 0.1f) }
            Text("${(settings.fontSize * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            SizeButton("A", 22) { settings.updateFontSize(settings.fontSize + 0.1f) }
        }

        Section(str(R.string.book_font))
        FlowRow(Modifier.settingAnchor("bookFont"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shipped = listOf(
                BookFontKind.Original to str(R.string.book_font_original),
                BookFontKind.Literata to "Literata",
                BookFontKind.Bitter to "Bitter",
                BookFontKind.Atkinson to "Atkinson Hyperlegible",
                BookFontKind.Serif to str(R.string.book_font_serif),
                BookFontKind.Sans to str(R.string.book_font_sans),
                BookFontKind.Dyslexic to "OpenDyslexic",
            )
            shipped.forEach { (k, label) -> Chip(label, settings.font == k) { settings.updateFont(k) } }
            fonts.forEach { f -> Chip(UserFonts.nameOf(f), settings.font == BookFontKind.Custom && settings.customFont == f.name) { settings.updateFont(BookFontKind.Custom, f.name) } }
            Chip("+ " + str(R.string.book_add_font), false) { pickFont.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/vnd.ms-opentype", "application/octet-stream")) }
        }
        if (added) Text(str(R.string.book_font_added), style = MaterialTheme.typography.bodySmall, color = FwipColors.Amber)

        Section(str(R.string.book_layout))
        LevelSlider(str(R.string.book_line_height), (settings.lineHeight - 1f) / 1.2f, 0f..1f, { settings.updateLineHeight(1f + it * 1.2f) }, key = "bookLines", valueLabel = "%.1f".format(settings.lineHeight))
        LevelSlider(str(R.string.book_margins), (settings.margins - 0.4f) / 1.8f, 0f..1f, { settings.updateMargins(0.4f + it * 1.8f) }, valueLabel = "%.1f".format(settings.margins))
        Toggle(str(R.string.book_justify), str(R.string.book_justify_hint), settings.justify, settings::updateJustify, key = "bookJustify")
        Toggle(str(R.string.book_hyphens), str(R.string.book_hyphens_hint), settings.hyphens, settings::updateHyphens, key = "bookHyphens")
        Toggle(str(R.string.book_bold), str(R.string.book_bold_hint), settings.bold, settings::updateBold, key = "bookBold")
        Toggle(str(R.string.book_publisher), str(R.string.book_publisher_hint), settings.publisherStyles, settings::updatePublisherStyles, key = "bookPublisher")
        Choice(str(R.string.book_columns), BookColumns.entries, settings.columns, { it.label }, settings::updateColumns, key = "bookColumns", hint = str(R.string.book_columns_hint))
        Choice(str(R.string.page_turn), BookTurn.entries, settings.turn, { it.label }, settings::updateTurn, key = "bookTurn")
        Choice(str(R.string.book_progress), BookProgress.entries, settings.progress, { it.label }, settings::updateProgress, key = "bookProgress", hint = str(R.string.book_progress_hint))

        if (inReader) {
            Section(str(R.string.section_light))
            io.github.christiandonatti.fwip.reader.LightLevelRow(light)
            LevelSlider(str(R.string.warm_light), light.warmth, 0f..1f, light::updateWarmth)
            Choice(str(R.string.ink_look), InkLook.entries, light.ink, { it.label }, light::updateInk)
        }
    }
}

@Composable
private fun SizeButton(text: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = size.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (on) FwipColors.Text else FwipColors.Surface)
            .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1) }
}

// ---------------------------------------------------------------- Contents, bookmarks, highlights

@Composable
private fun ContentsPanel(
    book: OpenedBook,
    title: String,
    author: String?,
    notes: BookNotes,
    notesVersion: Int,
    onNotesChanged: () -> Unit,
    locator: Locator?,
    onDismiss: () -> Unit,
    onGo: (Locator) -> Unit,
    onGoLink: (Link) -> Unit,
    onEditHighlight: (BookHighlight) -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    Sheet(title, onDismiss, actions = {
        if (tab == 2 && notes.highlights.isNotEmpty()) {
            CircleIconButton(Icons.Filled.Share, str(R.string.book_export_notes), {
                runCatching {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/markdown")
                                .putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                                .putExtra(android.content.Intent.EXTRA_TEXT, notes.markdown(title, author)),
                            str(R.string.book_export_notes),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }, size = 40.dp)
            Spacer(Modifier.width(8.dp))
        }
    }) {
        Row(
            Modifier
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .clip(CircleShape)
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(str(R.string.book_toc), str(R.string.book_marks), str(R.string.book_highlights)).forEachIndexed { i, t ->
                val on = i == tab
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(if (on) FwipColors.Text else Color.Transparent)
                        .clickable(role = Role.Tab) { tab = i }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft) }
            }
        }
        val currentHref = locator?.href?.removeFragment()?.toString()
        when (tab) {
            0 -> {
                fun flatten(links: List<Link>, depth: Int): List<Pair<Link, Int>> = links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }
                val toc = remember { flatten(book.publication.tableOfContents, 0).ifEmpty { book.publication.readingOrder.map { it to 0 } } }
                val here = toc.indexOfLast { it.first.url().removeFragment().toString() == currentHref }
                val state = rememberLazyListState(initialFirstVisibleItemIndex = (here - 3).coerceAtLeast(0))
                LazyColumn(state = state, modifier = Modifier.padding(bottom = 16.dp)) {
                    itemsIndexed(toc) { i, (link, depth) ->
                        Text(
                            link.title?.takeIf { it.isNotBlank() } ?: str(R.string.book_chapter_n, i + 1),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (i == here) FwipColors.Amber else FwipColors.Text,
                            fontWeight = if (i == here) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGoLink(link) }
                                .padding(start = 22.dp + 18.dp * depth, end = 22.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
            1 -> {
                val marks = remember(notesVersion) { notes.marks.sortedBy { it.progression } }
                if (marks.isEmpty()) Empty(str(R.string.book_no_marks))
                LazyColumn(Modifier.padding(bottom = 16.dp)) {
                    items(marks, key = { it.id }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { runCatching { Locator.fromJSON(JSONObject(m.locator)) }.getOrNull()?.let(onGo) }
                                .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.chapter.ifBlank { title }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${(m.progression * 100).toInt()}% · " + android.text.format.DateUtils.getRelativeTimeSpanString(m.created),
                                    style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted,
                                )
                            }
                            CircleIconButton(Icons.Filled.Delete, str(R.string.delete), { notes.marks.removeAll { it.id == m.id }; notes.save(); onNotesChanged() }, size = 38.dp)
                        }
                    }
                }
            }
            else -> {
                val hls = remember(notesVersion) { notes.highlights.sortedBy { it.progression } }
                if (hls.isEmpty()) Empty(str(R.string.book_no_highlights))
                LazyColumn(Modifier.padding(bottom = 16.dp)) {
                    items(hls, key = { it.id }) { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { runCatching { Locator.fromJSON(JSONObject(h.locator)) }.getOrNull()?.let(onGo) }
                                .padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.width(4.dp).height(44.dp).clip(CircleShape).background(Color(h.color)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(h.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodySmall, color = FwipColors.Amber, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(h.chapter, style = MaterialTheme.typography.labelSmall, color = FwipColors.TextMuted, maxLines = 1)
                            }
                            CircleIconButton(Icons.Filled.Edit, str(R.string.book_note), { onEditHighlight(h) }, size = 38.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted, modifier = Modifier.padding(22.dp))
}

// ---------------------------------------------------------------- Search

@Composable
private fun SearchPanel(book: OpenedBook, onGo: (Locator) -> Unit) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<Locator>() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun run() {
        val q = query.trim()
        if (q.length < 2) return
        keyboard?.hide()
        results.clear()
        searching = true
        searched = q
        scope.launch {
            runCatching {
                val it = book.publication.search(q) ?: return@runCatching
                while (results.size < 300) {
                    val page = it.next().getOrNull() ?: break
                    results += page.locators
                }
                it.close()
            }
            searching = false
        }
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(CircleShape)
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, CircleShape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(str(R.string.book_search_hint), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                    cursorBrush = SolidColor(FwipColors.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (searching) CircularProgressIndicator(Modifier.size(18.dp), color = FwipColors.Amber, strokeWidth = 2.dp)
        }
        if (!searching && searched != null) {
            Text(
                if (results.isEmpty()) str(R.string.book_search_none) else str(R.string.book_search_count, results.size),
                style = MaterialTheme.typography.labelMedium, color = FwipColors.TextMuted,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        LazyColumn {
            items(results.toList()) { loc ->
                val t = loc.text
                Column(
                    Modifier.fillMaxWidth().clickable { onGo(loc) }.padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            append("…" + t.before.orEmpty().takeLast(60).trimStart())
                            withStyle(SpanStyle(color = FwipColors.Amber, fontWeight = FontWeight.SemiBold)) { append(t.highlight.orEmpty()) }
                            append(t.after.orEmpty().take(80).trimEnd() + "…")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    loc.title?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = FwipColors.TextMuted, maxLines = 1) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Listen

@Composable
private fun ListenPanel(book: OpenedBook, locator: Locator?, speaker: BookSpeaker, settings: BookSettings, language: String?) {
    Column(
        Modifier.padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!speaker.ready) Text(str(R.string.book_tts_unavailable), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("⏮", { speaker.previous() }, style = PillStyle.Ghost, height = 52.dp, enabled = speaker.playing)
            PillButton(
                if (speaker.playing) str(R.string.book_pause) else str(R.string.book_play),
                {
                    when {
                        speaker.playing -> speaker.pause()
                        speaker.current != null -> speaker.resume(language, settings.ttsRate)
                        locator != null -> speaker.play(book.publication, locator, language, settings.ttsRate)
                    }
                },
                style = PillStyle.Amber,
                height = 52.dp,
                modifier = Modifier.weight(1f),
                enabled = speaker.ready,
            )
            PillButton("⏭", { speaker.next() }, style = PillStyle.Ghost, height = 52.dp, enabled = speaker.playing)
        }
        Choice(str(R.string.book_tts_speed), listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f), settings.ttsRate, { "${it}x".replace(".0x", "x") }, { r ->
            settings.updateTtsRate(r)
            speaker.setRate(r)
        })
        val timers = listOf(0, 15, 30, 45, 60, -1)
        val chosen = when {
            speaker.sleepAtChapterEnd -> -1
            speaker.sleepAt == null -> 0
            else -> timers.filter { it > 0 }.minByOrNull { kotlin.math.abs((speaker.sleepAt!! - System.currentTimeMillis()) / 60_000 - it) } ?: 0
        }
        Choice(str(R.string.book_sleep), timers, chosen, { m ->
            when (m) {
                0 -> str(R.string.book_sleep_off)
                -1 -> str(R.string.book_sleep_chapter)
                else -> "$m min"
            }
        }, { m ->
            speaker.sleepAtChapterEnd = m == -1
            speaker.sleepAt = if (m > 0) System.currentTimeMillis() + m * 60_000L else null
        })
        Text(str(R.string.book_tts_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
    }
}

// ---------------------------------------------------------------- A highlight and its note

@Composable
internal fun HighlightDialog(highlight: BookHighlight, onSave: (BookHighlight) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf(highlight.note) }
    var color by remember { mutableStateOf(highlight.color) }
    io.github.christiandonatti.fwip.ui.FwipDialog(onDismiss) {
        io.github.christiandonatti.fwip.ui.DialogTitle(str(R.string.book_highlight), onDismiss)
        Text("“" + highlight.text + "”", style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft, maxLines = 8, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HIGHLIGHT_COLORS.forEach { c ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (c == color) 3.dp else 0.dp, FwipColors.Text, CircleShape)
                        .clickable(role = Role.RadioButton) { color = c },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            if (note.isEmpty()) Text(str(R.string.book_note_hint), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = FwipColors.Text),
                cursorBrush = SolidColor(FwipColors.Amber),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(str(R.string.delete), onDelete, style = PillStyle.Ghost, height = 46.dp)
            Spacer(Modifier.weight(1f))
            PillButton(str(R.string.save), { onSave(highlight.copy(note = note.trim(), color = color)) }, style = PillStyle.Amber, height = 46.dp)
        }
    }
}
