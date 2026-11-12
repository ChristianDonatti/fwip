package io.github.christiandonatti.fwip.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.runtime.remember
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
        // A tablet lying down: the first page goes side by side so it all fits without scrolling.
        val landscape = wide && maxHeight < 820.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (wide) 48.dp else 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FwipLogo(size = if (wide) 22 else 19)
                Spacer(Modifier.weight(1f))
                if (step < 3) {
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
                            .widthIn(max = if (s == 0 && landscape) 940.dp else 560.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        when (s) {
                            0 -> if (landscape) IntroSideBySide() else Intro(wide)
                            1 -> Kind { step++ }
                            2 -> Look()
                            else -> Comics(onPickFolder, onServers, onSkip)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    repeat(4) { i ->
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
                if (step < 3) {
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

/** The first page on a tablet lying down: the covers on the left, the words on the right. */
@Composable
private fun IntroSideBySide() {
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        FannedCovers(Modifier.size(380.dp, 266.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(str(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(str(R.string.welcome_subtitle), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
            Spacer(Modifier.height(2.dp))
            Feature(Glyph.Page, str(R.string.welcome_f1_title), str(R.string.welcome_f1))
            Feature(Glyph.Balloon, str(R.string.welcome_f2_title), str(R.string.welcome_f2))
            Feature(Glyph.Shelf, str(R.string.welcome_f3_title), str(R.string.welcome_f3))
        }
    }
}

/** "Comics, books or both?": sets which half of the library comes first and what Fwip talks about. */
@Composable
private fun Kind(onPicked: () -> Unit) {
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    Text(str(R.string.welcome_kind_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(str(R.string.welcome_kind), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    listOf(
        Triple("comics", Glyph.Balloon, R.string.welcome_kind_comics) to R.string.welcome_kind_comics_hint,
        Triple("books", Glyph.Page, R.string.welcome_kind_books) to R.string.welcome_kind_books_hint,
        Triple("both", Glyph.Shelf, R.string.welcome_kind_both) to R.string.welcome_kind_both_hint,
    ).forEach { (t, hint) ->
        val (key, glyph, title) = t
        val on = ui.readsKind == key
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (on) FwipColors.Amber.copy(alpha = 0.14f) else FwipColors.Surface)
                .border(if (on) 2.dp else 1.dp, if (on) FwipColors.Amber else FwipColors.Stroke, RoundedCornerShape(22.dp))
                .clickable(role = Role.RadioButton) { ui.updateReadsKind(key); onPicked() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(FwipColors.Amber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { FeatureGlyph(glyph, FwipColors.Amber, 26.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(str(title), style = MaterialTheme.typography.labelLarge)
                Text(str(hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            }
        }
    }
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
    val kind = UiSettings.get(androidx.compose.ui.platform.LocalContext.current).readsKind
    Text(
        str(when (kind) { "books" -> R.string.welcome_books_title; "both" -> R.string.welcome_both_title; else -> R.string.welcome_comics_title }),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
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

/**
 * The picture of the first screen: an open book in the light of a lamp, a page in the middle
 * of turning, two comics behind it and a speech balloon, in the colours of the theme.
 */
@Composable
private fun FannedCovers(modifier: Modifier) {
    val accent = FwipColors.Amber
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = remember { A11y.reduceMotion(context) }
    val sway = if (still) 0.5f else {
        val t = rememberInfiniteTransition(label = "welcome")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "page")
        v
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val u = size.width / 300f
        val paper = Color(0xFFF6F1E4)
        val ink = Color(0xFF2A2620)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)

        // The lamp's light.
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent), center = p(150f, 110f), radius = 150f * u), 150f * u, p(150f, 110f))

        // Two comics leaning behind the book.
        fun comic(cx: Float, cy: Float, angle: Float, c: Color) = rotate(angle, p(cx, cy)) {
            val w = 78f * u; val h = 112f * u
            val o = Offset(cx * u - w / 2, cy * u - h / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.25f), o + Offset(3f * u, 5f * u), Size(w, h), CornerRadius(5f * u))
            drawRoundRect(c.darken(0.5f), o, Size(w, h), CornerRadius(5f * u))
            drawRect(Color.White.copy(alpha = 0.92f), o + Offset(8f * u, 9f * u), Size(w * 0.55f, 9f * u))
            drawRoundRect(c, o + Offset(8f * u, 26f * u), Size(w - 16f * u, 34f * u), CornerRadius(2f * u))
            drawCircle(Color.White.copy(alpha = 0.85f), 9f * u, o + Offset(w * 0.68f, 40f * u))
            drawRoundRect(c.darken(0.8f), o + Offset(8f * u, 65f * u), Size((w - 20f * u) / 2, 36f * u), CornerRadius(2f * u))
            drawRoundRect(lerp(c, Color.White, 0.25f), o + Offset(12f * u + (w - 20f * u) / 2, 65f * u), Size((w - 20f * u) / 2, 36f * u), CornerRadius(2f * u))
        }
        comic(74f, 82f, -14f, Color(0xFF2E6BB0))
        comic(228f, 80f, 13f, Color(0xFFB23A2A))

        // The open book: its shadow, the cover showing around the pages, the block of sheets.
        val left = 52f; val right = 248f; val top = 86f; val bottom = 178f; val mid = 150f
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center = p(mid, bottom + 14f), radius = 120f * u), p(left - 20f, bottom - 4f), Size((right - left + 40f) * u, 36f * u))
        drawRoundRect(accent.darken(0.55f), p(left - 5f, top - 2f), Size((right - left + 10f) * u, (bottom - top + 12f) * u), CornerRadius(5f * u))
        fun page(x0: Float, x1: Float): Path = Path().apply {
            // The paper rises from the gutter and settles towards the edge.
            val out = if (x1 > x0) 1f else -1f
            moveTo(x0 * u, (top + 6f) * u)
            cubicTo((x0 + out * 22f) * u, (top - 6f) * u, (x0 + out * 60f) * u, (top - 3f) * u, x1 * u, (top + 2f) * u)
            lineTo(x1 * u, (bottom - 2f) * u)
            cubicTo((x0 + out * 60f) * u, (bottom - 7f) * u, (x0 + out * 22f) * u, (bottom - 10f) * u, x0 * u, (bottom + 2f) * u)
            close()
        }
        for (i in 3 downTo 1) {
            translate(0f, i * 2.2f * u) { drawPath(page(mid, left), lerp(paper, ink, 0.12f + 0.06f * i)); drawPath(page(mid, right), lerp(paper, ink, 0.12f + 0.06f * i)) }
        }
        val leftPage = page(mid, left); val rightPage = page(mid, right)
        drawPath(leftPage, paper); drawPath(rightPage, paper)
        // Words on the left.
        clipPath(leftPage) {
            for (i in 0 until 8) {
                val w = if (i == 3 || i == 7) 46f else 74f
                drawLine(ink.copy(alpha = 0.55f), p(left + 12f, top + 16f + i * 9.5f), p(left + 12f + w, top + 14f + i * 9.5f), 2.2f * u, StrokeCap.Round)
            }
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), startX = (mid - 26f) * u, endX = mid * u), p(mid - 26f, top - 10f), Size(26f * u, 110f * u))
        }
        // Panels on the right.
        clipPath(rightPage) {
            drawRoundRect(Color(0xFF2E6BB0), p(mid + 10f, top + 9f), Size(76f * u, 30f * u), CornerRadius(2f * u))
            drawCircle(Color(0xFFFFE08A), 8f * u, p(mid + 68f, top + 22f))
            drawRoundRect(Color(0xFFB23A2A), p(mid + 10f, top + 44f), Size(35f * u, 36f * u), CornerRadius(2f * u))
            drawRoundRect(Color(0xFF3B8F6B), p(mid + 50f, top + 44f), Size(36f * u, 36f * u), CornerRadius(2f * u))
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent), startX = mid * u, endX = (mid + 26f) * u), p(mid, top - 10f), Size(26f * u, 110f * u))
        }
        // The page being turned: lifted from the right edge, bending over the gutter.
        val lift = 0.35f + 0.3f * sway
        val tipX = right - (right - mid) * lift * 0.85f
        val tipY = top - 30f * lift
        val leaf = Path().apply {
            moveTo(mid * u, (top + 6f) * u)
            cubicTo((mid + 20f) * u, (top - 16f * lift) * u, (tipX - 30f) * u, (tipY - 6f) * u, tipX * u, tipY * u)
            lineTo((tipX + 6f) * u, (tipY + (bottom - top) * (1f - lift * 0.25f)) * u)
            cubicTo((tipX - 26f) * u, (bottom - 8f - 10f * lift) * u, (mid + 20f) * u, (bottom - 6f) * u, mid * u, (bottom + 2f) * u)
            close()
        }
        translate(5f * u, 6f * u) { drawPath(leaf, Color.Black.copy(alpha = 0.22f)) }
        drawPath(leaf, Brush.horizontalGradient(listOf(lerp(paper, ink, 0.22f), Color.White, lerp(paper, ink, 0.10f)), startX = mid * u, endX = (tipX + 6f) * u))
        drawPath(leaf, ink.copy(alpha = 0.18f), style = Stroke(0.8f * u))

        // A balloon rising from the page.
        val b = p(236f, 30f + 3f * sway)
        drawRoundRect(Color.Black.copy(alpha = 0.2f), b + Offset(-30f * u, -14f * u), Size(64f * u, 34f * u), CornerRadius(17f * u))
        val tail = Path().apply { moveTo(b.x - 8f * u, b.y + 12f * u); lineTo(b.x - 20f * u, b.y + 32f * u); lineTo(b.x + 6f * u, b.y + 14f * u); close() }
        drawPath(tail, Color.White)
        drawRoundRect(Color.White, b + Offset(-32f * u, -17f * u), Size(64f * u, 34f * u), CornerRadius(17f * u))
        for (i in -1..1) drawCircle(accent.darken(0.9f), 3.6f * u, b + Offset(i * 13f * u, 0f))
    }
}
