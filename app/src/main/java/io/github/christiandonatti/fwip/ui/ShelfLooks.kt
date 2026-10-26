package io.github.christiandonatti.fwip.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.christiandonatti.fwip.data.Shelf
import io.github.christiandonatti.fwip.data.seriesKey
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * A series, folder or collection made personal: its own colour, a picture in place of the fanned
 * covers, and (for series and collections) a logo in place of the written title.
 */
object ShelfLooks {
    private lateinit var dir: File
    private lateinit var file: File
    private val colors = mutableMapOf<String, Int>()

    /** Bumped on every change so screens draw again. */
    var revision by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "shelf_art").apply { mkdirs() }
        file = File(context.filesDir, "shelf_looks.json")
        runCatching {
            val o = JSONObject(file.readText()).optJSONObject("colors") ?: JSONObject()
            o.keys().forEach { k -> colors[k] = o.getInt(k) }
        }
    }

    /** A stable name for a shelf, whatever it is called today. */
    fun keyOf(shelf: Shelf): String? = when (shelf) {
        is Shelf.Series -> "s:" + seriesKey(shelf.key)
        is Shelf.Folder -> "f:" + shelf.name
        is Shelf.Custom -> "c:" + shelf.id
        is Shelf.Publisher -> "p:" + io.github.christiandonatti.fwip.publisherKey(shelf.name)
        is Shelf.Smart -> null
    }

    private fun hash(key: String) = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    fun coverFile(key: String) = File(dir, hash(key) + "-cover.jpg")
    fun logoFile(key: String) = File(dir, hash(key) + "-logo.png")

    fun color(shelf: Shelf): Color? { revision; return keyOf(shelf)?.let { colors[it] }?.let { Color(it) } }
    fun cover(shelf: Shelf): File? { revision; return keyOf(shelf)?.let(::coverFile)?.takeIf { it.exists() } }
    fun logo(shelf: Shelf): File? { revision; return keyOf(shelf)?.let(::logoFile)?.takeIf { it.exists() } }

    fun setColor(shelf: Shelf, color: Color?) {
        val key = keyOf(shelf) ?: return
        if (color == null) colors.remove(key) else colors[key] = color.toArgb()
        save()
    }

    private fun save() {
        val c = JSONObject()
        colors.forEach { (k, v) -> c.put(k, v) }
        runCatching { file.writeText(JSONObject().put("colors", c).toString()) }
        revision++
    }

    fun clearCover(shelf: Shelf) { keyOf(shelf)?.let { coverFile(it).delete() }; revision++ }
    fun clearLogo(shelf: Shelf) { keyOf(shelf)?.let { logoFile(it).delete() }; revision++ }

    /** Copies a picked image, made smaller: a JPEG for the cover, a PNG (keeping transparency) for a logo. */
    fun setImage(context: Context, key: String, uri: Uri, logo: Boolean): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val max = if (logo) 1200 else 1000
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val target = if (logo) logoFile(key) else coverFile(key)
        target.outputStream().use { out ->
            bmp.compress(if (logo) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        revision++
        true
    }.getOrDefault(false)
}
