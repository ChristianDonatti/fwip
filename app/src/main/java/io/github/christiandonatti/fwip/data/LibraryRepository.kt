package io.github.christiandonatti.fwip.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything the library keeps on the device: chosen folders, the list of comics found in them,
 * generated covers and reading progress. Plain JSON files, no database needed at this size.
 */
class LibraryRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("fwip", Context.MODE_PRIVATE)
    private val comicsFile = File(context.filesDir, "library.json")
    private val progressFile = File(context.filesDir, "progress.json")
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val colorsFile = File(context.filesDir, "colors.json")

    private val progress: MutableMap<String, ReadingProgress> = loadProgress()
    private val colors: MutableMap<String, Int> = loadColors()

    // ---------- Folders ----------

    fun folders(): List<Uri> =
        (prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()).map(Uri::parse)

    fun addFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val set = (prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()).toMutableSet()
        set.add(uri.toString())
        prefs.edit().putStringSet(KEY_FOLDERS, set).apply()
    }

    fun removeFolder(uri: Uri) {
        val set = (prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()).toMutableSet()
        set.remove(uri.toString())
        prefs.edit().putStringSet(KEY_FOLDERS, set).apply()
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ---------- Comics ----------

    fun cachedComics(): List<Comic> {
        if (!comicsFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(comicsFile.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val format = runCatching { ComicFormat.valueOf(o.getString("format")) }.getOrNull()
                    ?: return@mapNotNull null
                Comic(
                    id = o.getString("id"),
                    uri = Uri.parse(o.getString("uri")),
                    fileName = o.getString("fileName"),
                    title = o.getString("title"),
                    folder = o.optString("folder"),
                    format = format,
                    size = o.optLong("size"),
                    lastModified = o.optLong("lastModified"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveComics(comics: List<Comic>) {
        val array = JSONArray()
        comics.forEach { c ->
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("uri", c.uri.toString())
                    .put("fileName", c.fileName)
                    .put("title", c.title)
                    .put("folder", c.folder)
                    .put("format", c.format.name)
                    .put("size", c.size)
                    .put("lastModified", c.lastModified)
            )
        }
        comicsFile.writeText(array.toString())
    }

    /** Walks every chosen folder (and its subfolders) looking for comic files. */
    suspend fun scan(): List<Comic> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Comic>()
        for (tree in folders()) {
            runCatching {
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                walk(tree, rootId, folderName = "", depth = 0, out = found)
            }
        }
        val list = found.values.sortedWith(compareBy(NaturalOrder) { it.folder + "/" + it.fileName })
        saveComics(list)
        list
    }

    private fun walk(tree: Uri, docId: String, folderName: String, depth: Int, out: MutableMap<String, Comic>) {
        if (depth > 8) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val subfolders = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) subfolders += id to name
                    continue
                }
                val format = ComicFormat.fromFileName(name) ?: continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                out[uri.toString()] = Comic(
                    id = uri.toString(),
                    uri = uri,
                    fileName = name,
                    title = prettyTitle(name),
                    folder = folderName,
                    format = format,
                    size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                    lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                )
            }
        }
        for ((id, name) in subfolders) walk(tree, id, name, depth + 1, out)
    }

    // ---------- Covers ----------

    fun coverFile(comic: Comic): File = File(coversDir, sha1(comic.id) + ".jpg")

    fun coverPathIfReady(comic: Comic): String? = coverFile(comic).takeIf { it.exists() }?.absolutePath

    fun colorFor(comic: Comic): Int? = colors[comic.id]

    /** Renders the first page as a small cover and remembers its ambient color. */
    suspend fun ensureCover(comic: Comic): Pair<String?, Int?> = withContext(Dispatchers.IO) {
        val file = coverFile(comic)
        if (file.exists() && colors.containsKey(comic.id)) return@withContext file.absolutePath to colors[comic.id]
        try {
            ComicSources.open(context, comic).use { source ->
                if (source.pageCount > 0) {
                    val bitmap = source.decodePage(0, COVER_W, COVER_H)
                    if (bitmap != null) {
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        val color = ambientColor(bitmap)
                        bitmap.recycle()
                        synchronized(colors) {
                            colors[comic.id] = color
                            saveColors()
                        }
                        val known = progress[comic.id]
                        if (known == null || known.pageCount != source.pageCount) {
                            setProgress(comic.id, known?.page ?: 0, source.pageCount, touch = false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Unreadable file: keep the placeholder cover.
        }
        (file.takeIf { it.exists() }?.absolutePath) to colors[comic.id]
    }

    private fun ambientColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        return palette.vibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: DEFAULT_COLOR
    }

    private fun loadColors(): MutableMap<String, Int> = runCatching {
        val o = JSONObject(colorsFile.readText())
        o.keys().asSequence().associateWith { o.getInt(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun saveColors() {
        val o = JSONObject()
        colors.forEach { (k, v) -> o.put(k, v) }
        colorsFile.writeText(o.toString())
    }

    // ---------- Progress ----------

    fun progressFor(id: String): ReadingProgress? = synchronized(progress) { progress[id] }

    fun setProgress(id: String, page: Int, pageCount: Int, touch: Boolean = true) {
        synchronized(progress) {
            val old = progress[id]
            progress[id] = ReadingProgress(
                page = page,
                pageCount = pageCount,
                lastRead = if (touch) System.currentTimeMillis() else (old?.lastRead ?: 0L),
            )
            val o = JSONObject()
            progress.forEach { (k, v) ->
                o.put(k, JSONObject().put("page", v.page).put("count", v.pageCount).put("last", v.lastRead))
            }
            progressFile.writeText(o.toString())
        }
    }

    private fun loadProgress(): MutableMap<String, ReadingProgress> = runCatching {
        val o = JSONObject(progressFile.readText())
        o.keys().asSequence().associateWith { key ->
            val p = o.getJSONObject(key)
            ReadingProgress(p.optInt("page"), p.optInt("count"), p.optLong("last"))
        }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    companion object {
        private const val KEY_FOLDERS = "folders"
        const val COVER_W = 480
        const val COVER_H = 720
        const val DEFAULT_COLOR = 0xFF2E6BB0.toInt()
    }
}
