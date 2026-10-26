package io.github.christiandonatti.fwip.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.christiandonatti.fwip.R

/**
 * The small tick and the paper sound that go with a page turn, each optional.
 *
 * The tick goes straight to the vibration motor: the view's "haptic feedback" constants are
 * silently ignored on many phones and tablets (Samsung among them) or when touch feedback is
 * off in the system settings.
 */
class TurnFeedback(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    /** Sound id of each kind, and which ones are ready to play. */
    private val ids = HashMap<TurnSoundKind, Int>()
    private val ready = java.util.Collections.synchronizedSet(HashSet<Int>())
    private val loaded: Boolean get() = ready.isNotEmpty()
    private var lastSound = 0L

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    /** The crispest tick the motor can do: a primitive, a predefined click, or a short pulse. */
    private val tick: VibrationEffect? = vibrator?.let { v ->
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) ->
                    VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f).compose()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else -> VibrationEffect.createOneShot(18, if (v.hasAmplitudeControl()) 140 else VibrationEffect.DEFAULT_AMPLITUDE)
            }
        }.getOrNull()
    }

    @Volatile private var pendingPreview: Int = 0

    init {
        pool.setOnLoadCompleteListener { p, id, status ->
            if (status == 0) {
                ready += id
                if (id == pendingPreview) { pendingPreview = 0; p.play(id, 1f, 1f, 1, 0, 1f) }
            }
        }
        TurnSoundKind.entries.forEach { ids[it] = pool.load(context, it.raw, 1) }
    }

    fun turn(haptic: Boolean, sound: Boolean, kind: TurnSoundKind = TurnSoundKind.Paper) {
        val soundId = ids[kind] ?: 0
        var stream = 0
        if (haptic) tick?.let { e -> runCatching { vibrator?.vibrate(e) }.onFailure { Log.w("Fwip", "vibrate", it) } }
        if (sound && soundId in ready) {
            // Quick taps through many pages should not stack into noise.
            val now = System.currentTimeMillis()
            if (now - lastSound > 120) {
                lastSound = now
                // Slightly different each time, like real paper.
                val rate = 0.92f + (now % 17) / 100f
                stream = pool.play(soundId, 1f, 1f, 1, 0, rate)
            }
        }
        Log.i("Fwip", "turn feedback haptic=$haptic (motor=${vibrator != null}, effect=${tick != null}) sound=$sound ${kind.name} loaded=$loaded stream=$stream")
    }

    /** Plays a sound once (when picked or switched on), so its volume can be checked right away. */
    fun preview(kind: TurnSoundKind = TurnSoundKind.Paper) {
        val id = ids[kind] ?: return
        if (id in ready) pool.play(id, 1f, 1f, 1, 0, 1f) else pendingPreview = id
        Log.i("Fwip", "turn sound preview ${kind.name} ready=${id in ready}")
    }

    fun release() = pool.release()
}

@Composable
fun rememberTurnFeedback(): TurnFeedback {
    val context = LocalContext.current
    val feedback = remember { TurnFeedback(context.applicationContext) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    return feedback
}
