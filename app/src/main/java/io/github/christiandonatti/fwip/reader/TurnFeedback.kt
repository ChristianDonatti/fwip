package io.github.christiandonatti.fwip.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.github.christiandonatti.fwip.R

/** The small tick and the paper sound that go with a page turn, each optional. */
class TurnFeedback(context: Context, private val view: View) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val soundId = pool.load(context, R.raw.page_turn, 1)
    private var lastSound = 0L

    fun turn(haptic: Boolean, sound: Boolean) {
        if (haptic) {
            val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(kind)
        }
        if (sound) {
            // Quick taps through many pages should not stack into noise.
            val now = System.currentTimeMillis()
            if (now - lastSound > 120) {
                lastSound = now
                // Slightly different each time, like real paper.
                val rate = 0.92f + (now % 17) / 100f
                pool.play(soundId, 0.55f, 0.55f, 1, 0, rate)
            }
        }
    }

    fun release() = pool.release()
}

@Composable
fun rememberTurnFeedback(): TurnFeedback {
    val context = LocalContext.current
    val view = LocalView.current
    val feedback = remember(view) { TurnFeedback(context, view) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    return feedback
}
