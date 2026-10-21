@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.ui.CircleIconButton
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlin.math.roundToInt

/** Speech-balloon icon drawn to match the stroke icons. */
@Composable
internal fun BalloonIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val u = size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(5 * u, 5 * u)
            lineTo(19 * u, 5 * u)
            quadraticTo(21 * u, 5 * u, 21 * u, 7 * u)
            lineTo(21 * u, 15 * u)
            quadraticTo(21 * u, 17 * u, 19 * u, 17 * u)
            lineTo(11 * u, 17 * u)
            lineTo(6 * u, 21 * u)
            lineTo(6 * u, 17 * u)
            lineTo(5 * u, 17 * u)
            quadraticTo(3 * u, 17 * u, 3 * u, 15 * u)
            lineTo(3 * u, 7 * u)
            quadraticTo(3 * u, 5 * u, 5 * u, 5 * u)
            close()
        }
        drawPath(path, color, style = Stroke(width = 2f * u))
    }
}

/** Comic page with three panels, for the panel-by-panel mode. */
@Composable
internal fun PanelsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val u = size.width / 24f
        val s = Stroke(width = 2f * u)
        val r = CornerRadius(1.5f * u)
        drawRoundRect(color, Offset(3 * u, 3 * u), Size(8 * u, 9 * u), r, style = s)
        drawRoundRect(color, Offset(13 * u, 3 * u), Size(8 * u, 9 * u), r, style = s)
        drawRoundRect(color, Offset(3 * u, 14 * u), Size(18 * u, 7 * u), r, style = s)
    }
}

/** Toggle for a guided mode; shows its label when there is room. */
@Composable
private fun GuideToggle(on: Boolean, label: String, showLabel: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (on) FwipColors.Amber else FwipColors.Surface)
            .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val c = if (on) FwipColors.AmberInk else FwipColors.Text
        icon(c)
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = c)
    }
}

/** Floating controls while reading panel by panel or balloon by balloon. */
@Composable
internal fun GuideBar(
    label: String,
    busy: Boolean,
    busyLabel: String,
    rtl: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Color(0xE6121318))
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(Icons.Filled.Close, str(R.string.exit_guided), onExit, size = 48.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
                    Text(busyLabel, style = MaterialTheme.typography.labelMedium, color = FwipColors.TextSoft)
                }
            } else {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // In manga mode "next" sits on the left, where the reading goes.
        if (rtl) {
            CircleIconButton(Icons.Filled.KeyboardArrowLeft, str(R.string.next), onNext, size = 48.dp, filled = true)
            CircleIconButton(Icons.Filled.KeyboardArrowRight, str(R.string.previous), onPrev, size = 48.dp)
        } else {
            CircleIconButton(Icons.Filled.KeyboardArrowLeft, str(R.string.previous), onPrev, size = 48.dp)
            CircleIconButton(Icons.Filled.KeyboardArrowRight, str(R.string.next), onNext, size = 48.dp, filled = true)
        }
    }
}

/**
 * Darkens the page around what is being read: softly around a balloon (with an amber outline),
 * almost black around a panel so the neighbours do not distract.
 */
@Composable
internal fun GuideSpotlight(target: androidx.compose.ui.geometry.Rect?, alpha: Float, panel: Boolean) {
    if (target == null || alpha <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val pad = if (panel) 2.dp.toPx() else 10.dp.toPx()
        val r = target.inflate(pad)
        val corner = if (panel) 4.dp.toPx() else 22.dp.toPx()
        val hole = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(corner)))
        }
        val full = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        }
        val mask = androidx.compose.ui.graphics.Path().apply {
            op(full, hole, androidx.compose.ui.graphics.PathOperation.Difference)
        }
        drawPath(mask, Color(0xFF0B0C10).copy(alpha = (if (panel) 0.9f else 0.62f) * alpha))
        if (!panel) {
            drawRoundRect(
                FwipColors.Amber.copy(alpha = 0.9f * alpha),
                topLeft = r.topLeft,
                size = Size(r.width, r.height),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun ReaderChrome(
    visible: Boolean,
    title: String,
    pageLabel: String,
    spreadIndex: Int,
    spreadCount: Int,
    rtl: Boolean,
    guide: GuideKind?,
    onSeek: (Int) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onGuide: (GuideKind) -> Unit,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onPages: () -> Unit,
    /** Panel and balloon reading need whole pages; vertical scrolling has none. */
    guidedAvailable: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxWidth >= 600.dp
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
                CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back_to_library), onBack, size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pageLabel, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
                DrawnIconButton(str(if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark), onBookmark, on = bookmarked) {
                    BookmarkIcon(it, filled = bookmarked)
                }
                Spacer(Modifier.width(8.dp))
                if (guidedAvailable) {
                    GuideToggle(guide == GuideKind.Panels, str(R.string.panels), roomy, { onGuide(GuideKind.Panels) }) { PanelsIcon(it) }
                    Spacer(Modifier.width(8.dp))
                    GuideToggle(guide == GuideKind.Balloons, str(R.string.balloons), roomy, { onGuide(GuideKind.Balloons) }) { BalloonIcon(it) }
                    Spacer(Modifier.width(8.dp))
                }
                CircleIconButton(Icons.Filled.Settings, str(R.string.reading_settings), onSettings, size = 44.dp)
            }
        }
        AnimatedVisibility(
            visible = visible && spreadCount > 1 && guide == null,
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
                    .padding(start = 8.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DrawnIconButton(str(R.string.pages_title), onPages, size = 48.dp) { GridIcon(it) }
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
internal fun SettingsDialog(settings: ReaderSettings, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF16171E))
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp)),
        ) {
            Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.reading_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 40.dp)
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Section(str(R.string.section_pages))
                Choice(str(R.string.page_turn), TurnStyle.entries, settings.turnStyle, { it.label }, settings::updateTurn)
                Choice(str(R.string.pages_on_screen), SpreadMode.entries, settings.spreadMode, { it.label }, settings::updateSpread)
                Toggle(str(R.string.manga_mode), str(R.string.manga_mode_hint), settings.mangaMode, settings::updateManga)
                Toggle(str(R.string.crop_margins), str(R.string.crop_margins_hint), settings.cropMargins, settings::updateCrop)

                Section(str(R.string.section_touch))
                Choice(str(R.string.tap_zones), TapZones.entries, settings.tapZones, { it.label }, settings::updateTapZones)
                TapZonePreview(settings.tapZones, settings.mangaMode)
                Toggle(str(R.string.volume_keys), str(R.string.volume_keys_hint), settings.volumeKeys, settings::updateVolume)

                Section(str(R.string.section_comfort))
                Toggle(
                    str(R.string.system_brightness),
                    str(R.string.system_brightness_hint),
                    settings.brightness < 0f,
                ) { system -> settings.updateBrightness(if (system) -1f else 0.6f) }
                if (settings.brightness >= 0f) {
                    LevelSlider(str(R.string.brightness), settings.brightness, 0.02f..1f, settings::updateBrightness)
                }
                LevelSlider(str(R.string.warm_light), settings.warmth, 0f..1f, settings::updateWarmth, hint = str(R.string.warm_light_hint))
                Toggle(str(R.string.keep_screen_on), str(R.string.keep_screen_on_hint), settings.keepScreenOn, settings::updateKeepScreenOn)
                Choice(str(R.string.rotation), ScreenRotation.entries, settings.rotation, { it.label }, settings::updateRotation)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FwipColors.Amber,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun LevelSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, hint: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = FwipColors.TextSoft)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = FwipColors.Amber,
                activeTrackColor = FwipColors.Amber,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
        )
    }
}

/** Little page showing what each third of the screen does with the chosen tap zones. */
@Composable
private fun TapZonePreview(zones: TapZones, rtl: Boolean) {
    fun label(side: Int): String = when (zones.move(side, rtl)) {
        1 -> str(R.string.zone_next)
        -1 -> str(R.string.zone_back)
        else -> str(R.string.zone_menu)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(14.dp)),
    ) {
        listOf(-1 to 0.28f, 0 to 0.44f, 1 to 0.28f).forEach { (side, w) ->
            val move = zones.move(side, rtl)
            Box(
                Modifier
                    .weight(w)
                    .fillMaxSize()
                    .background(
                        when (move) {
                            1 -> FwipColors.Amber.copy(alpha = 0.22f)
                            -1 -> Color.White.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(side), style = MaterialTheme.typography.labelMedium, color = if (move == 1) FwipColors.Amber else FwipColors.TextSoft)
            }
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


/** Shown after the last page: what you just finished and what comes next. */
@Composable
internal fun EndCard(
    title: String,
    next: io.github.christiandonatti.fwip.data.LibraryItem?,
    onNext: () -> Unit,
    onLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF00B0C10))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(str(R.string.the_end), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (next != null) {
                Spacer(Modifier.height(10.dp))
                io.github.christiandonatti.fwip.ui.CoverImage(
                    next,
                    Modifier
                        .size(150.dp, 225.dp)
                        .clickable(onClickLabel = next.title, onClick = onNext),
                    corner = 16.dp,
                    titleSize = 18,
                )
                Text(str(R.string.up_next), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
                Text(
                    next.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                io.github.christiandonatti.fwip.ui.PillButton(
                    if (next.progress?.started == true && next.progress.finished.not()) str(R.string.continue_next) else str(R.string.read_next),
                    onNext,
                    style = io.github.christiandonatti.fwip.ui.PillStyle.Amber,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            io.github.christiandonatti.fwip.ui.PillButton(
                str(R.string.back_to_library),
                onLibrary,
                style = if (next == null) io.github.christiandonatti.fwip.ui.PillStyle.Solid else io.github.christiandonatti.fwip.ui.PillStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                str(R.string.end_tap_outside),
                style = MaterialTheme.typography.bodySmall,
                color = FwipColors.TextMuted,
            )
        }
    }
}
