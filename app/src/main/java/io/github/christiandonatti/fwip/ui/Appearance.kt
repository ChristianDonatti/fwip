package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.reader.ReaderLookSettings
import io.github.christiandonatti.fwip.reader.ReaderSettings
import io.github.christiandonatti.fwip.reader.Toggle
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.theme.Palette
import io.github.christiandonatti.fwip.ui.theme.ThemePreset
import io.github.christiandonatti.fwip.ui.theme.TitleFont

@Composable
private fun Modifier.choice(on: Boolean): Modifier = this
    .clip(RoundedCornerShape(18.dp))
    .background(if (on) FwipColors.Amber.copy(alpha = 0.10f) else FwipColors.Surface)
    .border(if (on) 2.dp else 1.dp, if (on) FwipColors.Amber else FwipColors.Stroke, RoundedCornerShape(18.dp))

@Composable
private fun Heading(text: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
    }
}

/** The five ready-made looks plus System, each drawn as a tiny screen. */
@Composable
fun ThemePicker(columns: Int = 3) {
    val ui = UiSettings.get(LocalContext.current)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePreset.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    val on = ui.preset == preset
                    Column(
                        Modifier
                            .weight(1f)
                            .choice(on)
                            .clickable(role = Role.RadioButton) { ui.updatePreset(preset) }
                            .semantics { selected = on }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeThumb(preset, Modifier.fillMaxWidth().height(70.dp))
                        Text(preset.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A theme in miniature: its background, a title in its font and its first colour. */
@Composable
private fun ThemeThumb(preset: ThemePreset, modifier: Modifier) {
    if (preset == ThemePreset.System) {
        Row(modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, FwipColors.Stroke, RoundedCornerShape(12.dp))) {
            ThemeThumb(ThemePreset.Paper, Modifier.weight(1f).fillMaxSize())
            ThemeThumb(ThemePreset.Night, Modifier.weight(1f).fillMaxSize())
        }
        return
    }
    val p = Palette.of(preset, preset.accents.first().color)
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.ink)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        if (p.halftone) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 7.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) { drawCircle(Color.White.copy(alpha = 0.12f), 1.dp.toPx(), Offset(x, y)); x += step }
                    y += step
                }
            }
        }
        Text(
            "Aa",
            fontFamily = preset.font.family,
            fontSize = (18 * preset.font.scale).sp,
            color = p.text,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Box(Modifier.align(Alignment.BottomStart).width(34.dp).height(8.dp).clip(CircleShape).background(p.accent))
    }
}

/** The current theme's colours, plus following the cover or the wallpaper. */
@Composable
fun AccentPicker() {
    val ui = UiSettings.get(LocalContext.current)
    val theme = ui.resolved(androidx.compose.foundation.isSystemInDarkTheme())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            theme.accents.forEachIndexed { i, a ->
                val on = ui.accentMode == AccentMode.Theme && ui.accentIndex == i
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 1.dp, if (on) FwipColors.Text else FwipColors.Stroke, CircleShape)
                        .padding(if (on) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(a.color)
                        .clickable(role = Role.RadioButton) { ui.updateAccent(i) }
                        .semantics {
                            contentDescription = a.label
                            selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, null, tint = Palette.inkFor(a.color), modifier = Modifier.size(18.dp))
                }
            }
        }
        ModeRow(str(R.string.accent_follow_cover), str(R.string.accent_follow_cover_hint), ui.accentMode == AccentMode.Cover) {
            ui.updateAccentMode(if (ui.accentMode == AccentMode.Cover) AccentMode.Theme else AccentMode.Cover)
        }
        if (ui.wallpaperAvailable) {
            ModeRow(str(R.string.accent_wallpaper), str(R.string.accent_wallpaper_hint), ui.accentMode == AccentMode.Wallpaper) {
                ui.updateAccentMode(if (ui.accentMode == AccentMode.Wallpaper) AccentMode.Theme else AccentMode.Wallpaper)
            }
        }
    }
}

@Composable
private fun ModeRow(title: String, hint: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .choice(on)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (on) FwipColors.Amber else FwipColors.TextMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(FwipColors.Amber))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
    }
}

/** Theme and colour together, as on the welcome screen. */
@Composable
fun AppearancePicker(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(str(R.string.appearance_theme), style = MaterialTheme.typography.labelLarge)
        ThemePicker(columns = 3)
        Text(str(R.string.appearance_accent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        AccentPicker()
    }
}

private enum class LookTab(val labelRes: Int) {
    Style(R.string.look_tab_style), Covers(R.string.look_tab_covers), Home(R.string.look_tab_home), Icon(R.string.look_tab_icon), Reader(R.string.look_tab_reader);

    val label: String get() = str(labelRes)
}

/**
 * Settings › Appearance: theme and colour, background, fonts and greeting; covers and carousel;
 * what the home screen shows; the launcher icon; how the reader looks.
 */
@Composable
fun AppearanceScreen(
    state: LibraryState,
    gutter: Dp,
    wide: Boolean,
    onPickImage: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ui = UiSettings.get(LocalContext.current)
    var tab by rememberSaveable { mutableStateOf(LookTab.Style) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
            Text(str(R.string.appearance), style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(CircleShape)
                    .background(FwipColors.Surface)
                    .border(1.dp, FwipColors.Stroke, CircleShape)
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LookTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(if (on) FwipColors.Text else Color.Transparent)
                            .clickable(role = Role.Tab) { tab = t }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1)
                    }
                }
            }
            when (tab) {
                LookTab.Style -> StyleTab(ui, wide, onPickImage)
                LookTab.Covers -> CoversTab(ui)
                LookTab.Home -> HomeTab(ui, state)
                LookTab.Icon -> IconTab(ui)
                LookTab.Reader -> ReaderTab(ui)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StyleTab(ui: UiSettings, wide: Boolean, onPickImage: () -> Unit) {
    val context = LocalContext.current
    Heading(str(R.string.appearance_theme), str(R.string.theme_hint))
    ThemePicker(columns = if (wide) 3 else 2)

    Heading(str(R.string.appearance_accent), str(R.string.accent_hint))
    AccentPicker()

    Heading(str(R.string.backdrop_title))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Backdrop.entries.forEach { b ->
            val on = ui.backdrop == b
            Column(
                Modifier
                    .weight(1f)
                    .choice(on)
                    .clickable(role = Role.RadioButton) {
                        if (b == Backdrop.Image && !Backdrops.file(context).exists()) onPickImage()
                        ui.updateBackdrop(b)
                    }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BackdropThumb(b, Modifier.fillMaxWidth().height(64.dp))
                Text(b.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(b.description, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 3)
            }
        }
    }
    if (ui.backdrop == Backdrop.Image) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(str(R.string.backdrop_pick), onPickImage, style = PillStyle.Ghost, height = 46.dp)
            Text(str(R.string.backdrop_cover_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, modifier = Modifier.weight(1f))
        }
    }

    Heading(str(R.string.font_title))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TitleFont.entries.forEach { f ->
            val on = ui.titleFont == f
            Column(
                Modifier
                    .weight(1f)
                    .choice(on)
                    .clickable(role = Role.RadioButton) { ui.updateTitleFont(f) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Pepper", fontFamily = f.family, fontSize = (19 * f.scale).sp, maxLines = 1, color = FwipColors.Text)
                Text(f.label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
            }
        }
    }

    Heading(str(R.string.greeting_title), str(R.string.greeting_hint))
    val greeting = ui.sections.firstOrNull { it.part == HomePart.Greeting }
    Toggle(str(R.string.greeting_show), greetingText(ui.name), greeting?.on == true) { on -> ui.setSection(HomePart.Greeting.name, on) }
    NameField(ui)
}

@Composable
private fun NameField(ui: UiSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(str(R.string.greeting_name), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, CircleShape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (ui.name.isEmpty()) Text(str(R.string.greeting_name_hint), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted)
                androidx.compose.foundation.text.BasicTextField(
                    value = ui.name,
                    onValueChange = ui::updateName,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(FwipColors.Amber),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** "Good evening, Christian", by the hour. */
fun greetingText(name: String): String {
    val hour = java.time.LocalTime.now().hour
    val base = when (hour) {
        in 5..11 -> str(R.string.greeting_morning)
        in 12..17 -> str(R.string.greeting_afternoon)
        else -> str(R.string.greeting_evening)
    }
    return if (name.isBlank()) base else "$base, ${name.trim()}"
}

@Composable
private fun BackdropThumb(b: Backdrop, modifier: Modifier) {
    val accent = FwipColors.Amber
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(FwipColors.Ink).border(1.dp, FwipColors.Stroke, RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            when (b) {
                Backdrop.Glow -> drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF2E6BB0).copy(alpha = 0.7f), Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width),
                    radius = size.width * 1.2f, center = Offset(size.width * 0.15f, 0f),
                )
                Backdrop.Solid -> {}
                Backdrop.Living -> {
                    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF6D3AD6).copy(alpha = 0.6f), Color.Transparent), center = Offset(size.width * 0.2f, size.height * 0.3f), radius = size.width * 0.6f), radius = size.width, center = Offset(size.width * 0.2f, size.height * 0.3f))
                    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), center = Offset(size.width * 0.85f, size.height), radius = size.width * 0.5f), radius = size.width, center = Offset(size.width * 0.85f, size.height))
                }
                Backdrop.Image -> {
                    var y = 4f
                    while (y < size.height) {
                        drawRect(Color.Gray.copy(alpha = 0.35f), topLeft = Offset(6f, y), size = androidx.compose.ui.geometry.Size(size.width - 12f, 10.dp.toPx()))
                        y += 14.dp.toPx()
                    }
                }
            }
        }
    }
}

@Composable
private fun CoversTab(ui: UiSettings) {
    Heading(str(R.string.corners_title))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CoverCorners.entries.forEach { c ->
            val on = ui.corners == c
            Column(
                Modifier
                    .weight(1f)
                    .choice(on)
                    .clickable(role = Role.RadioButton) { ui.updateCorners(c) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(12.dp * c.factor)).background(Color(0xFFD9643A)))
                Text(c.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Toggle(str(R.string.cover_shadow), str(R.string.cover_shadow_hint), ui.coverShadow, ui::updateCoverShadow)
    Toggle(str(R.string.cover_titles), str(R.string.cover_titles_hint), ui.coverTitles, ui::updateCoverTitles)

    Heading(str(R.string.read_mark_title))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReadMark.entries.forEach { m ->
            val on = ui.readMark == m
            Column(
                Modifier
                    .weight(1f)
                    .choice(on)
                    .clickable(role = Role.RadioButton) { ui.updateReadMark(m) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadMarkThumb(m)
                Text(m.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    Heading(str(R.string.appearance_cover_size), str(R.string.appearance_pinch_hint))
    if (ui.coverScale !in 0.97f..1.03f) {
        PillButton(str(R.string.appearance_cover_reset), { ui.scaleCovers(1f / ui.coverScale); ui.saveCoverScale() }, style = PillStyle.Ghost, height = 46.dp)
    }

    Heading(str(R.string.carousel_title))
    Toggle(str(R.string.carousel_show), str(R.string.carousel_show_hint), ui.showCarousel, ui::updateShowCarousel)
    if (ui.showCarousel) {
        CarouselStyle.entries.forEach { k ->
            val on = ui.carousel == k
            Row(
                Modifier
                    .fillMaxWidth()
                    .choice(on)
                    .clickable(role = Role.RadioButton) { ui.updateCarousel(k) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CarouselThumb(k, Modifier.width(180.dp).height(84.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(k.label, style = MaterialTheme.typography.labelLarge)
                    Text(k.description, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ReadMarkThumb(m: ReadMark) {
    Box(
        Modifier
            .size(56.dp, 84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2E6BB0))
            .graphicsLayer { alpha = if (m == ReadMark.Faded) 0.4f else 1f }
    ) {
        when (m) {
            ReadMark.Badge -> Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(FwipColors.Amber),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = FwipColors.AmberInk, modifier = Modifier.size(12.dp)) }
            ReadMark.Ribbon -> Box(
                Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = 8.dp).rotate(45f).width(70.dp).background(FwipColors.Amber).padding(vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) { Text(str(R.string.read_ribbon), fontSize = 7.sp, color = FwipColors.AmberInk, maxLines = 1) }
            ReadMark.Faded -> {}
        }
    }
}

@Composable
private fun CarouselThumb(style: CarouselStyle, modifier: Modifier) {
    val colors = listOf(Color(0xFF2E6BB0), Color(0xFFB23A2A), FwipColors.Amber, Color(0xFF6D3AD6), Color(0xFF3F7A33))
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(FwipColors.Ink)) {
        val h = size.height * 0.62f
        val w = h * 2f / 3f
        val cx = size.width / 2f
        val cy = size.height / 2f
        colors.indices.sortedByDescending { kotlin.math.abs(it - 2) }.forEach { i ->
            val d = i - 2
            val c = colors[i]
            when (style) {
                CarouselStyle.Flat -> {
                    val s = if (d == 0) 1.1f else 0.85f
                    drawRoundRect(c.copy(alpha = if (d == 0) 1f else 0.6f), topLeft = Offset(cx + d * w * 1.25f - w * s / 2, cy - h * s / 2), size = androidx.compose.ui.geometry.Size(w * s, h * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
                CarouselStyle.Tilt -> {
                    val s = if (d == 0) 1.1f else 0.9f
                    val squeeze = if (d == 0) 1f else 0.55f
                    drawRoundRect(c.copy(alpha = if (d == 0) 1f else 0.8f), topLeft = Offset(cx + d * w * 0.8f - w * s * squeeze / 2, cy - h * s / 2), size = androidx.compose.ui.geometry.Size(w * s * squeeze, h * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
                CarouselStyle.Fan -> {
                    rotate(d * 11f, Offset(cx, size.height * 1.4f)) {
                        drawRoundRect(c, topLeft = Offset(cx - w / 2, size.height - h - 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTab(ui: UiSettings, state: LibraryState) {
    var picking by remember { mutableStateOf<HomePart?>(null) }
    Heading(str(R.string.home_parts_title), str(R.string.home_parts_hint))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ui.sections.forEachIndexed { i, s ->
            val pinned = s.ref.isNotEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (pinned) FwipColors.Amber.copy(alpha = 0.08f) else FwipColors.Surface)
                    .border(1.dp, if (pinned) FwipColors.Amber.copy(alpha = 0.35f) else FwipColors.Stroke, RoundedCornerShape(16.dp))
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                    .graphicsLayer { alpha = if (s.on) 1f else 0.6f },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column {
                    SmallIconButton(Icons.Filled.KeyboardArrowUp, str(R.string.move_up), enabled = i > 0) { ui.moveSection(s.key, -1) }
                    SmallIconButton(Icons.Filled.KeyboardArrowDown, str(R.string.move_down), enabled = i < ui.sections.lastIndex) { ui.moveSection(s.key, 1) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sectionTitle(s), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sectionHint(s), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2)
                }
                if (pinned) {
                    CircleIconButton(Icons.Filled.Close, str(R.string.unpin), { ui.unpin(s.key) }, size = 38.dp)
                } else {
                    androidx.compose.material3.Switch(
                        checked = s.on,
                        onCheckedChange = { ui.setSection(s.key, it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = FwipColors.Amber, checkedThumbColor = FwipColors.AmberInk),
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(str(R.string.pin_collection), { picking = HomePart.Collection }, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.weight(1f))
        PillButton(str(R.string.pin_series), { picking = HomePart.Series }, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.weight(1f))
    }

    picking?.let { part ->
        FwipDialog({ picking = null }) {
            DialogTitle(str(if (part == HomePart.Collection) R.string.pin_collection else R.string.pin_series), { picking = null })
            val options: List<Pair<String, String>> = if (part == HomePart.Collection) {
                state.collections.map { it.id to it.name }
            } else {
                state.series.map { it.first.key to it.first.title }
            }
            if (options.isEmpty()) {
                Text(str(if (part == HomePart.Collection) R.string.no_collections_yet else R.string.series_hint), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            }
            options.forEach { (ref, title) ->
                val already = ui.sections.any { it.part == part && it.ref == ref }
                val pick = {
                    if (!already) ui.pin(part, ref, title)
                    picking = null
                }
                if (already) {
                    SheetButton(title, pick, trailing = { Icon(Icons.Filled.Check, null, tint = FwipColors.Amber) })
                } else {
                    SheetButton(title, pick)
                }
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp, 26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) FwipColors.Text else FwipColors.TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

fun sectionTitle(s: HomeSection): String = when (s.part) {
    HomePart.Greeting -> str(R.string.part_greeting)
    HomePart.Carousel -> str(R.string.part_carousel)
    HomePart.Continue -> str(R.string.continue_reading)
    HomePart.Shelf -> str(R.string.shelf)
    HomePart.Week -> str(R.string.week_title)
    HomePart.Want -> str(R.string.smart_want)
    HomePart.Recent -> str(R.string.smart_recent)
    HomePart.Collection -> str(R.string.part_pinned_collection, s.title)
    HomePart.Series -> str(R.string.part_pinned_series, s.title)
}

private fun sectionHint(s: HomeSection): String = when (s.part) {
    HomePart.Greeting -> str(R.string.part_greeting_hint)
    HomePart.Carousel -> str(R.string.part_carousel_hint)
    HomePart.Continue -> str(R.string.part_continue_hint)
    HomePart.Shelf -> str(R.string.part_shelf_hint)
    HomePart.Week -> str(R.string.part_week_hint)
    HomePart.Want -> str(R.string.part_want_hint)
    HomePart.Recent -> str(R.string.part_recent_hint)
    HomePart.Collection -> str(R.string.part_pinned_collection_hint)
    HomePart.Series -> str(R.string.part_pinned_series_hint)
}

@Composable
private fun IconTab(ui: UiSettings) {
    Heading(str(R.string.icon_title), str(R.string.icon_hint))
    AppIcon.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { icon ->
                val on = ui.appIcon == icon
                Row(
                    Modifier
                        .weight(1f)
                        .choice(on)
                        .clickable(role = Role.RadioButton) { ui.updateAppIcon(icon) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconPreview(AppIcons.variantFor(icon, FwipColors.Amber), 64.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(icon.label, style = MaterialTheme.typography.labelLarge)
                        Text(icon.description, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** The launcher icon drawn the way the adaptive icon draws it. */
@Composable
fun IconPreview(v: IconVariant, size: Dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(v.bg))) {
        val s = this.size.width / 72f
        // Same drawing as the launcher foreground, in its 72-unit visible area.
        val balloon = Path().apply {
            moveTo(36f * s, 13f * s)
            cubicTo(50f * s, 13f * s, 60f * s, 21f * s, 60f * s, 31f * s)
            cubicTo(60f * s, 41f * s, 50f * s, 49f * s, 36f * s, 49f * s)
            cubicTo(33f * s, 49f * s, 30f * s, 48.6f * s, 27.4f * s, 47.9f * s)
            lineTo(17f * s, 56f * s)
            lineTo(19.5f * s, 45.2f * s)
            cubicTo(14.8f * s, 41.8f * s, 12f * s, 36.7f * s, 12f * s, 31f * s)
            cubicTo(12f * s, 21f * s, 22f * s, 13f * s, 36f * s, 13f * s)
            close()
        }
        drawPath(balloon, Color(v.balloon))
        val arc = Path().apply {
            moveTo(25f * s, 33f * s)
            cubicTo(25f * s, 25f * s, 31f * s, 21f * s, 37f * s, 22f * s)
            cubicTo(42f * s, 23f * s, 46f * s, 27f * s, 46f * s, 31f * s)
        }
        val head = Path().apply {
            moveTo(41.5f * s, 29.5f * s)
            lineTo(46f * s, 32.5f * s)
            lineTo(49f * s, 27.8f * s)
        }
        val stroke = Stroke(width = 3.4f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(arc, Color(v.arrow), style = stroke)
        drawPath(head, Color(v.arrow), style = stroke)
    }
}

@Composable
private fun ReaderTab(ui: UiSettings) {
    val context = LocalContext.current
    val settings = remember { ReaderSettings(context) }
    Heading(str(R.string.look_tab_reader), str(R.string.reader_look_hint))
    ReaderLookSettings(settings)
    Toggle(str(R.string.cover_animation), str(R.string.cover_animation_hint), ui.coverAnimation, ui::updateCoverAnimation)
}
