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

/** Reader looks read while drawing (outside composition), set from [ReaderSettings]. */
object ReaderLook {
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

    /** Everything worth carrying to another device in a backup. */
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("manga", mangaMode).put("turn", turnStyle.name).put("spread", spreadMode.name)
        .put("volume", volumeKeys).put("crop", cropMargins).put("brightness", brightness.toDouble())
        .put("warmth", warmth.toDouble()).put("awake", keepScreenOn).put("rotation", rotation.name).put("taps", tapZones.name)
        .put("haptic", turnHaptic).put("sound", turnSound)
        .put("ground", ground.name).put("spine", spineShadow.toDouble()).put("texture", paperTexture)

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
    }
}

/** Volume key presses forwarded from the activity while the reader is open. +1 = next, -1 = previous. */
object ReaderKeys {
    @Volatile var active: Boolean = false
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}
