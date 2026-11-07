@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.christiandonatti.fwip.reader

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.CircleIconButton
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.PillStyle
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.delay

/** The setting a search result pointed at: that row scrolls into view and glows for a moment. */
val LocalSettingHighlight = staticCompositionLocalOf<String?> { null }

/** Marks a setting row so a search can find it on screen. */
@Composable
fun Modifier.settingAnchor(key: String?): Modifier {
    if (key == null || LocalSettingHighlight.current != key) return this
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    val accent = FwipColors.Amber
    LaunchedEffect(key) {
        delay(280)
        requester.bringIntoView()
        glow.animateTo(1f, tween(260))
        delay(1100)
        glow.animateTo(0f, tween(900))
    }
    return this
        .bringIntoViewRequester(requester)
        .drawBehind {
            if (glow.value > 0f) {
                val pad = 8.dp.toPx()
                drawRoundRect(
                    accent.copy(alpha = 0.18f * glow.value),
                    topLeft = androidx.compose.ui.geometry.Offset(-pad, -pad),
                    size = androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
        }
}

private fun timeLabel(context: android.content.Context, minute: Int): String {
    val c = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minute / 60)
        set(java.util.Calendar.MINUTE, minute % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(c.time)
}

/** Everything about how comics read: pages, image, touch and sound, gestures, the turn. */
@Composable
fun ComicReadingSettings(settings: ReaderSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Section(str(R.string.section_pages))
        Choice(str(R.string.page_turn), TurnStyle.entries, settings.turnStyle, { it.label }, settings::updateTurn, key = "turn")
        Choice(str(R.string.pages_on_screen), SpreadMode.entries, settings.spreadMode, { it.label }, settings::updateSpread, key = "spread")
        Toggle(str(R.string.manga_mode), str(R.string.manga_mode_hint), settings.mangaMode, settings::updateManga, key = "manga")
        Toggle(str(R.string.crop_margins), str(R.string.crop_margins_hint), settings.cropMargins, settings::updateCrop, key = "crop")
        Toggle(str(R.string.split_wide), str(R.string.split_wide_hint), settings.splitWide, settings::updateSplitWide, key = "split")
        Toggle(str(R.string.film_strip), str(R.string.film_strip_hint), settings.filmStrip, settings::updateFilmStrip, key = "strip")

        Section(str(R.string.section_image))
        Toggle(str(R.string.fix_colors), str(R.string.fix_colors_hint), settings.fixColors, settings::updateFixColors, key = "fixColors")
        Toggle(str(R.string.denoise), str(R.string.denoise_hint), settings.denoise, settings::updateDenoise, key = "denoise")
        Choice(str(R.string.sharpness), Sharpness.entries, settings.sharpness, { it.label }, settings::updateSharpness, key = "sharp")

        Section(str(R.string.section_touch))
        Choice(str(R.string.tap_zones), TapZones.entries, settings.tapZones, { it.label }, settings::updateTapZones, key = "taps")
        TapZonePreview(settings.tapZones, settings.mangaMode)
        Toggle(str(R.string.volume_keys), str(R.string.volume_keys_hint), settings.volumeKeys, settings::updateVolume, key = "volume")
        Toggle(str(R.string.turn_haptic), str(R.string.turn_haptic_hint), settings.turnHaptic, settings::updateTurnHaptic, key = "haptic")
        val soundCheck = rememberTurnFeedback()
        Toggle(str(R.string.turn_sound), str(R.string.turn_sound_hint), settings.turnSound, { on ->
            settings.updateTurnSound(on)
            if (on) soundCheck.preview(settings.soundKind)
        }, key = "sound")
        if (settings.turnSound) {
            Choice(str(R.string.sound_kind), TurnSoundKind.entries, settings.soundKind, { it.label }, { k ->
                settings.updateSoundKind(k)
                soundCheck.preview(k)
            })
        }
        Text(str(R.string.keys_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, modifier = Modifier.settingAnchor("keys"))

        Section(str(R.string.section_gestures))
        GestureRow(str(R.string.gesture_double_tap), settings.gestureDoubleTap, GestureAction.entries, settings::updateGestureDoubleTap, key = "gestures")
        val noZoom = GestureAction.entries - GestureAction.Zoom
        GestureRow(str(R.string.gesture_long_press), settings.gestureLongPress, noZoom, settings::updateGestureLongPress)
        GestureRow(str(R.string.gesture_swipe_up), settings.gestureSwipeUp, noZoom, settings::updateGestureSwipeUp)
        GestureRow(str(R.string.gesture_swipe_down), settings.gestureSwipeDown, noZoom, settings::updateGestureSwipeDown)

        Section(str(R.string.section_look))
        ReaderLookSettings(settings)

        Section(str(R.string.comic3d_section))
        ComicSceneSettings(settings)
    }
}

/** The 3D book for comics, in the same places (and with the same sound) as books. */
@Composable
fun ComicSceneSettings(settings: ReaderSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    io.github.christiandonatti.fwip.book.BookSceneSettings(
        io.github.christiandonatti.fwip.book.BookSettings.get(context),
        threeD = settings.comic3d,
        onThreeD = settings::updateComic3d,
        toggleTitle = str(R.string.comic3d),
        toggleHint = str(R.string.comic3d_hint),
        toggleKey = "comic3d",
    )
}

/** The light slider: the system's light at the far left switch, then down to below the minimum. */
@Composable
internal fun LightLevelRow(light: LightSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Toggle(str(R.string.system_brightness), str(R.string.system_brightness_hint), light.level < 0f, { system ->
            light.updateLevel(if (system) -1f else 0.6f)
        }, key = "light")
        if (light.level >= 0f) {
            LevelSlider(
                str(R.string.brightness),
                light.level,
                0f..1f,
                light::updateLevel,
                hint = if (light.ambient) str(R.string.ambient_on_hint) else str(R.string.brightness_dim_hint),
                valueLabel = if (light.level < LightSettings.DIM_ZONE) str(R.string.below_minimum) else "${((light.level - LightSettings.DIM_ZONE) / (1f - LightSettings.DIM_ZONE) * 100).toInt()}%",
            )
        }
    }
}

/** Light and comfort, for comics and books alike. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightComfortSettings(light: LightSettings) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf<Boolean?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) light.learnLocation()
    }
    var focusOk by remember { mutableStateOf(focusAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        focusOk = focusAllowed(context)
        // Coming back from the location prompt or system settings: try again for sunset times.
        if (light.schedule == WarmSchedule.Sun && light.latitude.isNaN()) light.learnLocation()
        onPauseOrDispose { }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Section(str(R.string.section_light))
        LightLevelRow(light)
        Toggle(str(R.string.ambient_light), str(R.string.ambient_light_hint), light.ambient, light::updateAmbient, key = "ambient")
        LevelSlider(str(R.string.warm_light), light.warmth, 0f..1f, light::updateWarmth, hint = str(R.string.warm_light_hint), key = "warmth")
        Choice(str(R.string.warm_schedule), WarmSchedule.entries, light.schedule, { it.label }, { s ->
            light.updateSchedule(s)
            if (s == WarmSchedule.Sun && !light.learnLocation()) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, key = "schedule", hint = str(R.string.warm_schedule_hint))
        if (light.schedule != WarmSchedule.Off) {
            LevelSlider(str(R.string.night_warmth), light.nightWarmth, 0.1f..1f, light::updateNightWarmth)
            val w = light.window()
            if (light.schedule == WarmSchedule.Hours) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeChip(str(R.string.warm_from), timeLabel(context, light.warmFrom), Modifier.weight(1f)) { picking = true }
                    TimeChip(str(R.string.warm_to), timeLabel(context, light.warmTo), Modifier.weight(1f)) { picking = false }
                }
            } else if (w != null) {
                Text(
                    str(R.string.warm_sun_times, timeLabel(context, w.first), timeLabel(context, w.second)) +
                        if (light.latitude.isNaN()) "\n" + str(R.string.warm_sun_guess) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = FwipColors.TextMuted,
                )
            }
        }

        Section(str(R.string.section_ink))
        Choice(str(R.string.ink_look), InkLook.entries, light.ink, { it.label }, light::updateInk, key = "ink", hint = str(R.string.ink_look_hint))
        Toggle(str(R.string.ink_flash), str(R.string.ink_flash_hint), light.inkFlash, light::updateInkFlash, key = "flash")

        Section(str(R.string.section_while_reading))
        Toggle(str(R.string.show_clock), str(R.string.show_clock_hint), light.showClock, light::updateShowClock, key = "clock")
        Toggle(str(R.string.show_battery), str(R.string.show_battery_hint), light.showBattery, light::updateShowBattery, key = "battery")
        Toggle(str(R.string.focus_mode), if (light.focus && !focusOk) str(R.string.focus_mode_allow) else str(R.string.focus_mode_hint), light.focus, { on ->
            light.updateFocus(on)
            if (on && !focusAllowed(context)) askFocusAccess(context)
        }, key = "focus")
        Toggle(str(R.string.keep_screen_on), str(R.string.keep_screen_on_hint), light.keepScreenOn, light::updateKeepScreenOn, key = "awake")
        Choice(str(R.string.rotation), ScreenRotation.entries, light.rotation, { it.label }, light::updateRotation, key = "rotation")
    }

    picking?.let { from ->
        val start = if (from) light.warmFrom else light.warmTo
        val state = rememberTimePickerState(start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(context))
        Dialog(onDismissRequest = { picking = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(FwipColors.Panel)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (from) str(R.string.warm_from) else str(R.string.warm_to), style = MaterialTheme.typography.titleLarge)
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = FwipColors.Surface,
                        selectorColor = FwipColors.Amber,
                        timeSelectorSelectedContainerColor = FwipColors.Amber.copy(alpha = 0.25f),
                        timeSelectorUnselectedContainerColor = FwipColors.Surface,
                        timeSelectorSelectedContentColor = FwipColors.Text,
                        timeSelectorUnselectedContentColor = FwipColors.TextSoft,
                        clockDialSelectedContentColor = FwipColors.AmberInk,
                        clockDialUnselectedContentColor = FwipColors.Text,
                        periodSelectorSelectedContainerColor = FwipColors.Amber.copy(alpha = 0.25f),
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(str(R.string.cancel), { picking = null }, style = PillStyle.Ghost, height = 44.dp)
                    PillButton(str(R.string.ok), {
                        val m = state.hour * 60 + state.minute
                        if (from) light.updateWarmFrom(m) else light.updateWarmTo(m)
                        picking = null
                    }, style = PillStyle.Amber, height = 44.dp)
                }
            }
        }
    }
}

@Composable
private fun TimeChip(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = FwipColors.TextMuted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The reader's own short panel: what people change while reading (light, the e-ink look, how
 * pages turn and fit). Everything else is one tap away under "More settings".
 */
@Composable
internal fun QuickSettings(settings: ReaderSettings, light: LightSettings, onDismiss: () -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    var places by rememberSaveable { mutableStateOf(false) }
    if (more) {
        ReaderMoreSettings(settings, light, onDismiss = { more = false })
        return
    }
    if (places) {
        Dialog(onDismissRequest = { places = false }) {
            Column(
                Modifier
                    .widthIn(max = 620.dp)
                    .heightIn(max = 760.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(FwipColors.Panel)
                    .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp)),
            ) {
                Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(str(R.string.comic3d_section), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    CircleIconButton(Icons.Filled.Close, str(R.string.close), { places = false }, size = 40.dp)
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                    ComicSceneSettings(settings)
                }
            }
        }
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(FwipColors.Panel)
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
                LightLevelRow(light)
                LevelSlider(str(R.string.warm_light), light.warmth, 0f..1f, light::updateWarmth)
                Choice(str(R.string.ink_look), InkLook.entries, light.ink, { it.label }, light::updateInk)
                Choice(str(R.string.page_turn), TurnStyle.entries, settings.turnStyle, { it.label }, settings::updateTurn)
                Choice(str(R.string.pages_on_screen), SpreadMode.entries, settings.spreadMode, { it.label }, settings::updateSpread)
                Toggle(str(R.string.manga_mode), str(R.string.manga_mode_hint), settings.mangaMode, settings::updateManga)
                Toggle(str(R.string.comic3d), str(R.string.comic3d_hint), settings.comic3d, settings::updateComic3d)
                PillButton(str(R.string.comic3d_places), { places = true }, Modifier.fillMaxWidth(), style = PillStyle.Ghost, height = 48.dp)
                PillButton(str(R.string.more_settings), { more = true }, Modifier.fillMaxWidth(), style = PillStyle.Ghost, height = 48.dp)
            }
        }
    }
}

/** All the reading settings, full screen, from inside the reader. */
@Composable
internal fun ReaderMoreSettings(settings: ReaderSettings, light: LightSettings, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(FwipColors.Panel), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(str(R.string.reading_settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 44.dp)
                }
                Row(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(CircleShape)
                        .background(FwipColors.Surface)
                        .border(1.dp, FwipColors.Stroke, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(str(R.string.settings_comics), str(R.string.settings_light)).forEachIndexed { i, t ->
                        val on = i == tab
                        Box(
                            Modifier
                                .height(40.dp)
                                .clip(CircleShape)
                                .background(if (on) FwipColors.Text else Color.Transparent)
                                .clickable(role = Role.Tab) { tab = i }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft)
                        }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    if (tab == 0) ComicReadingSettings(settings) else LightComfortSettings(light)
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}
