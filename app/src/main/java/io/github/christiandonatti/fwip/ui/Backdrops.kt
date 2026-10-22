package io.github.christiandonatti.fwip.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** The picture behind the library: a copy kept in the app's files, made small (it is blurred anyway). */
object Backdrops {
    fun file(context: Context) = File(context.filesDir, "backdrop.jpg")

    /** Copies an image the user picked (or a comic's cover file) and returns whether it worked. */
    fun set(context: Context, source: Uri): Boolean = runCatching {
        val bytes = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        save(context, bytes)
    }.getOrDefault(false)

    fun set(context: Context, source: File): Boolean = runCatching { save(context, source.readBytes()) }.getOrDefault(false)

    private fun save(context: Context, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1200) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        val tmp = File(context.filesDir, "backdrop.jpg.part")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        return tmp.renameTo(file(context))
    }
}
