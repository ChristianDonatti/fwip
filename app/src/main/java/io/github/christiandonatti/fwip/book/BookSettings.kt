package io.github.christiandonatti.fwip.book

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import java.io.File

/** Page colours for books, as on e-readers and in Apple Books: background and text. */
enum class BookTheme(@androidx.annotation.StringRes private val labelRes: Int, val background: Int, val text: Int, val dark: Boolean) {
    White(R.string.book_theme_white, 0xFFFFFFFF.toInt(), 0xFF1B1B1D.toInt(), false),
    Paper(R.string.book_theme_paper, 0xFFF3EAD7.toInt(), 0xFF3A2F22.toInt(), false),
    Ink(R.string.book_theme_ink, 0xFFE6E3DA.toInt(), 0xFF1E1E21.toInt(), false),
    Calm(R.string.book_theme_calm, 0xFFE4E8DA.toInt(), 0xFF2C3428.toInt(), false),
    Gray(R.string.book_theme_gray, 0xFF4A4B50.toInt(), 0xFFEDEDED.toInt(), true),
    Night(R.string.book_theme_night, 0xFF1B1B1E.toInt(), 0xFFCFCCC4.toInt(), true),
    Black(R.string.book_theme_black, 0xFF000000.toInt(), 0xFFB9B9B9.toInt(), true);

    val label: String get() = str(labelRes)
}

/** Typefaces for books: the book's own, the ones shipped with Fwip, generic ones, and the user's. */
enum class BookFontKind { Original, Literata, Bitter, Atkinson, Serif, Sans, Dyslexic, Custom }

enum class BookTurn(@androidx.annotation.StringRes private val labelRes: Int) {
    /** The page curls over like paper, under the finger or on a tap. */
    Paper(R.string.turn_curl), Instant(R.string.turn_instant), Slide(R.string.turn_slide), Scroll(R.string.turn_scroll);

    val label: String get() = str(labelRes)
}

enum class BookColumns(@androidx.annotation.StringRes private val labelRes: Int) {
    Auto(R.string.columns_auto), One(R.string.columns_one), Two(R.string.columns_two);

    val label: String get() = str(labelRes)
}

/** What the bottom line shows; a tap moves to the next one, as on the Kindle. */
enum class BookProgress(@androidx.annotation.StringRes private val labelRes: Int) {
    ChapterTime(R.string.book_progress_chapter), BookTime(R.string.book_progress_book), Page(R.string.book_progress_page), Percent(R.string.book_progress_percent);

    val label: String get() = str(labelRes)
}

/** Book reading preferences, shared state like the comic ones. */
class BookSettings private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("fwip_books", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    var theme by mutableStateOf(enumPref("theme", BookTheme.Paper))
        private set
    var font by mutableStateOf(enumPref("font", BookFontKind.Literata))
        private set
    /** File name of the user's own font, when [font] is Custom. */
    var customFont by mutableStateOf(prefs.getString("customFont", null))
        private set
    var fontSize by mutableFloatStateOf(prefs.getFloat("fontSize", 1.0f))
        private set
    var lineHeight by mutableFloatStateOf(prefs.getFloat("lineHeight", 1.5f))
        private set
    var margins by mutableFloatStateOf(prefs.getFloat("margins", 1.0f))
        private set
    var justify by mutableStateOf(prefs.getBoolean("justify", true))
        private set
    var hyphens by mutableStateOf(prefs.getBoolean("hyphens", true))
        private set
    var bold by mutableStateOf(prefs.getBoolean("bold", false))
        private set
    /** Keep the book's own layout and fonts (some books need it). */
    var publisherStyles by mutableStateOf(prefs.getBoolean("publisher", false))
        private set
    var columns by mutableStateOf(enumPref("columns", BookColumns.Auto))
        private set
    var turn by mutableStateOf(enumPref("turn", BookTurn.Paper))
        private set

    // ---------- The book in 3D, in a place ----------
    /** The page shown as a real book lying in a scene, instead of filling the screen. */
    var book3d by mutableStateOf(prefs.getBoolean("book3d", false))
        private set
    /** How much of the screen the book takes, 0.6 to 0.96. */
    var bookScale by mutableFloatStateOf(prefs.getFloat("bookScale", 0.82f))
        private set
    var scene by mutableStateOf(enumPref("scene", io.github.christiandonatti.fwip.book.scene.Scene.Fireplace))
        private set
    /** Scenes shown as photos; the others are drawn. Each scene remembers its own choice. */
    var photoScenes by mutableStateOf(prefs.getStringSet("photoScenes", setOf("Mountain", "Park", "Beach", "Library"))!!.toSet())
        private set
    var ambientSound by mutableStateOf(prefs.getBoolean("ambientSound", true))
        private set
    var ambientVolume by mutableFloatStateOf(prefs.getFloat("ambientVolume", 0.35f))
        private set
    /** The scene moves a little as the tablet tilts, so the book seems to float in front of it. */
    var parallax by mutableStateOf(prefs.getBoolean("parallax", true))
        private set
    /** Fire, leaves, rain and the rest move; off keeps the scene still (and saves battery). */
    var sceneMotion by mutableStateOf(prefs.getBoolean("sceneMotion", true))
        private set
    var progress by mutableStateOf(enumPref("progress", BookProgress.ChapterTime))
        private set
    var ttsRate by mutableFloatStateOf(prefs.getFloat("ttsRate", 1.0f))
        private set

    fun updateTheme(v: BookTheme) { theme = v; prefs.edit().putString("theme", v.name).apply() }
    fun updateFont(v: BookFontKind, custom: String? = null) {
        font = v; customFont = custom
        prefs.edit().putString("font", v.name).putString("customFont", custom).apply()
    }
    fun updateFontSize(v: Float) { fontSize = v.coerceIn(0.6f, 2.6f); prefs.edit().putFloat("fontSize", fontSize).apply() }
    fun updateLineHeight(v: Float) { lineHeight = v; prefs.edit().putFloat("lineHeight", v).apply() }
    fun updateMargins(v: Float) { margins = v; prefs.edit().putFloat("margins", v).apply() }
    fun updateJustify(v: Boolean) { justify = v; prefs.edit().putBoolean("justify", v).apply() }
    fun updateHyphens(v: Boolean) { hyphens = v; prefs.edit().putBoolean("hyphens", v).apply() }
    fun updateBold(v: Boolean) { bold = v; prefs.edit().putBoolean("bold", v).apply() }
    fun updatePublisherStyles(v: Boolean) { publisherStyles = v; prefs.edit().putBoolean("publisher", v).apply() }
    fun updateColumns(v: BookColumns) { columns = v; prefs.edit().putString("columns", v.name).apply() }
    fun updateTurn(v: BookTurn) { turn = v; prefs.edit().putString("turn", v.name).apply() }
    fun updateProgress(v: BookProgress) { progress = v; prefs.edit().putString("progress", v.name).apply() }
    fun updateTtsRate(v: Float) { ttsRate = v; prefs.edit().putFloat("ttsRate", v).apply() }
    fun updateBook3d(v: Boolean) { book3d = v; prefs.edit().putBoolean("book3d", v).apply() }
    fun updateBookScale(v: Float) { bookScale = v.coerceIn(0.6f, 0.96f); prefs.edit().putFloat("bookScale", bookScale).apply() }
    fun updateScene(v: io.github.christiandonatti.fwip.book.scene.Scene) { scene = v; prefs.edit().putString("scene", v.name).apply() }
    fun isPhoto(v: io.github.christiandonatti.fwip.book.scene.Scene): Boolean = v.name in photoScenes && v.hasPhoto
    fun updatePhoto(v: io.github.christiandonatti.fwip.book.scene.Scene, photo: Boolean) {
        photoScenes = if (photo) photoScenes + v.name else photoScenes - v.name
        prefs.edit().putStringSet("photoScenes", photoScenes).apply()
    }
    fun updateAmbientSound(v: Boolean) { ambientSound = v; prefs.edit().putBoolean("ambientSound", v).apply() }
    fun updateAmbientVolume(v: Float) { ambientVolume = v.coerceIn(0f, 1f); prefs.edit().putFloat("ambientVolume", ambientVolume).apply() }
    fun updateParallax(v: Boolean) { parallax = v; prefs.edit().putBoolean("parallax", v).apply() }
    fun updateSceneMotion(v: Boolean) { sceneMotion = v; prefs.edit().putBoolean("sceneMotion", v).apply() }

    fun reset() {
        updateTheme(BookTheme.Paper); updateFont(BookFontKind.Literata); updateFontSize(1f); updateLineHeight(1.5f)
        updateMargins(1f); updateJustify(true); updateHyphens(true); updateBold(false); updatePublisherStyles(false)
        updateColumns(BookColumns.Auto); updateTurn(BookTurn.Paper); updateProgress(BookProgress.ChapterTime); updateTtsRate(1f)
        updateBook3d(false); updateBookScale(0.82f); updateScene(io.github.christiandonatti.fwip.book.scene.Scene.Fireplace)
        updateAmbientSound(true); updateAmbientVolume(0.35f); updateParallax(true); updateSceneMotion(true)
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("theme", theme.name).put("font", font.name).put("customFont", customFont).put("fontSize", fontSize.toDouble())
        .put("lineHeight", lineHeight.toDouble()).put("margins", margins.toDouble()).put("justify", justify).put("hyphens", hyphens)
        .put("bold", bold).put("publisher", publisherStyles).put("columns", columns.name).put("turn", turn.name)
        .put("progress", progress.name).put("ttsRate", ttsRate.toDouble())
        .put("book3d", book3d).put("bookScale", bookScale.toDouble()).put("scene", scene.name)
        .put("photoScenes", org.json.JSONArray(photoScenes.toList())).put("ambientSound", ambientSound)
        .put("ambientVolume", ambientVolume.toDouble()).put("parallax", parallax).put("sceneMotion", sceneMotion)

    fun fromJson(o: org.json.JSONObject) {
        runCatching { updateTheme(BookTheme.valueOf(o.getString("theme"))) }
        runCatching { updateFont(BookFontKind.valueOf(o.getString("font")), o.optString("customFont").takeIf { it.isNotBlank() && it != "null" }) }
        if (o.has("fontSize")) updateFontSize(o.getDouble("fontSize").toFloat())
        if (o.has("lineHeight")) updateLineHeight(o.getDouble("lineHeight").toFloat())
        if (o.has("margins")) updateMargins(o.getDouble("margins").toFloat())
        if (o.has("justify")) updateJustify(o.getBoolean("justify"))
        if (o.has("hyphens")) updateHyphens(o.getBoolean("hyphens"))
        if (o.has("bold")) updateBold(o.getBoolean("bold"))
        if (o.has("publisher")) updatePublisherStyles(o.getBoolean("publisher"))
        runCatching { updateColumns(BookColumns.valueOf(o.getString("columns"))) }
        runCatching { updateTurn(BookTurn.valueOf(o.getString("turn"))) }
        runCatching { updateProgress(BookProgress.valueOf(o.getString("progress"))) }
        if (o.has("ttsRate")) updateTtsRate(o.getDouble("ttsRate").toFloat())
        if (o.has("book3d")) updateBook3d(o.getBoolean("book3d"))
        if (o.has("bookScale")) updateBookScale(o.getDouble("bookScale").toFloat())
        runCatching { updateScene(io.github.christiandonatti.fwip.book.scene.Scene.valueOf(o.getString("scene"))) }
        o.optJSONArray("photoScenes")?.let { a ->
            photoScenes = (0 until a.length()).map { a.getString(it) }.toSet()
            prefs.edit().putStringSet("photoScenes", photoScenes).apply()
        }
        if (o.has("ambientSound")) updateAmbientSound(o.getBoolean("ambientSound"))
        if (o.has("ambientVolume")) updateAmbientVolume(o.getDouble("ambientVolume").toFloat())
        if (o.has("parallax")) updateParallax(o.getBoolean("parallax"))
        if (o.has("sceneMotion")) updateSceneMotion(o.getBoolean("sceneMotion"))
    }

    companion object {
        @Volatile private var instance: BookSettings? = null
        fun get(context: Context): BookSettings =
            instance ?: synchronized(this) { instance ?: BookSettings(context.applicationContext).also { instance = it } }
    }
}

/** Fonts the user added (TTF or OTF files), kept by the app. */
object UserFonts {
    /** Bumped when a font is added, so open readers declare it. */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun dir(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "woff", "woff2") }?.sortedBy { it.name.lowercase() }.orEmpty()

    /** "MyFont-Regular.ttf" -> "MyFont". */
    fun nameOf(file: File): String = file.nameWithoutExtension.replace(Regex("[-_ ](Regular|Book|Roman|Normal)$", RegexOption.IGNORE_CASE), "").replace('_', ' ')

    /** Copies a picked font file in; returns its file name. */
    fun add(context: Context, uri: android.net.Uri): String? {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in setOf("ttf", "otf", "woff", "woff2")) return null
        val target = File(dir(context), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        version++
        return target.name
    }

    fun mediaType(file: File) = when (file.extension.lowercase()) {
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> "font/ttf"
    }
}
