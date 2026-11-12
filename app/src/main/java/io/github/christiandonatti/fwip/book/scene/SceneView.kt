package io.github.christiandonatti.fwip.book.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Seconds since the scene appeared, ticking about 30 times a second while [running]. */
@Composable
private fun rememberSceneClock(running: Boolean): () -> Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        var acc = t
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    acc += (now - last) / 1e9f
                    // About 30 frames a second is plenty for fire and leaves, and kinder to the battery.
                    if (acc - t >= 1f / 30f) t = acc % 3600f
                }
                last = now
            }
        }
    }
    return { t }
}

/** The photo of a scene, softened like a background out of focus. Null while loading or if missing. */
@Composable
private fun rememberScenePhoto(scene: Scene, blur: Int): ImageBitmap? {
    val context = LocalContext.current
    // The photo on screen stays until the next one is decoded: no flash of nothing in between.
    var shown by remember { androidx.compose.runtime.mutableStateOf(PhotoMemo.get(scene, blur)) }
    androidx.compose.runtime.LaunchedEffect(scene, blur) {
        val ready = PhotoMemo.get(scene, blur)
            ?: withContext(Dispatchers.IO) { loadPhoto(context, scene, blur) }?.also { PhotoMemo.put(scene, blur, it) }
        if (ready != null) shown = ready
    }
    return shown
}

/** The last couple of photos decoded, so a second view of the same place shows at once. */
private object PhotoMemo {
    private val map = object : LinkedHashMap<Pair<Scene, Int>, ImageBitmap>(4, 0.75f, true) {
        // Thumbnails are tiny and all eight show at once in the picker; full photos are big.
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Scene, Int>, ImageBitmap>?): Boolean {
            if (keys.count { it.second <= 1 } > 3) {
                keys.firstOrNull { it.second <= 1 }?.let { remove(it) }
            }
            return size > 20
        }
    }
    @Synchronized fun get(scene: Scene, blur: Int): ImageBitmap? = map[scene to blur]
    @Synchronized fun put(scene: Scene, blur: Int, image: ImageBitmap) { map[scene to blur] = image }
}

/** A photo, made small and then stretched back: the cheapest good-looking blur there is. */
internal fun loadPhoto(context: Context, scene: Scene, blur: Int): ImageBitmap? = runCatching {
    val name = "scenes/" + scene.name.lowercase() + ".jpg"
    // Made small while it is decoded: a thumbnail never costs a whole photo.
    val opts = BitmapFactory.Options().apply {
        inSampleSize = Integer.highestOneBit(blur.coerceIn(1, 16))
        inPreferredConfig = Bitmap.Config.RGB_565.takeIf { blur > 1 } ?: Bitmap.Config.ARGB_8888
    }
    val bmp: Bitmap = context.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
    bmp.asImageBitmap()
}.getOrNull()

/** Whether the app carries a photo for [scene] (the drawn one is used otherwise). */
fun scenePhotoExists(context: Context, scene: Scene): Boolean {
    val names = scenePhotos ?: runCatching { context.assets.list("scenes")?.toSet() }.getOrNull().orEmpty().also { scenePhotos = it }
    return (scene.name.lowercase() + ".jpg") in names
}

/** The photos the app carries, listed once: asking the assets is slow and this is asked often. */
@Volatile private var scenePhotos: Set<String>? = null

/** How the tablet is tilted, smoothed, as an offset in -1..1 on each axis. */
@Composable
private fun rememberTilt(enabled: Boolean): () -> Offset {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sm == null || sensor == null) return@DisposableEffect onDispose { tilt = Offset.Zero }
        var rest: FloatArray? = null
        val listener = object : SensorEventListener {
            val rot = FloatArray(9)
            val angles = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, angles)
                // Relative to how the tablet was held when reading began, drifting back slowly.
                val r = rest ?: angles.copyOf().also { rest = it }
                for (i in 1..2) r[i] += (angles[i] - r[i]) * 0.01f
                val target = Offset(((angles[2] - r[2]) / 0.35f).coerceIn(-1f, 1f), ((angles[1] - r[1]) / 0.35f).coerceIn(-1f, 1f))
                tilt += (target - tilt) * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener); tilt = Offset.Zero }
    }
    return { tilt }
}

/**
 * The place behind the book: drawn, or a photo with the same moving touches. With [parallax]
 * it shifts a little against the tablet's tilt, so the book seems to float in front of it.
 */
@Composable
fun SceneBackground(scene: Scene, photo: Boolean, motion: Boolean, parallax: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val painter = remember(scene) { painterFor(scene) }
    val clock = rememberSceneClock(motion)
    // The photos are surfaces seen from above, right under the book: kept sharp.
    val image = if (photo) rememberScenePhoto(scene, blur = 1) else null
    val tilt = rememberTilt(parallax)
    Canvas(
        modifier.graphicsLayer {
            val o = tilt()
            val shift = size.width * 0.018f
            translationX = -o.x * shift
            translationY = -o.y * shift
            if (parallax) { scaleX = 1.05f; scaleY = 1.05f }
        },
    ) {
        val t = clock()
        if (image != null) {
            drawCover(image)
            topDownTouches(scene, t)
        } else {
            with(painter) { drawn(t) }
        }
    }
}

/** A small preview of a scene for the picker: drawn still, or the photo. */
@Composable
fun SceneThumb(scene: Scene, photo: Boolean, modifier: Modifier) {
    val painter = remember(scene) { painterFor(scene) }
    val image = if (photo) rememberScenePhoto(scene, blur = 8) else null
    Canvas(modifier) {
        if (image != null) {
            drawCover(image)
        } else with(painter) { drawn(12f) }
    }
}

/** Draws [image] to fill the whole area, cropping what does not fit. */
private fun DrawScope.drawCover(image: ImageBitmap) {
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val w = (size.width / scale).roundToInt().coerceAtMost(image.width)
    val h = (size.height / scale).roundToInt().coerceAtMost(image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - w) / 2, (image.height - h) / 2),
        srcSize = IntSize(w, h),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** The sound of [scene] while [on] and the app is in front; quiet otherwise. */
@Composable
fun SceneSound(on: Boolean, scene: Scene, volume: Float, duck: Boolean = false) {
    val ambient = AmbientSound.shared
    val me = remember { Any() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var started by remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) started = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) started = false
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); ambient.stop(me) }
    }
    if (on) { ambient.volume = volume; ambient.duck = duck }
    androidx.compose.runtime.LaunchedEffect(on, scene, started) {
        if (on && started) ambient.play(me, scene.sound) else ambient.stop(me)
    }
}
