package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/**
 * First launch: what Fwip does, how it should look, and where the comics are. Three short
 * steps; every one can be skipped.
 */
@Composable
fun WelcomeScreen(onPickFolder: () -> Unit, onServers: () -> Unit, onSkip: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (wide) 48.dp else 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FwipLogo(size = if (wide) 22 else 19)
                Spacer(Modifier.weight(1f))
                if (step < 2) {
                    Text(
                        str(R.string.welcome_skip),
                        style = MaterialTheme.typography.labelMedium,
                        color = FwipColors.TextSoft,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onSkip)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 5 * dir } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 5 * dir } + fadeOut(tween(180)))
                },
                label = "welcome",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { s ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .widthIn(max = 560.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        when (s) {
                            0 -> Intro(wide)
                            1 -> Look()
                            else -> Comics(onPickFolder, onServers, onSkip)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    repeat(3) { i ->
                        val w by animateDpAsState(if (i == step) 26.dp else 8.dp, label = "dot")
                        Box(
                            Modifier
                                .height(8.dp)
                                .width(w)
                                .clip(CircleShape)
                                .background(if (i == step) FwipColors.Amber else FwipColors.Stroke)
                        )
                    }
                }
                if (step > 0) {
                    PillButton(str(R.string.back), { step-- }, style = PillStyle.Ghost, height = 50.dp)
                    Spacer(Modifier.width(10.dp))
                }
                if (step < 2) {
                    PillButton(str(R.string.welcome_next), { step++ }, height = 50.dp)
                }
            }
        }
    }
}

@Composable
private fun Intro(wide: Boolean) {
    FannedCovers(Modifier.size(if (wide) 300.dp else 240.dp, if (wide) 210.dp else 170.dp))
    Text(
        str(R.string.welcome_title),
        style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Text(str(R.string.welcome_subtitle), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Feature(Glyph.Page, str(R.string.welcome_f1_title), str(R.string.welcome_f1))
    Feature(Glyph.Balloon, str(R.string.welcome_f2_title), str(R.string.welcome_f2))
    Feature(Glyph.Shelf, str(R.string.welcome_f3_title), str(R.string.welcome_f3))
}

@Composable
private fun Look() {
    Text(str(R.string.welcome_look_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(str(R.string.welcome_look), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    AppearancePicker(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
            .padding(20.dp)
    )
    Text(str(R.string.appearance_reader_note), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, textAlign = TextAlign.Center)
}

@Composable
private fun Comics(onPickFolder: () -> Unit, onServers: () -> Unit, onSkip: () -> Unit) {
    Text(str(R.string.welcome_comics_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(str(R.string.empty_hint), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    PillButton(
        str(R.string.choose_folder),
        onPickFolder,
        style = PillStyle.Amber,
        modifier = Modifier.fillMaxWidth(),
        leading = { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)) },
    )
    PillButton(str(R.string.welcome_servers), onServers, style = PillStyle.Ghost, modifier = Modifier.fillMaxWidth())
    Text(str(R.string.welcome_servers_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, textAlign = TextAlign.Center)
    Text(
        str(R.string.welcome_later),
        style = MaterialTheme.typography.labelMedium,
        color = FwipColors.TextSoft,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onSkip)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private enum class Glyph { Page, Balloon, Shelf }

@Composable
private fun Feature(glyph: Glyph, title: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(FwipColors.Amber.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { FeatureGlyph(glyph, FwipColors.Amber, 26.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
    }
}

@Composable
private fun FeatureGlyph(glyph: Glyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.08f)
        when (glyph) {
            Glyph.Page -> {
                // A page with its corner lifting.
                val p = Path().apply {
                    moveTo(w * 0.2f, h * 0.12f)
                    lineTo(w * 0.62f, h * 0.12f)
                    lineTo(w * 0.82f, h * 0.32f)
                    lineTo(w * 0.82f, h * 0.88f)
                    lineTo(w * 0.2f, h * 0.88f)
                    close()
                }
                drawPath(p, color, style = stroke)
                val fold = Path().apply {
                    moveTo(w * 0.62f, h * 0.12f)
                    lineTo(w * 0.62f, h * 0.32f)
                    lineTo(w * 0.82f, h * 0.32f)
                }
                drawPath(fold, color, style = stroke)
            }
            Glyph.Balloon -> {
                drawOval(color, Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.6f), style = stroke)
                val tail = Path().apply {
                    moveTo(w * 0.3f, h * 0.68f)
                    lineTo(w * 0.22f, h * 0.92f)
                    lineTo(w * 0.46f, h * 0.72f)
                }
                drawPath(tail, color, style = stroke)
            }
            Glyph.Shelf -> {
                listOf(0.12f, 0.4f, 0.68f).forEachIndexed { i, x ->
                    drawRoundRect(
                        color,
                        topLeft = Offset(w * x, h * (0.16f + i * 0.04f)),
                        size = Size(w * 0.22f, h * (0.7f - i * 0.04f)),
                        cornerRadius = CornerRadius(w * 0.04f),
                        style = stroke,
                    )
                }
            }
        }
    }
}

/** Three made-up covers fanned out, in the accent and two neighbouring colours. */
@Composable
private fun FannedCovers(modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val h = maxHeight * 0.92f
        val w = h * (2f / 3f)
        val colors = listOf(Color(0xFF2E6BB0), FwipColors.Amber, Color(0xFFB23A2A))
        listOf(-1f, 1f, 0f).forEach { pos ->
            val c = colors[(pos + 1).toInt()]
            Box(
                Modifier
                    .size(w, h)
                    .graphicsLayer {
                        translationX = (maxWidth * 0.26f * pos).toPx()
                        rotationZ = pos * 9f
                        val s = if (pos == 0f) 1f else 0.88f
                        scaleX = s; scaleY = s
                    }
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.darken(0.45f)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c, radius = size.width * 0.34f, center = Offset(size.width * 0.62f, size.height * 0.5f))
                    drawRect(c.darken(0.7f), topLeft = Offset(0f, size.height * 0.76f))
                    drawRect(Color.White.copy(alpha = 0.9f), topLeft = Offset(size.width * 0.12f, size.height * 0.1f), size = Size(size.width * 0.5f, size.height * 0.07f))
                    drawRect(Color.White.copy(alpha = 0.6f), topLeft = Offset(size.width * 0.12f, size.height * 0.2f), size = Size(size.width * 0.32f, size.height * 0.04f))
                }
            }
        }
    }
}
