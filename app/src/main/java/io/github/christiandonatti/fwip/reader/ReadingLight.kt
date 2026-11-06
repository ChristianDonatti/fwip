package io.github.christiandonatti.fwip.reader

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.tan

/** How pages look: as they are, or as on an e-ink reader (grey, or the soft colours of colour e-ink). */
enum class InkLook(@androidx.annotation.StringRes private val labelRes: Int) {
    Off(R.string.ink_off), Mono(R.string.ink_mono), Color(R.string.ink_color);

    val label: String get() = str(labelRes)
}

/** When the warm light comes on by itself. */
enum class WarmSchedule(@androidx.annotation.StringRes private val labelRes: Int) {
    Off(R.string.warm_schedule_off), Sun(R.string.warm_schedule_sun), Hours(R.string.warm_schedule_hours);

    val label: String get() = str(labelRes)
}

/**
 * Light and comfort while reading, shared by comics and books: a front light (brightness that
 * goes below the screen's minimum and a warm tint, both maybe on a schedule or following the
 * room), the e-ink look, and the small things around reading (clock, Do Not Disturb, rotation).
 */
class LightSettings private constructor(context: Context) {
    private val app = context.applicationContext
    // Same file as the reader settings, where brightness and warmth lived before.
    private val prefs = app.getSharedPreferences("fwip_reader", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    /**
     * The light, 0..1, or below zero to follow the system. The first [DIM_ZONE] of the range is
     * darker than the screen can go on its own (the screen at its lowest, plus a veil).
     */
    var level by mutableFloatStateOf(loadLevel())
        private set
    /** Warm tint, 0 (off) to 1. */
    var warmth by mutableFloatStateOf(prefs.getFloat("warmth", 0f))
        private set
    var schedule by mutableStateOf(enumPref("warmSchedule", WarmSchedule.Off))
        private set
    /** How warm it gets at night when scheduled. */
    var nightWarmth by mutableFloatStateOf(prefs.getFloat("nightWarmth", 0.6f))
        private set
    /** Scheduled hours, in minutes after midnight. */
    var warmFrom by mutableIntStateOf(prefs.getInt("warmFrom", 21 * 60))
        private set
    var warmTo by mutableIntStateOf(prefs.getInt("warmTo", 7 * 60))
        private set
    /** Light (and a little warmth) follow the room, from the light sensor. */
    var ambient by mutableStateOf(prefs.getBoolean("ambient", false))
        private set
    var ink by mutableStateOf(enumPref("ink", InkLook.Off))
        private set
    /** The black-white blink of an e-ink screen at each page turn. */
    var inkFlash by mutableStateOf(prefs.getBoolean("inkFlash", false))
        private set
    var keepScreenOn by mutableStateOf(prefs.getBoolean("awake", true))
        private set
    var rotation by mutableStateOf(enumPref("rotation", ScreenRotation.Auto))
        private set
    /** Do Not Disturb while a comic or book is open. */
    var focus by mutableStateOf(prefs.getBoolean("focus", false))
        private set
    var showClock by mutableStateOf(prefs.getBoolean("clock", false))
        private set
    var showBattery by mutableStateOf(prefs.getBoolean("battery", false))
        private set

    /** Where sunset and sunrise are worked out for; NaN until known. */
    val latitude: Float get() = prefs.getFloat("sunLat", Float.NaN)
    val longitude: Float get() = prefs.getFloat("sunLon", Float.NaN)

    private fun loadLevel(): Float {
        if (prefs.contains("light")) return prefs.getFloat("light", -1f)
        // Before: plain screen brightness 0.02..1, or -1 for the system's.
        val old = prefs.getFloat("brightness", -1f)
        return if (old < 0f) -1f else DIM_ZONE + old * (1f - DIM_ZONE)
    }

    fun updateLevel(value: Float) { level = value; prefs.edit().putFloat("light", value).apply() }
    fun updateWarmth(value: Float) { warmth = value; prefs.edit().putFloat("warmth", value).apply() }
    fun updateSchedule(value: WarmSchedule) { schedule = value; prefs.edit().putString("warmSchedule", value.name).apply() }
    fun updateNightWarmth(value: Float) { nightWarmth = value; prefs.edit().putFloat("nightWarmth", value).apply() }
    fun updateWarmFrom(value: Int) { warmFrom = value.mod(1440); prefs.edit().putInt("warmFrom", warmFrom).apply() }
    fun updateWarmTo(value: Int) { warmTo = value.mod(1440); prefs.edit().putInt("warmTo", warmTo).apply() }
    fun updateAmbient(value: Boolean) { ambient = value; prefs.edit().putBoolean("ambient", value).apply() }
    fun updateInk(value: InkLook) { ink = value; prefs.edit().putString("ink", value.name).apply() }
    fun updateInkFlash(value: Boolean) { inkFlash = value; prefs.edit().putBoolean("inkFlash", value).apply() }
    fun updateKeepScreenOn(value: Boolean) { keepScreenOn = value; prefs.edit().putBoolean("awake", value).apply() }
    fun updateRotation(value: ScreenRotation) { rotation = value; prefs.edit().putString("rotation", value.name).apply() }
    fun updateFocus(value: Boolean) { focus = value; prefs.edit().putBoolean("focus", value).apply() }
    fun updateShowClock(value: Boolean) { showClock = value; prefs.edit().putBoolean("clock", value).apply() }
    fun updateShowBattery(value: Boolean) { showBattery = value; prefs.edit().putBoolean("battery", value).apply() }

    /** Remembers where the device is (roughly), for sunset and sunrise. Needs the location permission. */
    fun learnLocation(): Boolean {
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        val lm = app.getSystemService(LocationManager::class.java) ?: return false
        val loc = runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull() ?: return false
        prefs.edit().putFloat("sunLat", loc.latitude.toFloat()).putFloat("sunLon", loc.longitude.toFloat()).apply()
        return true
    }

    /** Evening start and morning end of the warm hours, in minutes after midnight. */
    fun window(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? = when (schedule) {
        WarmSchedule.Off -> null
        WarmSchedule.Hours -> warmFrom to warmTo
        WarmSchedule.Sun -> {
            val lat = latitude.toDouble()
            val lon = longitude.toDouble()
            // Without a location, the time zone gives the longitude and the equator a fair guess.
            val guessLon = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 240.0
            val t = SunTimes.compute(if (lat.isNaN()) 0.0 else lat, if (lon.isNaN()) guessLon else lon, date, zone)
            t?.let { (rise, set) -> set to rise } ?: (19 * 60 to 6 * 60 + 30)
        }
    }

    /** The warmth for [minute] of the day: the chosen one, raised to the night one during warm hours. */
    fun warmthAt(minute: Int = LocalTime.now().let { it.hour * 60 + it.minute }): Float {
        val w = window() ?: return warmth
        val f = nightFactor(minute, w.first, w.second)
        val night = maxOf(warmth, nightWarmth)
        return warmth + (night - warmth) * f
    }

    fun reset() {
        updateLevel(-1f); updateWarmth(0f); updateSchedule(WarmSchedule.Off); updateNightWarmth(0.6f)
        updateWarmFrom(21 * 60); updateWarmTo(7 * 60); updateAmbient(false); updateInk(InkLook.Off); updateInkFlash(false)
        updateKeepScreenOn(true); updateRotation(ScreenRotation.Auto); updateFocus(false); updateShowClock(false); updateShowBattery(false)
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("light", level.toDouble()).put("warmth", warmth.toDouble()).put("warmSchedule", schedule.name)
        .put("nightWarmth", nightWarmth.toDouble()).put("warmFrom", warmFrom).put("warmTo", warmTo)
        .put("ambient", ambient).put("ink", ink.name).put("inkFlash", inkFlash).put("awake", keepScreenOn)
        .put("rotation", rotation.name).put("focus", focus).put("clock", showClock).put("battery", showBattery)

    fun fromJson(o: org.json.JSONObject) {
        if (o.has("light")) updateLevel(o.getDouble("light").toFloat())
        else if (o.has("brightness")) o.getDouble("brightness").toFloat().let { b -> updateLevel(if (b < 0f) -1f else DIM_ZONE + b * (1f - DIM_ZONE)) }
        if (o.has("warmth")) updateWarmth(o.getDouble("warmth").toFloat())
        runCatching { updateSchedule(WarmSchedule.valueOf(o.getString("warmSchedule"))) }
        if (o.has("nightWarmth")) updateNightWarmth(o.getDouble("nightWarmth").toFloat())
        if (o.has("warmFrom")) updateWarmFrom(o.getInt("warmFrom"))
        if (o.has("warmTo")) updateWarmTo(o.getInt("warmTo"))
        if (o.has("ambient")) updateAmbient(o.getBoolean("ambient"))
        runCatching { updateInk(InkLook.valueOf(o.getString("ink"))) }
        if (o.has("inkFlash")) updateInkFlash(o.getBoolean("inkFlash"))
        if (o.has("awake")) updateKeepScreenOn(o.getBoolean("awake"))
        runCatching { updateRotation(ScreenRotation.valueOf(o.getString("rotation"))) }
        if (o.has("focus")) updateFocus(o.getBoolean("focus"))
        if (o.has("clock")) updateShowClock(o.getBoolean("clock"))
        if (o.has("battery")) updateShowBattery(o.getBoolean("battery"))
    }

    companion object {
        /** The bottom of the light range that goes darker than the screen alone. */
        const val DIM_ZONE = 0.15f
        /** Minutes the warm light takes to come on or go off. */
        const val RAMP = 45

        /** 0 by day, 1 in the warm hours, sliding over [RAMP] minutes at each end. */
        fun nightFactor(minute: Int, start: Int, end: Int): Float {
            val length = (end - start).mod(1440)
            if (length == 0) return 0f
            val since = (minute - start).mod(1440)
            if (since >= length) return 0f
            val ramp = minOf(RAMP, length / 2).coerceAtLeast(1)
            return minOf(1f, since / ramp.toFloat(), (length - since) / ramp.toFloat()).coerceIn(0f, 1f)
        }

        /** Screen brightness (0.01..1, or -1 for the system's) and the extra veil (0..1) for a light level. */
        fun screenFor(level: Float): Pair<Float, Float> = when {
            level < 0f -> -1f to 0f
            level < DIM_ZONE -> 0.01f to (DIM_ZONE - level) / DIM_ZONE * 0.6f
            else -> (0.01f + (level - DIM_ZONE) / (1f - DIM_ZONE) * 0.99f) to 0f
        }

        /** A light level for a room this bright (lux): dark rooms get the dimmest light. */
        fun levelForLux(lux: Float): Float = (0.05f + 0.95f * (log10(lux + 1f) / 3.3f)).coerceIn(0.03f, 1f)

        @Volatile private var instance: LightSettings? = null
        fun get(context: Context): LightSettings =
            instance ?: synchronized(this) { instance ?: LightSettings(context.applicationContext).also { instance = it } }
    }
}

/** Sunrise and sunset from the NOAA approximation (a minute or two off at most). */
object SunTimes {
    /** Minutes after local midnight of sunrise and sunset, or null when the sun stays up or down all day. */
    fun compute(lat: Double, lon: Double, date: LocalDate, zone: ZoneId): Pair<Int, Int>? {
        val gamma = 2 * PI / 365 * (date.dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val latR = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (cosHa < -1.0 || cosHa > 1.0) return null
        val ha = Math.toDegrees(acos(cosHa))
        val offset = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 60.0
        val rise = 720 - 4 * (lon + ha) - eqTime + offset
        val set = 720 - 4 * (lon - ha) - eqTime + offset
        return rise.toInt().mod(1440) to set.toInt().mod(1440)
    }
}

/** Fine paper grain, made once and tiled (a matte e-ink screen, or paper). */
object PaperGrain {
    val bitmap: ImageBitmap by lazy {
        val size = 160
        val px = IntArray(size * size)
        var seed = 0x2545F491
        for (i in px.indices) {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            val v = 128 + (seed and 0x3f) - 32
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    val brush: ShaderBrush by lazy { ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)) }
}

/** The light level coming from the room, smoothed; null without a light sensor or when off. */
@Composable
fun rememberRoomLux(enabled: Boolean): Float? {
    val context = LocalContext.current
    val lux by produceState<Float?>(null, enabled) {
        if (!enabled) { value = null; return@produceState }
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sm == null || sensor == null) { value = null; return@produceState }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = event.values.firstOrNull() ?: return
                val old = value
                // Slow to follow, so a passing shadow does not make the page pump.
                value = if (old == null) now else old + (now - old) * 0.12f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitDispose { sm.unregisterListener(listener) }
    }
    return lux
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The minute of the day, ticking once a minute. */
@Composable
private fun rememberMinute(): Int {
    val minute by produceState(LocalTime.now().let { it.hour * 60 + it.minute }) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
    return minute
}

/**
 * Everything the light settings do to the screen while reading: brightness (and the veil below
 * the minimum), warm tint, keeping the screen on, the rotation lock and Do Not Disturb.
 * Draws over whatever comes before it in the same Box.
 */
@Composable
fun ReadingLightLayer(light: LightSettings) {
    val view = LocalView.current
    val activity = view.context.activity()
    val lux = rememberRoomLux(light.ambient && light.level >= 0f)
    val minute = rememberMinute()
    val level = if (lux != null) LightSettings.levelForLux(lux) else light.level
    val (screen, veil) = LightSettings.screenFor(level)
    // In a dark room the light also warms up a little, as paper under a lamp would.
    val roomWarm = if (lux != null) (1f - LightSettings.levelForLux(lux)) * 0.25f else 0f
    val warmth = (light.warmthAt(minute) + roomWarm).coerceIn(0f, 1f)

    if (activity != null) {
        DisposableEffect(screen) {
            val attrs = activity.window.attributes
            attrs.screenBrightness = if (screen < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else screen
            activity.window.attributes = attrs
            onDispose {
                val a = activity.window.attributes
                a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = a
            }
        }
        DisposableEffect(light.rotation) {
            activity.requestedOrientation = when (light.rotation) {
                ScreenRotation.Auto -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ScreenRotation.Portrait -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ScreenRotation.Landscape -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            onDispose { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    DisposableEffect(view, light.keepScreenOn) {
        view.keepScreenOn = light.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    FocusMode(light.focus)

    if (warmth > 0f || veil > 0f || light.ink != InkLook.Off) {
        Canvas(Modifier.fillMaxSize()) {
            // A matte screen: fine grain over everything, like e-ink film.
            if (light.ink != InkLook.Off) drawRect(PaperGrain.brush, alpha = 0.07f, blendMode = BlendMode.Overlay)
            if (warmth > 0f) drawRect(Color(0xFFFFA24A).copy(alpha = 0.45f * warmth), blendMode = BlendMode.Multiply)
            if (veil > 0f) drawRect(Color.Black.copy(alpha = veil))
        }
    }
}

/** Do Not Disturb (priority only) while reading, if allowed; puts back what was there. */
@Composable
fun FocusMode(on: Boolean) {
    val context = LocalContext.current
    DisposableEffect(on) {
        val nm = context.getSystemService(NotificationManager::class.java)
        var changed = false
        if (on && nm != null && nm.isNotificationPolicyAccessGranted && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); changed = true }
        }
        onDispose {
            if (changed && nm != null && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            }
        }
    }
}

/** Whether Fwip may turn Do Not Disturb on. */
fun focusAllowed(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

/** Opens the system page where Fwip can be allowed to use Do Not Disturb. */
fun askFocusAccess(context: Context) {
    runCatching {
        context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * The blink of an e-ink screen when the page changes: black for a moment, then white, then the
 * page. [trigger] is anything that changes with the page.
 */
@Composable
fun InkFlash(trigger: Any?, enabled: Boolean) {
    val black = remember { Animatable(0f) }
    val white = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (!enabled) return@LaunchedEffect
        black.snapTo(0.85f)
        delay(70)
        black.snapTo(0f)
        white.snapTo(0.75f)
        white.animateTo(0f, tween(160))
    }
    if (black.value > 0f || white.value > 0f) {
        Canvas(Modifier.fillMaxSize()) {
            if (black.value > 0f) drawRect(Color.Black.copy(alpha = black.value))
            if (white.value > 0f) drawRect(Color(0xFFF2F0EA).copy(alpha = white.value))
        }
    }
}

/** A small clock and battery level at the top of the page, like an e-reader's. */
@Composable
fun ReadingStatus(clock: Boolean, battery: Boolean, modifier: Modifier = Modifier) {
    if (!clock && !battery) return
    val context = LocalContext.current
    val minute = rememberMinute()
    val percent by produceState<Int?>(null, battery, minute) {
        value = if (!battery) null else runCatching {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (l >= 0 && s > 0) l * 100 / s else null
        }.getOrNull()
    }
    val parts = buildList {
        if (clock) add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date()))
        percent?.let { add("$it%") }
    }
    if (parts.isEmpty()) return
    Box(modifier.fillMaxSize().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)),
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}
