package io.github.christiandonatti.fwip.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.reader.ComicReadingSettings
import io.github.christiandonatti.fwip.reader.LightComfortSettings
import io.github.christiandonatti.fwip.reader.LightSettings
import io.github.christiandonatti.fwip.reader.LocalSettingHighlight
import io.github.christiandonatti.fwip.reader.ReaderSettings
import io.github.christiandonatti.fwip.reader.Section
import io.github.christiandonatti.fwip.reader.Toggle
import io.github.christiandonatti.fwip.reader.WarmSchedule
import io.github.christiandonatti.fwip.reader.InkLook
import io.github.christiandonatti.fwip.reader.settingAnchor
import io.github.christiandonatti.fwip.remote.Server
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.flow.Flow

/** The groups of Settings, in the order they are listed, with the heading each sits under. */
enum class SettingsGroup(val key: String, @StringRes private val titleRes: Int, val glyph: GroupGlyph, @StringRes val sectionRes: Int) {
    Library("library", R.string.settings_library, GroupGlyph.Folder, R.string.settings_section_library),
    Servers("servers", R.string.tab_servers, GroupGlyph.Server, R.string.settings_section_library),
    Appearance("appearance", R.string.appearance, GroupGlyph.Palette, R.string.settings_section_look),
    Light("light", R.string.settings_light, GroupGlyph.Sun, R.string.settings_section_look),
    Comics("comics", R.string.settings_comics, GroupGlyph.Panels, R.string.settings_section_reading),
    Books("books", R.string.settings_books, GroupGlyph.Book, R.string.settings_section_reading),
    Sync("sync", R.string.sync_title, GroupGlyph.Sync, R.string.settings_section_data),
    Wifi("wifi", R.string.wifi_title, GroupGlyph.Wifi, R.string.settings_section_data),
    Storage("storage", R.string.storage_title, GroupGlyph.Disk, R.string.settings_section_data),
    Alerts("alerts", R.string.settings_alerts, GroupGlyph.Bell, R.string.settings_section_app),
    Stats("stats", R.string.stats_title, GroupGlyph.Chart, R.string.settings_section_app),
    About("about", R.string.settings_about, GroupGlyph.Info, R.string.settings_section_app);

    val title: String get() = str(titleRes)

    companion object {
        fun of(key: String?): SettingsGroup? = entries.firstOrNull { it.key == key }
    }
}

/** One setting that search can find: its group, its row (to light up) and its name. */
private data class SettingEntry(val group: SettingsGroup, val key: String?, val title: String, val extra: String = "")

private fun entries(): List<SettingEntry> {
    fun e(group: SettingsGroup, key: String?, @StringRes title: Int, extra: String = "") = SettingEntry(group, key, str(title), extra)
    return listOf(
        e(SettingsGroup.Library, "folders", R.string.library_folders),
        e(SettingsGroup.Library, "cbl", R.string.cbl_import),
        e(SettingsGroup.Library, "refresh", R.string.refresh_library),
        e(SettingsGroup.Servers, null, R.string.tab_servers, "smb webdav komga kavita opds nas"),
        e(SettingsGroup.Appearance, "look:Style:theme", R.string.appearance_theme),
        e(SettingsGroup.Appearance, "look:Style:accent", R.string.appearance_accent),
        e(SettingsGroup.Appearance, "look:Style:backdrop", R.string.backdrop_title),
        e(SettingsGroup.Appearance, "look:Style:font", R.string.font_title),
        e(SettingsGroup.Appearance, "look:Style:greeting", R.string.greeting_title),
        e(SettingsGroup.Appearance, "look:Covers:corners", R.string.corners_title),
        e(SettingsGroup.Appearance, "look:Covers:progress", R.string.progress_title),
        e(SettingsGroup.Appearance, "look:Covers:list", R.string.library_view_title),
        e(SettingsGroup.Appearance, "look:Covers:readmark", R.string.read_mark_title),
        e(SettingsGroup.Appearance, "look:Covers:size", R.string.appearance_cover_size),
        e(SettingsGroup.Appearance, "look:Covers:carousel", R.string.carousel_title),
        e(SettingsGroup.Appearance, "look:Covers:animation", R.string.cover_animation),
        e(SettingsGroup.Appearance, "look:Home:starttab", R.string.start_tab_title),
        e(SettingsGroup.Appearance, "look:Home:tabs", R.string.show_tab_collections),
        e(SettingsGroup.Appearance, "look:Home:sections", R.string.home_parts_title),
        e(SettingsGroup.Appearance, "look:Icon:icon", R.string.icon_title),
        e(SettingsGroup.Light, "light", R.string.brightness),
        e(SettingsGroup.Light, "ambient", R.string.ambient_light),
        e(SettingsGroup.Light, "warmth", R.string.warm_light),
        e(SettingsGroup.Light, "schedule", R.string.warm_schedule),
        e(SettingsGroup.Light, "ink", R.string.ink_look, "e-ink kindle"),
        e(SettingsGroup.Light, "flash", R.string.ink_flash),
        e(SettingsGroup.Light, "clock", R.string.show_clock),
        e(SettingsGroup.Light, "battery", R.string.show_battery),
        e(SettingsGroup.Light, "focus", R.string.focus_mode),
        e(SettingsGroup.Light, "awake", R.string.keep_screen_on),
        e(SettingsGroup.Light, "rotation", R.string.rotation),
        e(SettingsGroup.Comics, "turn", R.string.page_turn),
        e(SettingsGroup.Comics, "spread", R.string.pages_on_screen),
        e(SettingsGroup.Comics, "manga", R.string.manga_mode),
        e(SettingsGroup.Comics, "crop", R.string.crop_margins),
        e(SettingsGroup.Comics, "split", R.string.split_wide),
        e(SettingsGroup.Comics, "strip", R.string.film_strip),
        e(SettingsGroup.Comics, "fixColors", R.string.fix_colors),
        e(SettingsGroup.Comics, "denoise", R.string.denoise),
        e(SettingsGroup.Comics, "sharp", R.string.sharpness),
        e(SettingsGroup.Comics, "taps", R.string.tap_zones),
        e(SettingsGroup.Comics, "volume", R.string.volume_keys),
        e(SettingsGroup.Comics, "keys", R.string.keys_title, "bluetooth teclado keyboard"),
        e(SettingsGroup.Comics, "haptic", R.string.turn_haptic),
        e(SettingsGroup.Comics, "sound", R.string.turn_sound),
        e(SettingsGroup.Comics, "gestures", R.string.section_gestures),
        e(SettingsGroup.Comics, "ground", R.string.reader_ground),
        e(SettingsGroup.Comics, "spine", R.string.spine_shadow),
        e(SettingsGroup.Comics, "texture", R.string.paper_texture),
        e(SettingsGroup.Comics, "speed", R.string.turn_speed),
        e(SettingsGroup.Books, "bookTheme", R.string.book_theme, "tema cor papel noite paper night"),
        e(SettingsGroup.Books, "bookSize", R.string.book_size),
        e(SettingsGroup.Books, "bookFont", R.string.book_font, "literata bitter atkinson dyslexic"),
        e(SettingsGroup.Books, "bookLines", R.string.book_line_height),
        e(SettingsGroup.Books, "bookJustify", R.string.book_justify),
        e(SettingsGroup.Books, "bookHyphens", R.string.book_hyphens),
        e(SettingsGroup.Books, "bookBold", R.string.book_bold),
        e(SettingsGroup.Books, "bookPublisher", R.string.book_publisher),
        e(SettingsGroup.Books, "bookColumns", R.string.book_columns),
        e(SettingsGroup.Books, "bookTurn", R.string.page_turn, "livro book"),
        e(SettingsGroup.Books, "bookProgress", R.string.book_progress, "kindle tempo time"),
        e(SettingsGroup.Sync, "sync", R.string.sync_title),
        e(SettingsGroup.Sync, "upload", R.string.upload_title),
        e(SettingsGroup.Sync, "backup", R.string.backup_title),
        e(SettingsGroup.Sync, "autobackup", R.string.autobackup_title),
        e(SettingsGroup.Wifi, null, R.string.wifi_title),
        e(SettingsGroup.Storage, null, R.string.storage_title),
        e(SettingsGroup.Alerts, "alerts", R.string.alerts_title),
        e(SettingsGroup.Alerts, "widgets", R.string.widgets_title),
        e(SettingsGroup.Alerts, "dream", R.string.dream_title),
        e(SettingsGroup.Stats, null, R.string.stats_title),
        e(SettingsGroup.About, "whatsnew", R.string.whats_new),
        e(SettingsGroup.About, "updates", R.string.updates),
        e(SettingsGroup.About, "report", R.string.report_title),
        e(SettingsGroup.About, null, R.string.about),
    )
}

/** Settings that match [query], for the library's search: (name, group name, target like "light/ink"). */
internal fun searchSettings(query: String): List<Triple<String, String, String>> {
    val q = plain(query.trim())
    if (q.length < 2) return emptyList()
    return entries().filter { e -> plain(e.title).contains(q) || plain(e.extra).contains(q) }
        .map { e -> Triple(e.title, e.group.title, e.group.key + (e.key?.let { "/$it" } ?: "")) }
}

private fun plain(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** What each group shows under its name: the state of its main settings. */
@Composable
private fun summary(group: SettingsGroup, state: LibraryState): String {
    val context = LocalContext.current
    return when (group) {
        SettingsGroup.Library -> when (state.folders.size) {
            0 -> str(R.string.settings_folders_none)
            1 -> str(R.string.settings_folders_one)
            else -> str(R.string.settings_folders_n, state.folders.size)
        }
        SettingsGroup.Servers -> if (state.servers.isEmpty()) str(R.string.settings_servers_none) else state.servers.joinToString(", ") { it.name }
        SettingsGroup.Appearance -> UiSettings.get(context).let { ui -> ui.preset.label + " · " + ui.backdrop.label }
        SettingsGroup.Light -> LightSettings.get(context).let { l ->
            listOfNotNull(
                if (l.level < 0f) str(R.string.settings_light_system) else str(R.string.settings_light_custom),
                str(R.string.settings_light_warm).takeIf { l.schedule != WarmSchedule.Off },
                l.ink.label.takeIf { l.ink != InkLook.Off },
            ).joinToString(" · ")
        }
        SettingsGroup.Comics -> ReaderSettings.get(context).let { r -> r.turnStyle.label + " · " + r.spreadMode.label }
        SettingsGroup.Books -> io.github.christiandonatti.fwip.book.BookSettings.get(context).let { b -> b.theme.label + " · ${(b.fontSize * 100).toInt()}%" }
        SettingsGroup.Sync -> if (state.sync.place != null) str(R.string.settings_sync_on) else str(R.string.settings_sync_off)
        SettingsGroup.Wifi -> str(R.string.settings_wifi_summary)
        SettingsGroup.Storage -> str(R.string.settings_storage_summary)
        SettingsGroup.Alerts -> str(R.string.settings_alerts_summary)
        SettingsGroup.Stats -> str(R.string.settings_stats_summary)
        SettingsGroup.About -> str(R.string.settings_about_summary)
    }
}

/**
 * Settings: groups on the left (or a list on the phone), the chosen group's settings beside it.
 * Search finds a single setting and lights it up. [start] can open a group, or a row in it
 * ("light/warmth").
 */
@Composable
fun SettingsScreen(
    state: LibraryState,
    actions: LibraryActions,
    serverActions: ServerActions,
    syncActions: SyncActions,
    start: String?,
    onBrowse: (Server) -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp
        val gutter = if (maxWidth >= 600.dp) 32.dp else 20.dp
        var open by rememberSaveable { mutableStateOf(start?.substringBefore('/')) }
        var highlight by remember { mutableStateOf(start?.substringAfter('/', "")?.ifEmpty { null }) }
        val shown = SettingsGroup.of(open) ?: if (twoPane) SettingsGroup.Library else null
        var back by remember { mutableFloatStateOf(0f) }
        val closingDetail = !twoPane && open != null

        // Predictive back: the page shrinks under the finger and goes once the gesture ends.
        PredictiveBackHandler { progress: Flow<BackEventCompat> ->
            try {
                progress.collect { back = it.progress }
                if (closingDetail) { open = null; highlight = null } else onClose()
            } finally {
                back = 0f
            }
        }
        fun pick(group: SettingsGroup, key: String?) {
            highlight = key
            open = group.key
        }
        val backLayer = Modifier.graphicsLayer {
            val k = back
            scaleX = 1f - 0.08f * k
            scaleY = 1f - 0.08f * k
            translationX = 24.dp.toPx() * k
            shape = RoundedCornerShape(28.dp * k)
            clip = k > 0f
        }

        if (twoPane) {
            Row(Modifier.fillMaxSize().then(backLayer)) {
                GroupList(state, shown, onPick = ::pick, onClose = onClose, modifier = Modifier.width(380.dp).fillMaxHeight(), gutter = 24.dp)
                Box(Modifier.width(1.dp).fillMaxHeight().background(FwipColors.Stroke))
                Crossfade(shown, Modifier.weight(1f), animationSpec = tween(220), label = "settings") { g ->
                    if (g != null) {
                        CompositionLocalProvider(LocalSettingHighlight provides highlight) {
                            GroupPage(g, state, actions, serverActions, syncActions, gutter, wide = true, onBack = null, onBrowse = onBrowse)
                        }
                    }
                }
            }
        } else {
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    if (targetState != null) (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(240))) togetherWith fadeOut(tween(150))
                    else fadeIn(tween(240)) togetherWith (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(200)))
                },
                label = "settings",
            ) { g ->
                if (g == null) {
                    GroupList(state, null, onPick = ::pick, onClose = onClose, modifier = Modifier.fillMaxSize().then(backLayer), gutter = gutter)
                } else {
                    Box(Modifier.fillMaxSize().then(backLayer).background(FwipColors.Ink)) {
                        CompositionLocalProvider(LocalSettingHighlight provides highlight) {
                            GroupPage(g, state, actions, serverActions, syncActions, gutter, wide = false, onBack = { open = null; highlight = null }, onBrowse = onBrowse)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupList(
    state: LibraryState,
    selected: SettingsGroup?,
    onPick: (SettingsGroup, String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    gutter: Dp,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onClose, size = 44.dp)
                Text(str(R.string.app_settings), style = MaterialTheme.typography.headlineSmall)
            }
            SettingsSearch(query) { query = it }
            if (query.isBlank()) {
                SettingsGroup.entries.groupBy { it.sectionRes }.forEach { (section, groups) ->
                    Text(
                        str(section).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = FwipColors.TextMuted,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                    )
                    Card {
                        groups.forEachIndexed { i, g ->
                            if (i > 0) Divider()
                            GroupRow(g, summary(g, state), g == selected) { onPick(g, null) }
                        }
                    }
                }
            } else {
                val q = plain(query.trim())
                val all = remember { entries() }
                val found = all.filter { e -> plain(e.title).contains(q) || plain(e.group.title).contains(q) || plain(e.extra).contains(q) }
                if (found.isEmpty()) {
                    Text(str(R.string.settings_nothing_found), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted, modifier = Modifier.padding(6.dp))
                } else {
                    Card {
                        found.take(40).forEachIndexed { i, e ->
                            if (i > 0) Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { onPick(e.group, e.key) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                GroupIcon(e.group.glyph, FwipColors.TextSoft, size = 20.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(e.group.title, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                                }
                                Icon(Icons.Filled.KeyboardArrowRight, null, tint = FwipColors.TextMuted)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp)),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(FwipColors.Stroke))
}

@Composable
private fun GroupRow(group: SettingsGroup, summary: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) FwipColors.Amber.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) FwipColors.Amber else FwipColors.Amber.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            GroupIcon(group.glyph, if (selected) FwipColors.AmberInk else FwipColors.Amber)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group.title, style = MaterialTheme.typography.labelLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = FwipColors.TextMuted)
    }
}

@Composable
private fun SettingsSearch(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = FwipColors.TextMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(str(R.string.settings_search), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                cursorBrush = SolidColor(FwipColors.Amber),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) CircleIconButton(Icons.Filled.Close, str(R.string.close_search), { onQuery("") }, size = 40.dp)
    }
}

/** "Reset" at the top of a group, asking first. */
@Composable
fun ResetButton(onReset: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    PillButton(str(R.string.reset_defaults), { asking = true }, style = PillStyle.Ghost, height = 40.dp)
    if (asking) {
        FwipDialog({ asking = false }) {
            DialogTitle(str(R.string.reset_defaults), { asking = false })
            Text(str(R.string.reset_defaults_hint), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(str(R.string.cancel), { asking = false }, style = PillStyle.Ghost, height = 46.dp, modifier = Modifier.weight(1f))
                PillButton(str(R.string.reset), { asking = false; onReset() }, style = PillStyle.Amber, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Title row of a settings page: back (on the phone), the name, and maybe "Reset". */
@Composable
private fun PageHeader(title: String, onBack: (() -> Unit)?, onReset: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (onReset != null) ResetButton(onReset)
    }
}

/** A plain scrolling page of settings. */
@Composable
private fun Page(title: String, gutter: Dp, onBack: (() -> Unit)?, onReset: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageHeader(title, onBack, onReset)
            content()
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun GroupPage(
    group: SettingsGroup,
    state: LibraryState,
    actions: LibraryActions,
    serverActions: ServerActions,
    syncActions: SyncActions,
    gutter: Dp,
    wide: Boolean,
    onBack: (() -> Unit)?,
    onBrowse: (Server) -> Unit,
) {
    val context = LocalContext.current
    when (group) {
        SettingsGroup.Library -> Page(group.title, gutter, onBack) { LibrarySettings(state, actions) }
        SettingsGroup.Servers -> Column(Modifier.fillMaxSize()) {
            if (onBack != null) Box(Modifier.padding(start = gutter, top = 16.dp)) { CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp) }
            Box(Modifier.padding(top = 16.dp)) { ServersTab(state.servers, serverActions, wide = false, gutter = gutter, onBrowse = onBrowse) }
        }
        SettingsGroup.Appearance -> AppearanceScreen(
            state, gutter, wide, actions.pickBackdrop, onBack, handleBack = false,
            initial = LocalSettingHighlight.current,
            onReset = { UiSettings.get(context).resetLook() },
        )
        SettingsGroup.Light -> Page(group.title, gutter, onBack, onReset = { LightSettings.get(context).reset() }) {
            LightComfortSettings(LightSettings.get(context))
        }
        SettingsGroup.Comics -> Page(group.title, gutter, onBack, onReset = { ReaderSettings.get(context).reset() }) {
            ComicReadingSettings(ReaderSettings.get(context))
        }
        SettingsGroup.Books -> Page(group.title, gutter, onBack, onReset = { io.github.christiandonatti.fwip.book.BookSettings.get(context).reset() }) {
            io.github.christiandonatti.fwip.book.BookTextSettings(io.github.christiandonatti.fwip.book.BookSettings.get(context), LightSettings.get(context))
        }
        SettingsGroup.Sync -> Page(group.title, gutter, onBack) { SyncSettings(state.sync, state.folders, state.servers, syncActions) }
        SettingsGroup.Wifi -> WifiScreen(state.folders, state.servers, gutter, onReceived = actions.refresh, onBack = onBack, handleBack = false)
        SettingsGroup.Storage -> StorageScreen(state, gutter, onDelete = actions.deleteComics, onBack = onBack, handleBack = false)
        SettingsGroup.Alerts -> Page(group.title, gutter, onBack) { AlertsAndWidgets(actions) }
        SettingsGroup.Stats -> StatsScreen(state, actions.stats, gutter, wide, onBack = onBack, handleBack = false)
        SettingsGroup.About -> {
            var updates by remember { mutableStateOf(false) }
            var report by remember { mutableStateOf(false) }
            var whatsNew by remember { mutableStateOf(false) }
            AboutScreen(gutter, onBack = onBack, handleBack = false, top = {
                Text(str(R.string.settings_about), style = MaterialTheme.typography.headlineSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.settingAnchor("whatsnew")) { SheetButton(str(R.string.whats_new), { whatsNew = true }) }
                    Box(Modifier.settingAnchor("updates")) {
                        SheetButton(str(R.string.updates), { updates = true }, trailing = if (io.github.christiandonatti.fwip.data.Updates.available != null) {
                            { Box(Modifier.size(10.dp).clip(CircleShape).background(FwipColors.Amber)) }
                        } else null)
                    }
                    Box(Modifier.settingAnchor("report")) { SheetButton(str(R.string.report_title), { report = true }) }
                }
            })
            if (updates) UpdatesDialog(onDismiss = { updates = false })
            if (report) ReportDialog(onSave = actions.saveReport, onDismiss = { report = false })
            if (whatsNew) WhatsNewDialog(all = true, onDismiss = { whatsNew = false })
        }
    }
}

@Composable
private fun LibrarySettings(state: LibraryState, actions: LibraryActions) {
    Column(Modifier.settingAnchor("folders"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section(str(R.string.library_folders))
        if (state.folders.isEmpty()) {
            Text(str(R.string.no_folders), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
        }
        state.folders.forEach { uri ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FwipColors.Surface)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FolderIcon(FwipColors.TextSoft)
                Text(folderLabel(uri, state.servers), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                CircleIconButton(Icons.Filled.Delete, str(R.string.remove_folder), { actions.removeFolder(uri) }, size = 40.dp)
            }
        }
        PillButton(str(R.string.add_folder), actions.addFolder, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth(), leading = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        Text(str(R.string.settings_net_folders_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
    }
    Section(str(R.string.settings_section_more))
    Box(Modifier.settingAnchor("refresh")) {
        SheetButton(str(R.string.refresh_library), actions.refresh, leading = { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp), tint = FwipColors.Text) })
    }
    Box(Modifier.settingAnchor("cbl")) {
        SheetButton(str(R.string.cbl_import), actions.importReadingList, leading = { StackIcon(FwipColors.Text) })
    }
    Text(str(R.string.cbl_import_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
}

/** Notices, widgets on the home screen, and the screen saver. */
@Composable
private fun AlertsAndWidgets(actions: LibraryActions) {
    val context = LocalContext.current
    var alerts by remember { mutableStateOf(io.github.christiandonatti.fwip.data.NewIssues.enabled(context)) }
    Section(str(R.string.settings_alerts))
    Toggle(str(R.string.alerts_title), str(R.string.alerts_hint), alerts, { on -> alerts = on; actions.setAlerts(on) }, key = "alerts")

    Section(str(R.string.widgets_title))
    Text(str(R.string.widgets_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, modifier = Modifier.settingAnchor("widgets"))
    val pinnable = remember { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
    WIDGETS.forEach { (cls, title, desc) ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FwipColors.Surface)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(str(title), style = MaterialTheme.typography.labelLarge)
                Text(str(desc), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            }
            if (pinnable) PillButton(str(R.string.widget_add), { pinWidget(context, cls) }, style = PillStyle.Ghost, height = 38.dp)
        }
    }

    Section(str(R.string.dream_title))
    Text(str(R.string.dream_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, modifier = Modifier.settingAnchor("dream"))
    PillButton(str(R.string.dream_open), {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, style = PillStyle.Ghost, height = 44.dp)
}

private val WIDGETS = listOf(
    Triple(io.github.christiandonatti.fwip.widget.ContinueReadingReceiver::class.java, R.string.widget_title, R.string.widget_description),
    Triple(io.github.christiandonatti.fwip.widget.UpNextReceiver::class.java, R.string.widget_up_next, R.string.widget_up_next_desc),
    Triple(io.github.christiandonatti.fwip.widget.CollectionReceiver::class.java, R.string.widget_collection, R.string.widget_collection_desc),
    Triple(io.github.christiandonatti.fwip.widget.StatsReceiver::class.java, R.string.widget_stats, R.string.widget_stats_desc),
    Triple(io.github.christiandonatti.fwip.widget.NewArrivalsReceiver::class.java, R.string.widget_new, R.string.widget_new_desc),
    Triple(io.github.christiandonatti.fwip.widget.ShortcutsReceiver::class.java, R.string.widget_shortcuts, R.string.widget_shortcuts_desc),
)

/** Asks the launcher to place one of the widgets (it shows its own confirmation). */
private fun pinWidget(context: Context, receiver: Class<*>) {
    runCatching { AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null) }
}
