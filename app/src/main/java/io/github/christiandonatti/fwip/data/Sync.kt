package io.github.christiandonatti.fwip.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.christiandonatti.fwip.remote.BlockReader
import io.github.christiandonatti.fwip.remote.NetClients
import io.github.christiandonatti.fwip.remote.NetUris
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Name of a comic that is the same on every device: its file name and size. A folder synced
 * with Syncthing, or read from the same NAS, gives the same key on the tablet and the phone.
 */
fun Comic.syncKey(): String = fileName.trim().lowercase() + "|" + size

/**
 * Reading state shared between devices and saved in backups: progress, bookmarks and
 * collections, all keyed by [syncKey]. Merging keeps the newest of each piece.
 */
class SyncState(
    val progress: Map<String, ReadingProgress>,
    val bookmarks: Map<String, BookmarkStore.Entry>,
    val collections: List<SharedCollection>,
    val deleted: Map<String, Long>,
) {
    data class SharedCollection(val id: String, val name: String, val updated: Long, val keys: List<String>)

    val isEmpty: Boolean get() = progress.isEmpty() && bookmarks.isEmpty() && collections.isEmpty() && deleted.isEmpty()

    fun toJson(): JSONObject {
        val p = JSONObject()
        progress.forEach { (k, v) -> p.put(k, JSONObject().put("page", v.page).put("count", v.pageCount).put("t", v.lastRead)) }
        val b = JSONObject()
        bookmarks.forEach { (k, v) -> b.put(k, JSONObject().put("pages", JSONArray(v.pages.sorted())).put("t", v.updated)) }
        val c = JSONArray()
        collections.forEach { col ->
            c.put(JSONObject().put("id", col.id).put("name", col.name).put("t", col.updated).put("comics", JSONArray(col.keys)))
        }
        val d = JSONObject()
        deleted.forEach { (k, v) -> d.put(k, v) }
        return JSONObject().put("progress", p).put("bookmarks", b).put("collections", c).put("deleted", d)
    }

    companion object {
        fun fromJson(o: JSONObject): SyncState {
            val progress = mutableMapOf<String, ReadingProgress>()
            o.optJSONObject("progress")?.let { p ->
                p.keys().forEach { k ->
                    val e = p.getJSONObject(k)
                    progress[k] = ReadingProgress(e.optInt("page"), e.optInt("count"), e.optLong("t"))
                }
            }
            val bookmarks = mutableMapOf<String, BookmarkStore.Entry>()
            o.optJSONObject("bookmarks")?.let { b ->
                b.keys().forEach { k ->
                    val e = b.getJSONObject(k)
                    val arr = e.optJSONArray("pages") ?: JSONArray()
                    bookmarks[k] = BookmarkStore.Entry((0 until arr.length()).map { arr.getInt(it) }.toSet(), e.optLong("t"))
                }
            }
            val collections = mutableListOf<SharedCollection>()
            o.optJSONArray("collections")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    val keys = e.optJSONArray("comics") ?: JSONArray()
                    collections += SharedCollection(e.getString("id"), e.optString("name"), e.optLong("t"), (0 until keys.length()).map { keys.getString(it) })
                }
            }
            val deleted = mutableMapOf<String, Long>()
            o.optJSONObject("deleted")?.let { d -> d.keys().forEach { k -> deleted[k] = d.getLong(k) } }
            return SyncState(progress, bookmarks, collections, deleted)
        }

        /** This device's state, keyed for sharing. */
        fun local(repo: LibraryRepository, bookmarks: BookmarkStore, comics: List<Comic>, pending: SyncState?): SyncState {
            val keyOf = comics.associate { it.id to it.syncKey() }
            val progress = mutableMapOf<String, ReadingProgress>()
            // What could not be matched yet (comic not scanned here) still travels on.
            pending?.progress?.let(progress::putAll)
            repo.allProgress().forEach { (id, p) ->
                val key = keyOf[id] ?: return@forEach
                val old = progress[key]
                if (old == null || p.lastRead >= old.lastRead) progress[key] = p
            }
            val marks = mutableMapOf<String, BookmarkStore.Entry>()
            pending?.bookmarks?.let(marks::putAll)
            bookmarks.all().forEach { (id, e) ->
                val key = keyOf[id] ?: return@forEach
                val old = marks[key]
                if (old == null || e.updated >= old.updated) marks[key] = e
            }
            val collections = repo.collections().map { c ->
                SharedCollection(c.id, c.name, c.updated, (c.comicIds.mapNotNull { keyOf[it] } + c.foreign).distinct())
            }
            return SyncState(progress, marks, collections, repo.deletedCollections())
        }
    }
}

/** What [SyncState.applyTo] changed, and what it could not place because the comic is not here (yet). */
class MergeResult(val changed: Boolean, val unmatched: SyncState)

/** Brings newer progress, bookmarks and collections from [this] into the local library. */
fun SyncState.applyTo(repo: LibraryRepository, bookmarks: BookmarkStore, comics: List<Comic>): MergeResult {
    val idsByKey = comics.groupBy({ it.syncKey() }, { it.id })
    var changed = false

    val leftProgress = mutableMapOf<String, ReadingProgress>()
    progress.forEach { (key, p) ->
        val ids = idsByKey[key]
        if (ids == null) { leftProgress[key] = p; return@forEach }
        for (id in ids) {
            val mine = repo.progressFor(id)
            if (mine == null || p.lastRead > mine.lastRead) {
                // Keep the page count measured here if the other side never opened the file.
                repo.putProgress(id, p.copy(pageCount = if (p.pageCount > 0) p.pageCount else mine?.pageCount ?: 0))
                changed = true
            }
        }
    }

    val leftMarks = mutableMapOf<String, BookmarkStore.Entry>()
    this.bookmarks.forEach { (key, e) ->
        val ids = idsByKey[key]
        if (ids == null) { leftMarks[key] = e; return@forEach }
        for (id in ids) {
            val mine = bookmarks.all()[id]
            if (mine == null || e.updated > mine.updated) {
                bookmarks.set(id, e.pages, e.updated)
                changed = true
            }
        }
    }

    val localCollections = repo.collections()
    val tombstones = repo.deletedCollections()
    val byId = localCollections.associateBy { it.id }.toMutableMap()
    val newTombstones = mutableMapOf<String, Long>()
    deleted.forEach { (id, t) ->
        val mine = byId[id]
        if (mine != null && t >= mine.updated) {
            byId.remove(id)
            changed = true
        }
        if ((tombstones[id] ?: 0L) < t) newTombstones[id] = t
    }
    for (c in collections) {
        if ((tombstones[c.id] ?: -1L) >= c.updated || (deleted[c.id] ?: -1L) >= c.updated) continue
        val mine = byId[c.id]
        if (mine == null || c.updated > mine.updated) {
            val local = c.keys.flatMap { idsByKey[it].orEmpty() }
            val foreign = c.keys.filter { it !in idsByKey }
            byId[c.id] = ComicCollection(c.id, c.name, local.distinct(), c.updated, foreign)
            changed = true
        }
    }
    if (changed || newTombstones.isNotEmpty()) {
        // Keep the user's order; new ones go at the end.
        val order = localCollections.map { it.id }
        val merged = order.mapNotNull { byId[it] } + byId.values.filter { it.id !in order }
        repo.saveCollections(merged)
        repo.markCollectionsDeleted(newTombstones)
    }

    return MergeResult(changed, SyncState(leftProgress, leftMarks, emptyList(), emptyMap()))
}

/** Moves collection members found by a new scan out of [ComicCollection.foreign]. */
fun LibraryRepository.resolveForeignMembers(comics: List<Comic>): Boolean {
    val idsByKey = comics.groupBy({ it.syncKey() }, { it.id })
    var changed = false
    val list = collections().map { c ->
        if (c.foreign.none { it in idsByKey }) return@map c
        changed = true
        c.copy(
            comicIds = (c.comicIds + c.foreign.flatMap { idsByKey[it].orEmpty() }).distinct(),
            foreign = c.foreign.filter { it !in idsByKey },
        )
    }
    if (changed) saveCollections(list)
    return changed
}

/** Where the shared state lives: a folder on this device (kept in step by Syncthing & co.) or a network folder. */
sealed interface SyncPlace {
    val uri: Uri

    fun readAll(context: Context): Map<String, JSONObject>
    fun write(context: Context, name: String, text: String)

    /** A folder picked with the system file picker (read and write access). */
    data class Local(override val uri: Uri) : SyncPlace {
        private fun dir(context: Context, create: Boolean): Uri? {
            val cr = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, rootId)
            cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == SyncManager.DIR) return DocumentsContract.buildDocumentUriUsingTree(uri, c.getString(0))
                }
            }
            if (!create) return null
            val parent = DocumentsContract.buildDocumentUriUsingTree(uri, rootId)
            return DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, SyncManager.DIR)
        }

        private fun files(context: Context, dir: Uri): Map<String, Uri> {
            val out = mutableMapOf<String, Uri>()
            val dirId = DocumentsContract.getDocumentId(dir)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, dirId)
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) out[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(uri, c.getString(0))
            }
            return out
        }

        override fun readAll(context: Context): Map<String, JSONObject> {
            val dir = dir(context, create = false) ?: return emptyMap()
            return files(context, dir).filterKeys { it.endsWith(".json") }.mapNotNull { (name, doc) ->
                runCatching {
                    name to JSONObject(context.contentResolver.openInputStream(doc)!!.use { it.readBytes().decodeToString() })
                }.getOrNull()
            }.toMap()
        }

        override fun write(context: Context, name: String, text: String) {
            val dir = dir(context, create = true) ?: throw java.io.IOException("No sync folder")
            val cr = context.contentResolver
            val doc = files(context, dir)[name]
                ?: DocumentsContract.createDocument(cr, dir, "application/json", name)
                ?: throw java.io.IOException("Could not create $name")
            cr.openOutputStream(doc, "wt")!!.use { it.write(text.toByteArray()) }
        }
    }

    /** A folder of an SMB/WebDAV server (fwip-net://server/path). */
    data class Network(override val uri: Uri) : SyncPlace {
        private val dir get() = (NetUris.path(uri) + "/" + SyncManager.DIR).trim('/')

        override fun readAll(context: Context): Map<String, JSONObject> {
            val client = NetClients.forUri(context, uri)
            val entries = runCatching { client.list(dir) }.getOrElse { return emptyMap() }
            return entries.filter { !it.isDir && it.name.endsWith(".json") }.mapNotNull { e ->
                runCatching {
                    val bytes = BlockReader(client.open(e.path)).use { r -> r.inputStream().readBytes() }
                    e.name to JSONObject(bytes.decodeToString())
                }.getOrNull()
            }.toMap()
        }

        override fun write(context: Context, name: String, text: String) {
            val client = NetClients.forUri(context, uri)
            client.mkdirs(dir)
            client.write("$dir/$name", text.toByteArray())
        }
    }

    companion object {
        fun of(uri: Uri): SyncPlace = if (NetUris.isNet(uri)) Network(uri) else Local(uri)
    }
}

/** Other devices seen in the sync folder. */
data class SyncDevice(val name: String, val updated: Long)

/**
 * Keeps this device's file in the sync folder up to date and merges in the files of the others.
 * Each device writes only its own file, so tools like Syncthing never see conflicting edits.
 */
class SyncManager(private val context: Context, private val repo: LibraryRepository) {
    private val prefs = context.getSharedPreferences("fwip_sync", Context.MODE_PRIVATE)
    private val bookmarks = BookmarkStore.get(context)
    private val pendingFile = File(context.filesDir, "sync_pending.json")

    val deviceId: String = prefs.getString("device", null) ?: java.util.UUID.randomUUID().toString().also {
        prefs.edit().putString("device", it).apply()
    }

    val deviceName: String
        get() = runCatching {
            android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: (android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + android.os.Build.MODEL)

    var place: SyncPlace?
        get() = prefs.getString("place", null)?.let { SyncPlace.of(Uri.parse(it)) }
        set(value) { prefs.edit().putString("place", value?.uri?.toString()).apply() }

    val lastSync: Long get() = prefs.getLong("last", 0L)

    val devices: List<SyncDevice>
        get() = runCatching {
            val o = JSONObject(prefs.getString("devices", "{}")!!)
            o.keys().asSequence().map { k -> o.getJSONObject(k).let { SyncDevice(it.optString("name"), it.optLong("t")) } }.toList()
        }.getOrDefault(emptyList())

    private fun pending(): SyncState? = runCatching { SyncState.fromJson(JSONObject(pendingFile.readText())) }.getOrNull()

    private fun savePending(state: SyncState) {
        if (state.isEmpty) pendingFile.delete() else runCatching { pendingFile.writeText(state.toJson().toString()) }
    }

    /** Merges [incoming] into the library; what does not match a comic here is kept for later scans. */
    fun merge(incoming: SyncState, comics: List<Comic>): Boolean {
        val result = incoming.applyTo(repo, bookmarks, comics)
        val old = pending()
        val left = result.unmatched
        // Pending entries keep the newest value per key.
        val progress = (old?.progress.orEmpty() + left.progress).toMutableMap()
        old?.progress?.forEach { (k, v) -> if ((left.progress[k]?.lastRead ?: -1) < v.lastRead) progress[k] = v }
        val marks = (old?.bookmarks.orEmpty() + left.bookmarks).toMutableMap()
        old?.bookmarks?.forEach { (k, v) -> if ((left.bookmarks[k]?.updated ?: -1) < v.updated) marks[k] = v }
        savePending(SyncState(progress, marks, emptyList(), emptyMap()))
        return result.changed
    }

    /** After a scan: places pending entries and collection members whose comics just showed up. */
    fun afterScan(comics: List<Comic>): Boolean {
        val foreign = repo.resolveForeignMembers(comics)
        val pending = pending() ?: return foreign
        val result = pending.applyTo(repo, bookmarks, comics)
        savePending(result.unmatched)
        return foreign || result.changed
    }

    fun localState(comics: List<Comic>): SyncState = SyncState.local(repo, bookmarks, comics, pending())

    /** Reads the other devices, merges, and writes this device's file. Returns whether anything changed here. */
    fun syncNow(comics: List<Comic>): Boolean {
        val target = place ?: return false
        val mine = "$deviceId.json"
        var changed = false
        val seen = JSONObject()
        for ((name, o) in target.readAll(context)) {
            if (name == mine) continue
            runCatching {
                changed = merge(SyncState.fromJson(o.optJSONObject("state") ?: o), comics) || changed
                seen.put(name, JSONObject().put("name", o.optString("name")).put("t", o.optLong("updated")))
            }
        }
        val out = JSONObject()
            .put("app", "fwip").put("v", 1)
            .put("device", deviceId).put("name", deviceName)
            .put("updated", System.currentTimeMillis())
            .put("state", localState(comics).toJson())
        target.write(context, mine, out.toString())
        prefs.edit().putLong("last", System.currentTimeMillis()).putString("devices", seen.toString()).apply()
        return changed
    }

    companion object {
        const val DIR = ".fwip"
    }
}
