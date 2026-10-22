package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.theme.Palette
import io.github.christiandonatti.fwip.ui.theme.ThemePreset
import io.github.christiandonatti.fwip.ui.theme.TitleFont
import org.json.JSONArray
import org.json.JSONObject

/** Where the highlight colour comes from. */
enum class AccentMode { Theme, Cover, Wallpaper }

/** What fills the library behind everything. */
enum class Backdrop(@StringRes private val labelRes: Int, @StringRes private val descRes: Int) {
    Glow(R.string.backdrop_glow, R.string.backdrop_glow_desc),
    Solid(R.string.backdrop_solid, R.string.backdrop_solid_desc),
    Living(R.string.backdrop_living, R.string.backdrop_living_desc),
    Image(R.string.backdrop_image, R.string.backdrop_image_desc);

    val label: String get() = str(labelRes)
    val description: String get() = str(descRes)
}

/** Cover corners: a multiplier on the usual rounding. */
enum class CoverCorners(@StringRes private val labelRes: Int, val factor: Float) {
    Square(R.string.corners_square, 0.15f), Rounded(R.string.corners_rounded, 1f), Round(R.string.corners_round, 1.8f);

    val label: String get() = str(labelRes)
}

/** How finished comics are marked on their cover. */
enum class ReadMark(@StringRes private val labelRes: Int) {
    Badge(R.string.read_mark_badge), Ribbon(R.string.read_mark_ribbon), Faded(R.string.read_mark_faded);

    val label: String get() = str(labelRes)
}

enum class CarouselStyle(@StringRes private val labelRes: Int, @StringRes private val descRes: Int) {
    Flat(R.string.carousel_flat, R.string.carousel_flat_desc),
    Tilt(R.string.carousel_tilt, R.string.carousel_tilt_desc),
    Fan(R.string.carousel_fan, R.string.carousel_fan_desc);

    val label: String get() = str(labelRes)
    val description: String get() = str(descRes)
}

/** The parts the home screen can show, in the order the user picks. */
enum class HomePart { Greeting, Carousel, Continue, Shelf, Week, Want, Recent, Collection, Series }

/** One part of the home screen; [ref] is the collection id or series key for pinned rows. */
data class HomeSection(val part: HomePart, val on: Boolean = true, val ref: String = "", val title: String = "") {
    val key: String get() = if (ref.isEmpty()) part.name else part.name + ":" + ref
}

/** The look of the library, kept as Compose state so screens follow every change at once. */
class UiSettings private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("fwip_ui", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    var preset by mutableStateOf(loadPreset())
        private set
    var accentMode by mutableStateOf(enumPref("accentMode", AccentMode.Theme))
        private set
    /** Index into the theme's own colours, remembered per theme. */
    var accentIndex by mutableStateOf(prefs.getInt("accent_" + preset.name, legacyAccent()))
        private set
    var titleFont by mutableStateOf(enumPref("titleFont", preset.font))
        private set
    var backdrop by mutableStateOf(enumPref("backdrop", Backdrop.Glow))
        private set
    var corners by mutableStateOf(enumPref("corners", CoverCorners.Rounded))
        private set
    var coverShadow by mutableStateOf(prefs.getBoolean("coverShadow", true))
        private set
    var coverTitles by mutableStateOf(prefs.getBoolean("coverTitles", true))
        private set
    var readMark by mutableStateOf(enumPref("readMark", ReadMark.Badge))
        private set
    var showCarousel by mutableStateOf(prefs.getBoolean("showCarousel", true))
        private set
    var carousel by mutableStateOf(enumPref("carousel", CarouselStyle.Flat))
        private set
    var sections by mutableStateOf(loadSections())
        private set
    var name by mutableStateOf(prefs.getString("name", "") ?: "")
        private set
    var appIcon by mutableStateOf(enumPref("appIcon", AppIcon.Amber))
        private set
    var coverAnimation by mutableStateOf(prefs.getBoolean("coverAnimation", true))
        private set
    /** Cover size in grids, changed by pinching: 1 = default. */
    var coverScale by mutableFloatStateOf(prefs.getFloat("coverScale", 1f))
        private set
    var welcomed by mutableStateOf(prefs.getBoolean("welcomed", false))
        private set
    /** Bumped when the background picture changes, so it is read again. */
    var backdropVersion by mutableStateOf(prefs.getLong("backdropVersion", 0L))
        private set

    /** The selected comic's colour, for "follow the cover". Not saved. */
    var coverTint by mutableStateOf<Color?>(null)

    private fun loadPreset(): ThemePreset {
        prefs.getString("preset", null)?.let { p -> runCatching { return ThemePreset.valueOf(p) } }
        // Before presets: dark, light or system.
        return when (prefs.getString("theme", null)) {
            "Light" -> ThemePreset.Paper
            "System" -> ThemePreset.System
            else -> ThemePreset.Night
        }
    }

    private fun legacyAccent(): Int = when (prefs.getString("accent", null)) {
        "Blue" -> 1; "Red" -> 2; "Green" -> 3; "Violet" -> 4; "Pink" -> 5; else -> 0
    }

    fun updatePreset(value: ThemePreset) {
        preset = value
        accentIndex = prefs.getInt("accent_" + value.name, 0)
        // A theme brings its own title font; it can still be changed afterwards.
        titleFont = value.font
        prefs.edit().putString("preset", value.name).putString("titleFont", value.font.name).apply()
        refreshAccentIcon()
    }

    fun updateAccent(index: Int) {
        accentMode = AccentMode.Theme
        accentIndex = index
        prefs.edit().putInt("accent_" + preset.name, index).putString("accentMode", AccentMode.Theme.name).apply()
        refreshAccentIcon()
    }

    /** The accent icon follows the highlight colour (not the cover one, which changes all the time). */
    private fun refreshAccentIcon() {
        if (appIcon == AppIcon.Accent && accentMode != AccentMode.Cover) AppIcons.apply(app, AppIcon.Accent, currentAccent())
    }

    fun updateAccentMode(value: AccentMode) { accentMode = value; prefs.edit().putString("accentMode", value.name).apply(); refreshAccentIcon() }
    fun updateTitleFont(value: TitleFont) { titleFont = value; prefs.edit().putString("titleFont", value.name).apply() }
    fun updateBackdrop(value: Backdrop) { backdrop = value; prefs.edit().putString("backdrop", value.name).apply() }
    fun updateCorners(value: CoverCorners) { corners = value; prefs.edit().putString("corners", value.name).apply() }
    fun updateCoverShadow(value: Boolean) { coverShadow = value; prefs.edit().putBoolean("coverShadow", value).apply() }
    fun updateCoverTitles(value: Boolean) { coverTitles = value; prefs.edit().putBoolean("coverTitles", value).apply() }
    fun updateReadMark(value: ReadMark) { readMark = value; prefs.edit().putString("readMark", value.name).apply() }
    fun updateShowCarousel(value: Boolean) { showCarousel = value; prefs.edit().putBoolean("showCarousel", value).apply() }
    fun updateCarousel(value: CarouselStyle) { carousel = value; prefs.edit().putString("carousel", value.name).apply() }
    fun updateName(value: String) { name = value.take(40); prefs.edit().putString("name", name).apply() }
    fun updateCoverAnimation(value: Boolean) { coverAnimation = value; prefs.edit().putBoolean("coverAnimation", value).apply() }

    fun updateAppIcon(value: AppIcon) {
        appIcon = value
        prefs.edit().putString("appIcon", value.name).apply()
        AppIcons.apply(app, value, currentAccent())
    }

    fun backdropChanged() {
        backdropVersion = System.currentTimeMillis()
        prefs.edit().putLong("backdropVersion", backdropVersion).apply()
    }

    fun updateSections(value: List<HomeSection>) {
        sections = value
        val a = JSONArray()
        value.forEach { s -> a.put(JSONObject().put("part", s.part.name).put("on", s.on).put("ref", s.ref).put("title", s.title)) }
        prefs.edit().putString("sections", a.toString()).apply()
    }

    fun setSection(key: String, on: Boolean) = updateSections(sections.map { if (it.key == key) it.copy(on = on) else it })

    fun moveSection(key: String, by: Int) {
        val list = sections.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        updateSections(list)
    }

    fun pin(part: HomePart, ref: String, title: String) {
        if (sections.any { it.part == part && it.ref == ref }) return
        // Pinned rows go right after the carousel and "continue reading".
        val list = sections.toMutableList()
        val after = list.indexOfLast { it.part == HomePart.Carousel || it.part == HomePart.Continue || it.ref.isNotEmpty() }
        list.add(after + 1, HomeSection(part, true, ref, title))
        updateSections(list)
    }

    fun unpin(key: String) = updateSections(sections.filterNot { it.key == key })

    private fun loadSections(): List<HomeSection> {
        val saved = runCatching {
            val a = JSONArray(prefs.getString("sections", null) ?: return@runCatching null)
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching { HomeSection(HomePart.valueOf(o.getString("part")), o.optBoolean("on", true), o.optString("ref"), o.optString("title")) }.getOrNull()
            }
        }.getOrNull()
        val list = saved?.toMutableList() ?: DEFAULT_SECTIONS.toMutableList()
        // Parts added in later versions show up at the end.
        DEFAULT_SECTIONS.filter { d -> list.none { it.part == d.part } }.forEach { list += it }
        return list
    }

    /** Live while the fingers move; [saveCoverScale] once they lift. */
    fun scaleCovers(factor: Float) { coverScale = (coverScale * factor).coerceIn(MIN_SCALE, MAX_SCALE) }
    fun saveCoverScale() { prefs.edit().putFloat("coverScale", coverScale).apply() }

    fun setWelcomed() { welcomed = true; prefs.edit().putBoolean("welcomed", true).apply() }

    fun systemDark(): Boolean =
        (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** The theme being drawn, System resolved. */
    fun resolved(systemDark: Boolean = systemDark()): ThemePreset = preset.resolve(systemDark)

    /** The highlight colour for the current settings. */
    fun currentAccent(systemDark: Boolean = systemDark()): Color {
        val theme = resolved(systemDark)
        val palette = Palette.of(theme, Color.Black)
        val own = theme.accents.let { it.getOrNull(accentIndex) ?: it.first() }.color
        return when (accentMode) {
            AccentMode.Theme -> own
            AccentMode.Cover -> coverTint?.let { accentFromCover(it, palette.light) } ?: own
            AccentMode.Wallpaper -> wallpaperAccent(palette.light) ?: own
        }
    }

    private fun wallpaperAccent(light: Boolean): Color? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching { Color(app.getColor(if (light) android.R.color.system_accent1_600 else android.R.color.system_accent1_200)) }.getOrNull()
    }

    val wallpaperAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Applies the look; called before the first frame and again on every change. */
    fun apply(systemDark: Boolean = systemDark()) {
        val theme = resolved(systemDark)
        FwipColors.set(Palette.of(theme, currentAccent(systemDark)), titleFont)
    }

    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2.2f

        val DEFAULT_SECTIONS = listOf(
            HomeSection(HomePart.Greeting, on = false),
            HomeSection(HomePart.Carousel),
            HomeSection(HomePart.Shelf),
            HomeSection(HomePart.Continue),
            HomeSection(HomePart.Week),
            HomeSection(HomePart.Want),
            HomeSection(HomePart.Recent),
        )

        @Volatile private var instance: UiSettings? = null
        fun get(context: Context): UiSettings =
            instance ?: synchronized(this) { instance ?: UiSettings(context.applicationContext).also { instance = it } }

        /** A cover's colour made readable as an accent on this theme. */
        fun accentFromCover(c: Color, light: Boolean): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c.toArgb(), hsv)
            hsv[1] = hsv[1].coerceIn(0.4f, 0.85f)
            hsv[2] = if (light) hsv[2].coerceIn(0.35f, 0.55f) else hsv[2].coerceIn(0.78f, 0.95f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
}
