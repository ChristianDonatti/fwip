package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow

enum class TurnStyle(@androidx.annotation.StringRes private val labelRes: Int) {
    Curl(R.string.turn_curl), Flip(R.string.turn_flip), Slide(R.string.turn_slide),
    /** Vertical scrolling for webtoons and long strips. */
    Scroll(R.string.turn_scroll);

    val label: String get() = str(labelRes)
}

enum class SpreadMode(@androidx.annotation.StringRes private val labelRes: Int) {
    Auto(R.string.spread_auto), Single(R.string.spread_single), Double(R.string.spread_double);

    val label: String get() = str(labelRes)
}

enum class ScreenRotation(@androidx.annotation.StringRes private val labelRes: Int) {
    Auto(R.string.rotation_auto), Portrait(R.string.rotation_portrait), Landscape(R.string.rotation_landscape);

    val label: String get() = str(labelRes)
}

/** What is behind the pages. */
enum class ReaderGround(@androidx.annotation.StringRes private val labelRes: Int, val argb: Long) {
    Black(R.string.ground_black, 0xFF121318), Gray(R.string.ground_gray, 0xFF3A3B42), Paper(R.string.ground_paper, 0xFFE7DECB);

    val label: String get() = str(labelRes)
}

enum class TurnSpeed(@androidx.annotation.StringRes private val labelRes: Int, val factor: Float) {
    Slow(R.string.speed_slow, 1.5f), Normal(R.string.speed_normal, 1f), Fast(R.string.speed_fast, 0.6f);

    val label: String get() = str(labelRes)
}

/** The sound of a page turning, each made for this app. */
enum class TurnSoundKind(@androidx.annotation.StringRes private val labelRes: Int, @androidx.annotation.RawRes val raw: Int) {
    Paper(R.string.sound_paper, R.raw.page_turn),
    Comic(R.string.sound_comic, R.raw.page_comic),
    Thin(R.string.sound_thin, R.raw.page_thin),
    Thick(R.string.sound_thick, R.raw.page_thick);

    val label: String get() = str(labelRes)
}

/** What a gesture on the page does. [Zoom] only makes sense for a double tap. */
enum class GestureAction(@androidx.annotation.StringRes private val labelRes: Int) {
    None(R.string.gesture_none), Zoom(R.string.gesture_zoom), Controls(R.string.gesture_controls),
    Pages(R.string.gesture_pages), Bookmark(R.string.gesture_bookmark), Guided(R.string.gesture_guided),
    Settings(R.string.gesture_settings), Close(R.string.gesture_close);

    val label: String get() = str(labelRes)
}

/** Reader looks read while drawing (outside composition), set from [ReaderSettings]. */
object ReaderLook {
    /** Multiplies the length of turn animations. */
    @Volatile var speed: Float = 1f
    /** How high the corner rises on a tapped turn, as a share of the page height. */
    @Volatile var lift: Float = 0.16f
    /** Strength of the shading along the fold, 0..1 (0.5 = the usual). */
    @Volatile var fold: Float = 0.5f
    /** Strength of the shadow where two pages meet, 0..1 (0.5 = the usual). */
    @Volatile var spine: Float = 0.5f
    /** Paper fibres on the back of a turning sheet. */
    @Volatile var texture: Boolean = false
}

/** What a tap on the left or right side of the page does (the middle always shows the controls). */
enum class TapZones(@androidx.annotation.StringRes private val labelRes: Int) {
    Sides(R.string.tap_sides), Reversed(R.string.tap_reversed), Forward(R.string.tap_forward), Off(R.string.tap_off);

    val label: String get() = str(labelRes)

    /** Pages to move for a tap on the left (-1) or right (+1) third, in reading direction. 0 = show controls. */
    fun move(side: Int, rtl: Boolean): Int {
        if (side == 0) return 0
        // Physical side to "back"/"forward": in manga the left side is forward.
        val forward = if (rtl) side < 0 else side > 0
        return when (this) {
            Sides -> if (forward) 1 else -1
            Reversed -> if (forward) -1 else 1
            Forward -> 1
            Off -> 0
        }
    }
}

/** Reader preferences, kept as Compose state so the screen updates the moment one changes. */
class ReaderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fwip_reader", Context.MODE_PRIVATE)

    var mangaMode by mutableStateOf(prefs.getBoolean("manga", false))
        private set
    // Stored under a new key so everyone starts with the paper curl once it exists.
    var turnStyle by mutableStateOf(runCatching { TurnStyle.valueOf(prefs.getString("turn2", null) ?: "") }.getOrDefault(TurnStyle.Curl))
        private set
    var spreadMode by mutableStateOf(runCatching { SpreadMode.valueOf(prefs.getString("spread", null) ?: "") }.getOrDefault(SpreadMode.Auto))
        private set
    var volumeKeys by mutableStateOf(prefs.getBoolean("volume", true))
        private set
    var cropMargins by mutableStateOf(prefs.getBoolean("crop", true))
        private set

    /** Screen brightness while reading, 0..1; below zero follows the system. */
    var brightness by mutableFloatStateOf(prefs.getFloat("brightness", -1f))
        private set
    /** Warm night filter strength, 0 (off) to 1. */
    var warmth by mutableFloatStateOf(prefs.getFloat("warmth", 0f))
        private set
    var keepScreenOn by mutableStateOf(prefs.getBoolean("awake", true))
        private set
    var rotation by mutableStateOf(runCatching { ScreenRotation.valueOf(prefs.getString("rotation", null) ?: "") }.getOrDefault(ScreenRotation.Auto))
        private set
    var tapZones by mutableStateOf(runCatching { TapZones.valueOf(prefs.getString("taps", null) ?: "") }.getOrDefault(TapZones.Sides))
        private set
    /** A light tick under the finger at each page turn. */
    var turnHaptic by mutableStateOf(prefs.getBoolean("haptic", true))
        private set
    var ground by mutableStateOf(runCatching { ReaderGround.valueOf(prefs.getString("ground", null) ?: "") }.getOrDefault(ReaderGround.Black))
        private set
    var spineShadow by mutableFloatStateOf(prefs.getFloat("spine", 0.5f))
        private set
    var paperTexture by mutableStateOf(prefs.getBoolean("texture", false))
        private set
    /** The rustle of paper at each page turn. */
    var turnSound by mutableStateOf(prefs.getBoolean("sound", false))
        private set
    /** Scan touch-ups: whiter paper and darker ink, less grain, crisper lines. */
    var fixColors by mutableStateOf(prefs.getBoolean("fixColors", false))
        private set
    var denoise by mutableStateOf(prefs.getBoolean("denoise", false))
        private set
    var sharpness by mutableStateOf(runCatching { Sharpness.valueOf(prefs.getString("sharp", null) ?: "") }.getOrDefault(Sharpness.Off))
        private set
    /** Wide (double) pages split in two when only one page fits on screen. */
    var splitWide by mutableStateOf(prefs.getBoolean("splitWide", false))
        private set

    var turnSpeed by mutableStateOf(runCatching { TurnSpeed.valueOf(prefs.getString("speed", null) ?: "") }.getOrDefault(TurnSpeed.Normal))
        private set
    /** 0 flat .. 1 high arc; 0.4 is the usual. */
    var turnLift by mutableFloatStateOf(prefs.getFloat("lift", 0.4f))
        private set
    var foldShade by mutableFloatStateOf(prefs.getFloat("fold", 0.5f))
        private set
    var soundKind by mutableStateOf(runCatching { TurnSoundKind.valueOf(prefs.getString("soundKind", null) ?: "") }.getOrDefault(TurnSoundKind.Paper))
        private set

    private fun gesture(key: String, default: GestureAction) =
        runCatching { GestureAction.valueOf(prefs.getString(key, null) ?: "") }.getOrDefault(default)
    var gestureDoubleTap by mutableStateOf(gesture("gDouble", GestureAction.Zoom))
        private set
    var gestureLongPress by mutableStateOf(gesture("gLong", GestureAction.None))
        private set
    var gestureSwipeUp by mutableStateOf(gesture("gUp", GestureAction.Pages))
        private set
    var gestureSwipeDown by mutableStateOf(gesture("gDown", GestureAction.Close))
        private set

    /** The page bar as a strip of little pages (Panels style), or a slim line. */
    var filmStrip by mutableStateOf(prefs.getBoolean("filmStrip", true))
        private set

    val pageFilters: PageFilters get() = PageFilters(fixColors, denoise, sharpness)

    fun updateManga(value: Boolean) { mangaMode = value; prefs.edit().putBoolean("manga", value).apply() }
    fun updateTurn(value: TurnStyle) { turnStyle = value; prefs.edit().putString("turn2", value.name).apply() }
    fun updateSpread(value: SpreadMode) { spreadMode = value; prefs.edit().putString("spread", value.name).apply() }
    fun updateVolume(value: Boolean) { volumeKeys = value; prefs.edit().putBoolean("volume", value).apply() }
    fun updateCrop(value: Boolean) { cropMargins = value; prefs.edit().putBoolean("crop", value).apply() }
    fun updateBrightness(value: Float) { brightness = value; prefs.edit().putFloat("brightness", value).apply() }
    fun updateWarmth(value: Float) { warmth = value; prefs.edit().putFloat("warmth", value).apply() }
    fun updateKeepScreenOn(value: Boolean) { keepScreenOn = value; prefs.edit().putBoolean("awake", value).apply() }
    fun updateRotation(value: ScreenRotation) { rotation = value; prefs.edit().putString("rotation", value.name).apply() }
    fun updateTapZones(value: TapZones) { tapZones = value; prefs.edit().putString("taps", value.name).apply() }
    fun updateGround(value: ReaderGround) { ground = value; prefs.edit().putString("ground", value.name).apply() }
    fun updateSpineShadow(value: Float) { spineShadow = value; prefs.edit().putFloat("spine", value).apply() }
    fun updatePaperTexture(value: Boolean) { paperTexture = value; prefs.edit().putBoolean("texture", value).apply() }
    fun updateTurnHaptic(value: Boolean) { turnHaptic = value; prefs.edit().putBoolean("haptic", value).apply() }
    fun updateTurnSound(value: Boolean) { turnSound = value; prefs.edit().putBoolean("sound", value).apply() }
    fun updateFixColors(value: Boolean) { fixColors = value; prefs.edit().putBoolean("fixColors", value).apply() }
    fun updateDenoise(value: Boolean) { denoise = value; prefs.edit().putBoolean("denoise", value).apply() }
    fun updateSharpness(value: Sharpness) { sharpness = value; prefs.edit().putString("sharp", value.name).apply() }
    fun updateTurnSpeed(value: TurnSpeed) { turnSpeed = value; prefs.edit().putString("speed", value.name).apply() }
    fun updateTurnLift(value: Float) { turnLift = value; prefs.edit().putFloat("lift", value).apply() }
    fun updateFoldShade(value: Float) { foldShade = value; prefs.edit().putFloat("fold", value).apply() }
    fun updateSoundKind(value: TurnSoundKind) { soundKind = value; prefs.edit().putString("soundKind", value.name).apply() }
    fun updateGestureDoubleTap(value: GestureAction) { gestureDoubleTap = value; prefs.edit().putString("gDouble", value.name).apply() }
    fun updateGestureLongPress(value: GestureAction) { gestureLongPress = value; prefs.edit().putString("gLong", value.name).apply() }
    fun updateGestureSwipeUp(value: GestureAction) { gestureSwipeUp = value; prefs.edit().putString("gUp", value.name).apply() }
    fun updateGestureSwipeDown(value: GestureAction) { gestureSwipeDown = value; prefs.edit().putString("gDown", value.name).apply() }
    fun updateFilmStrip(value: Boolean) { filmStrip = value; prefs.edit().putBoolean("filmStrip", value).apply() }
    fun updateSplitWide(value: Boolean) { splitWide = value; prefs.edit().putBoolean("splitWide", value).apply() }

    /** Everything worth carrying to another device in a backup. */
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("manga", mangaMode).put("turn", turnStyle.name).put("spread", spreadMode.name)
        .put("volume", volumeKeys).put("crop", cropMargins).put("brightness", brightness.toDouble())
        .put("warmth", warmth.toDouble()).put("awake", keepScreenOn).put("rotation", rotation.name).put("taps", tapZones.name)
        .put("haptic", turnHaptic).put("sound", turnSound)
        .put("ground", ground.name).put("spine", spineShadow.toDouble()).put("texture", paperTexture)
        .put("fixColors", fixColors).put("denoise", denoise).put("sharp", sharpness.name).put("splitWide", splitWide).put("filmStrip", filmStrip)
        .put("speed", turnSpeed.name).put("lift", turnLift.toDouble()).put("fold", foldShade.toDouble()).put("soundKind", soundKind.name)
        .put("gDouble", gestureDoubleTap.name).put("gLong", gestureLongPress.name).put("gUp", gestureSwipeUp.name).put("gDown", gestureSwipeDown.name)

    fun fromJson(o: org.json.JSONObject) {
        if (o.has("manga")) updateManga(o.getBoolean("manga"))
        runCatching { updateTurn(TurnStyle.valueOf(o.getString("turn"))) }
        runCatching { updateSpread(SpreadMode.valueOf(o.getString("spread"))) }
        if (o.has("volume")) updateVolume(o.getBoolean("volume"))
        if (o.has("crop")) updateCrop(o.getBoolean("crop"))
        if (o.has("brightness")) updateBrightness(o.getDouble("brightness").toFloat())
        if (o.has("warmth")) updateWarmth(o.getDouble("warmth").toFloat())
        if (o.has("awake")) updateKeepScreenOn(o.getBoolean("awake"))
        runCatching { updateRotation(ScreenRotation.valueOf(o.getString("rotation"))) }
        runCatching { updateTapZones(TapZones.valueOf(o.getString("taps"))) }
        if (o.has("haptic")) updateTurnHaptic(o.getBoolean("haptic"))
        if (o.has("sound")) updateTurnSound(o.getBoolean("sound"))
        runCatching { updateGround(ReaderGround.valueOf(o.getString("ground"))) }
        if (o.has("spine")) updateSpineShadow(o.getDouble("spine").toFloat())
        if (o.has("texture")) updatePaperTexture(o.getBoolean("texture"))
        if (o.has("fixColors")) updateFixColors(o.getBoolean("fixColors"))
        if (o.has("denoise")) updateDenoise(o.getBoolean("denoise"))
        runCatching { updateSharpness(Sharpness.valueOf(o.getString("sharp"))) }
        if (o.has("splitWide")) updateSplitWide(o.getBoolean("splitWide"))
        if (o.has("filmStrip")) updateFilmStrip(o.getBoolean("filmStrip"))
        runCatching { updateTurnSpeed(TurnSpeed.valueOf(o.getString("speed"))) }
        if (o.has("lift")) updateTurnLift(o.getDouble("lift").toFloat())
        if (o.has("fold")) updateFoldShade(o.getDouble("fold").toFloat())
        runCatching { updateSoundKind(TurnSoundKind.valueOf(o.getString("soundKind"))) }
        runCatching { updateGestureDoubleTap(GestureAction.valueOf(o.getString("gDouble"))) }
        runCatching { updateGestureLongPress(GestureAction.valueOf(o.getString("gLong"))) }
        runCatching { updateGestureSwipeUp(GestureAction.valueOf(o.getString("gUp"))) }
        runCatching { updateGestureSwipeDown(GestureAction.valueOf(o.getString("gDown"))) }
    }
}

/** Volume key presses forwarded from the activity while the reader is open. +1 = next, -1 = previous. */
object ReaderKeys {
    @Volatile var active: Boolean = false
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}
