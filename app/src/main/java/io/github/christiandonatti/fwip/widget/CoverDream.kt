package io.github.christiandonatti.fwip.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.reader.InkLook
import io.github.christiandonatti.fwip.reader.LightSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The screen saver (Android's "Screen saver" while charging or docked): the cover of what you
 * are reading, like an e-reader's sleep screen. With the e-ink look on it is grey on paper.
 * Covers take turns every minute and drift a little so nothing stays burnt in.
 */
class CoverDream : DreamService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val ink = LightSettings.get(this).ink != InkLook.Off
        val bg = if (ink) 0xFFE6E3DA.toInt() else 0xFF0B0C10.toInt()
        val fg = if (ink) 0xFF222225.toInt() else 0xFFE8E8EC.toInt()
        val soft = if (ink) 0xFF55555A.toInt() else 0xFF9A9CA8.toInt()

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10f))
                }
            }
            clipToOutline = true
            elevation = if (ink) 0f else dp(18f)
            if (ink) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        val title = TextView(this).apply {
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setPadding(0, dp(22f).toInt(), 0, 0)
        }
        val sub = TextView(this).apply {
            setTextColor(soft)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6f).toInt(), 0, 0)
        }
        column.addView(cover, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0).apply { height = (resources.displayMetrics.heightPixels * 0.62f).toInt() })
        column.addView(title)
        column.addView(sub)
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)

        loop = scope.launch {
            val items = withContext(Dispatchers.IO) {
                val lib = WidgetLibrary.load(this@CoverDream)
                (lib.reading.ifEmpty { lib.upNext }).take(8)
            }
            if (items.isEmpty()) {
                title.text = getString(R.string.dream_empty)
                return@launch
            }
            var i = 0
            while (isActive) {
                val item = items[i % items.size]
                val bmp = withContext(Dispatchers.IO) { coverBitmap(item) }
                column.animate().alpha(0f).setDuration(if (i == 0) 0 else 900).withEndAction {
                    cover.setImageBitmap(bmp)
                    title.text = item.title
                    sub.text = item.progress?.let { p -> if (p.pageCount > 0) getString(R.string.dream_progress, (p.fraction * 100).toInt()) else "" } ?: ""
                    // A small drift each time, so the same pixels are not lit for hours.
                    column.translationX = ((i * 37) % 41 - 20) * resources.displayMetrics.density
                    column.translationY = ((i * 53) % 31 - 15) * resources.displayMetrics.density
                    column.animate().alpha(1f).setDuration(1200).start()
                }.start()
                i++
                delay(60_000)
            }
        }
    }

    private fun coverBitmap(item: LibraryItem): Bitmap? = item.coverPath?.let { path ->
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            val target = (resources.displayMetrics.heightPixels * 0.62f).toInt()
            var sample = 1
            while (o.outHeight / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        loop?.cancel()
        scope.cancel()
        super.onDetachedFromWindow()
    }
}
