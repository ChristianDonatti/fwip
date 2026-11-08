package io.github.christiandonatti.fwip.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.PillButton
import io.github.christiandonatti.fwip.ui.PillStyle
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/** One line of the gesture card: what you do, what it does. */
data class TipLine(val gesture: String, val effect: String)

/**
 * Shown once, the first time a reader opens: the screen split into where a tap goes back, opens
 * the controls and goes forward, and a short card with the gestures as they are set. A tap
 * anywhere (or "Got it") closes it for good.
 */
@Composable
fun ReaderTips(
    left: String,
    middle: String,
    right: String,
    lines: List<TipLine>,
    note: String,
    onDone: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(450); shown = true }
    AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = str(R.string.tips_done)) { onDone() },
        ) {
            val wide = maxWidth > 600.dp
            Row(Modifier.fillMaxSize()) {
                Zone(left, Modifier.weight(0.3f))
                Zone(middle, Modifier.weight(0.4f), center = true)
                Zone(right, Modifier.weight(0.3f))
            }
            Column(
                Modifier
                    .align(if (wide) Alignment.BottomCenter else Alignment.Center)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(FwipColors.Panel)
                    .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(str(R.string.tips_title), style = MaterialTheme.typography.titleMedium)
                lines.forEach { l ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Text(l.gesture, style = MaterialTheme.typography.labelLarge, color = FwipColors.Amber, modifier = Modifier.weight(0.42f))
                        Text(l.effect, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft, modifier = Modifier.weight(0.58f))
                    }
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                Spacer(Modifier.height(2.dp))
                PillButton(str(R.string.tips_done), onDone, Modifier.fillMaxWidth(), style = PillStyle.Amber, height = 46.dp)
            }
        }
    }
}

@Composable
private fun Zone(label: String, modifier: Modifier, center: Boolean = false) {
    Box(
        modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(8.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, Color.White.copy(alpha = if (center) 0.18f else 0.32f), RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (center) 0.02f else 0.05f)),
        contentAlignment = if (center) Alignment.TopCenter else Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (center) 28.dp else 0.dp),
            )
        }
    }
}

/** The tips for the comic reader, from the reader's own settings (zones, direction, gestures). */
@Composable
fun ComicReaderTips(settings: ReaderSettings, rtl: Boolean, onDone: () -> Unit) {
    fun side(s: Int) = when (settings.tapZones.move(s, rtl)) {
        1 -> str(R.string.tips_next)
        -1 -> str(R.string.tips_back)
        else -> ""
    }
    val lines = buildList {
        add(TipLine(str(R.string.tips_pinch), str(R.string.gesture_zoom)))
        if (settings.gestureDoubleTap != GestureAction.None) add(TipLine(str(R.string.gesture_double_tap), settings.gestureDoubleTap.label))
        if (settings.gestureLongPress != GestureAction.None) add(TipLine(str(R.string.gesture_long_press), settings.gestureLongPress.label))
        if (settings.gestureSwipeUp != GestureAction.None) add(TipLine(str(R.string.gesture_swipe_up), settings.gestureSwipeUp.label))
        if (settings.gestureSwipeDown != GestureAction.None) add(TipLine(str(R.string.gesture_swipe_down), settings.gestureSwipeDown.label))
        if (settings.turnStyle == TurnStyle.Curl) add(TipLine(str(R.string.tips_drag_corner), str(R.string.tips_drag_corner_text)))
    }
    ReaderTips(side(-1), str(R.string.tips_menu), side(1), lines, str(R.string.tips_note_comics), onDone)
}

/** The tips for the book reader: the same three zones, and what a long press and the menu hold. */
@Composable
fun BookReaderTips(paper: Boolean, onDone: () -> Unit) {
    val lines = buildList {
        add(TipLine(str(R.string.tips_long_press_word), str(R.string.tips_long_press_word_text)))
        if (paper) add(TipLine(str(R.string.tips_drag_corner), str(R.string.tips_drag_corner_text)))
        add(TipLine(str(R.string.tips_menu_aa), str(R.string.tips_menu_aa_text)))
    }
    ReaderTips(str(R.string.tips_back), str(R.string.tips_menu), str(R.string.tips_next), lines, str(R.string.tips_note_books), onDone)
}
