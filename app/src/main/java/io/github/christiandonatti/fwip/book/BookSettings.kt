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
    Instant(R.string.turn_instant), Slide(R.string.turn_slide), Scroll(R.string.turn_scroll);

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
    var turn by mutableStateOf(enumPref("turn", BookTurn.Instant))
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

    fun reset() {
        updateTheme(BookTheme.Paper); updateFont(BookFontKind.Literata); updateFontSize(1f); updateLineHeight(1.5f)
        updateMargins(1f); updateJustify(true); updateHyphens(true); updateBold(false); updatePublisherStyles(false)
        updateColumns(BookColumns.Auto); updateTurn(BookTurn.Instant); updateProgress(BookProgress.ChapterTime); updateTtsRate(1f)
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("theme", theme.name).put("font", font.name).put("customFont", customFont).put("fontSize", fontSize.toDouble())
        .put("lineHeight", lineHeight.toDouble()).put("margins", margins.toDouble()).put("justify", justify).put("hyphens", hyphens)
        .put("bold", bold).put("publisher", publisherStyles).put("columns", columns.name).put("turn", turn.name)
        .put("progress", progress.name).put("ttsRate", ttsRate.toDouble())

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
