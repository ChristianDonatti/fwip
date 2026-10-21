package io.github.christiandonatti.fwip.balloons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

enum class DetectionKind(val code: Int) {
    Balloon(0),
    /** Panel found by the ML model. */
    Panel(1),
    /** Panel found by cutting along the gutters. */
    Cut(2);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: Panel
    }
}

/** A detected box in page-relative coordinates (0..1). */
data class Detection(val rect: RectF, val kind: DetectionKind, val score: Float)

/**
 * Finds speech balloons and panels on a comic page.
 *
 * Uses the YOLOv4-tiny model trained by the Seeneva project (GPL-3.0). The model takes a batch of
 * three 480x736 RGB images, normalised to 0..1, with the page scaled to fit and padded at the
 * bottom/right, and already applies non-max suppression: it returns up to 50 boxes per image as
 * (y_min, x_min, y_max, x_max, score) relative to the padded input, plus class ids and counts.
 */
class BalloonDetector(context: Context) : AutoCloseable {

    private val interpreter: Interpreter = Interpreter(
        loadModel(context, MODEL),
        Interpreter.Options().setNumThreads(4),
    )

    private val inputShape = interpreter.getInputTensor(0).shape() // [batch, height, width, 3]
    private val batch = inputShape[0]
    private val inH = inputShape[1]
    private val inW = inputShape[2]
    private val maxBoxes = interpreter.getOutputTensor(0).shape()[1]

    private val input: ByteBuffer = ByteBuffer.allocateDirect(batch * inH * inW * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(inW * inH)

    private class Tile(val page: Int, val rect: android.graphics.Rect)

    /**
     * Detects on several pages at once. All tiles of all pages share the model's batch, so a
     * double-page spread usually costs a single inference.
     */
    @Synchronized
    fun detect(pages: List<Bitmap>, minScore: Float = 0.5f): List<List<Detection>> {
        val tiles = pages.flatMapIndexed { i, b -> tiles(b.width, b.height).map { Tile(i, it) } }
        val out = List(pages.size) { mutableListOf<Detection>() }
        for (chunk in tiles.chunked(batch)) {
            input.clear()
            val floats = input.asFloatBuffer()
            val scaled = chunk.map { t ->
                val tile = t.rect
                val s = min(inW / tile.width().toFloat(), inH / tile.height().toFloat())
                val rw = (tile.width() * s).roundToInt().coerceIn(1, inW)
                val rh = (tile.height() * s).roundToInt().coerceIn(1, inH)
                rw to rh
            }
            chunk.forEachIndexed { j, t ->
                val tile = t.rect
                val page = pages[t.page]
                val (rw, rh) = scaled[j]
                val crop = Bitmap.createBitmap(page, tile.left, tile.top, tile.width(), tile.height())
                val small = Bitmap.createScaledBitmap(crop, rw, rh, true)
                if (crop !== page && crop !== small) crop.recycle()
                small.getPixels(pixels, 0, rw, 0, 0, rw, rh)
                if (small !== page) small.recycle()
                val base = j * inH * inW * 3
                for (y in 0 until inH) {
                    for (x in 0 until inW) {
                        val i = base + (y * inW + x) * 3
                        if (x < rw && y < rh) {
                            val c = pixels[y * rw + x]
                            floats.put(i, ((c shr 16) and 0xFF) / 255f)
                            floats.put(i + 1, ((c shr 8) and 0xFF) / 255f)
                            floats.put(i + 2, (c and 0xFF) / 255f)
                        } else {
                            floats.put(i, 0f)
                            floats.put(i + 1, 0f)
                            floats.put(i + 2, 0f)
                        }
                    }
                }
            }
            // Unused batch slots stay black.
            for (k in chunk.size * inH * inW * 3 until batch * inH * inW * 3) floats.put(k, 0f)
            input.rewind()

            val boxes = Array(batch) { Array(maxBoxes) { FloatArray(5) } }
            val classes = Array(batch) { IntArray(maxBoxes) }
            val counts = IntArray(batch)
            interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input), mapOf(0 to boxes, 1 to classes, 2 to counts))

            chunk.forEachIndexed { j, t ->
                val tile = t.rect
                val page = pages[t.page]
                val (rw, rh) = scaled[j]
                for (i in 0 until counts[j].coerceAtMost(maxBoxes)) {
                    val b = boxes[j][i]
                    val score = b[4]
                    if (score < minScore) continue
                    // Relative to the padded model input -> pixels of the tile -> relative to the page.
                    val x0 = tile.left + b[1] * inW / rw * tile.width()
                    val x1 = tile.left + b[3] * inW / rw * tile.width()
                    val y0 = tile.top + b[0] * inH / rh * tile.height()
                    val y1 = tile.top + b[2] * inH / rh * tile.height()
                    val rect = RectF(
                        (x0 / page.width).coerceIn(0f, 1f),
                        (y0 / page.height).coerceIn(0f, 1f),
                        (x1 / page.width).coerceIn(0f, 1f),
                        (y1 / page.height).coerceIn(0f, 1f),
                    )
                    if (rect.width() <= 0f || rect.height() <= 0f) continue
                    val kind = if (classes[j][i] == 0) DetectionKind.Balloon else DetectionKind.Panel
                    out[t.page] += Detection(rect, kind, score)
                }
            }
        }
        return out
    }

    /**
     * Wide pages (double spreads) are cut into vertical strips and very tall pages (webtoon-like)
     * into horizontal bands, so each piece is close to the model's portrait shape.
     */
    private fun tiles(w: Int, h: Int): List<android.graphics.Rect> {
        val target = inW / inH.toFloat()
        val ratio = w / h.toFloat()
        val cols: Int
        val rows: Int
        when {
            ratio > 1f -> { cols = ceil(ratio).toInt(); rows = 1 }
            ratio < target / 1.25f -> { cols = 1; rows = ceil(h * target / w).toInt() }
            else -> { cols = 1; rows = 1 }
        }
        val tw = ceil(w / cols.toFloat()).toInt()
        val th = ceil(h / rows.toFloat()).toInt()
        val list = mutableListOf<android.graphics.Rect>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val left = c * tw
            val top = r * th
            list += android.graphics.Rect(left, top, min(left + tw, w), min(top + th, h))
        }
        return list.filter { it.width() > 0 && it.height() > 0 }
    }

    override fun close() = interpreter.close()

    companion object {
        private const val MODEL = "balloons/balloons.tflite"

        private fun loadModel(context: Context, name: String): ByteBuffer {
            context.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}
