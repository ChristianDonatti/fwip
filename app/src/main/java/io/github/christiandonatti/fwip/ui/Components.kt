package io.github.christiandonatti.fwip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.theme.Unbounded
import java.io.File
import kotlin.math.max

/** Big soft color blobs behind the library that follow the selected cover, launcher style. */
@Composable
fun AmbientBackground(color: Color, intensity: Float = 0.55f, content: @Composable BoxScope.() -> Unit) {
    val animated by animateColorAsState(color, tween(700), label = "ambient")
    Box(
        Modifier
            .fillMaxSize()
            .background(FwipColors.Ink)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = max(size.width, size.height)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(animated.copy(alpha = intensity), animated.copy(alpha = intensity * 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.05f),
                    radius = r * 0.75f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(animated.copy(alpha = intensity * 0.25f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 1.05f),
                    radius = r * 0.55f,
                )
            )
        }
        content()
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    filled: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) FwipColors.Amber else FwipColors.Surface)
            .border(1.dp, if (filled) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (filled) FwipColors.AmberInk else FwipColors.Text, modifier = Modifier.size(size * 0.45f))
    }
}

enum class PillStyle { Solid, Ghost, Amber }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Solid,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val (bg, fg) = when (style) {
        PillStyle.Solid -> FwipColors.Text to FwipColors.Ink
        PillStyle.Ghost -> FwipColors.Surface to FwipColors.Text
        PillStyle.Amber -> FwipColors.Amber to FwipColors.AmberInk
    }
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.35f))
            .then(if (style == PillStyle.Ghost) Modifier.border(1.dp, FwipColors.Stroke, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val tint = fg.copy(alpha = if (enabled) 1f else 0.4f)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint) {
            leading?.invoke(this)
            Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
            trailing?.invoke(this)
        }
    }
}

/** A cover from the library, or a generated placeholder while the real one is being rendered. */
@Composable
fun CoverImage(item: LibraryItem, modifier: Modifier = Modifier, corner: Dp = 18.dp, titleSize: Int = 26) {
    val shape = RoundedCornerShape(corner)
    val base = item.color?.let { Color(it) } ?: placeholderColor(item.comic.id)
    Box(
        modifier
            .clip(shape)
            .background(base.copy(alpha = 1f).darken(0.45f))
    ) {
        if (item.coverPath != null) {
            AsyncImage(
                model = File(item.coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(base, radius = size.width * 0.32f, center = Offset(size.width * 0.66f, size.height * 0.55f))
                drawRect(base.darken(0.7f), topLeft = Offset(0f, size.height * 0.74f))
            }
            Text(
                item.title.uppercase(),
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = titleSize.sp,
                lineHeight = (titleSize * 0.95f).sp,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
            )
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun FwipLogo(modifier: Modifier = Modifier, size: Int = 22) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size((size * 1.3f).dp)) {
            val u = this.size.width / 24f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * u)
            val c = FwipColors.Text
            val cr = androidx.compose.ui.geometry.CornerRadius(2f * u)
            drawRoundRect(c, Offset(3 * u, 3 * u), androidx.compose.ui.geometry.Size(8 * u, 18 * u), cr, style = stroke)
            drawRoundRect(c, Offset(13 * u, 3 * u), androidx.compose.ui.geometry.Size(8 * u, 8 * u), cr, style = stroke)
            drawRoundRect(c, Offset(13 * u, 13 * u), androidx.compose.ui.geometry.Size(8 * u, 8 * u), cr, style = stroke)
        }
        Text("fwip", fontFamily = Unbounded, fontWeight = FontWeight(700), fontSize = size.sp, letterSpacing = (-0.4).sp, color = FwipColors.Text)
    }
}

fun Color.darken(factor: Float): Color = Color(red * (1 - factor), green * (1 - factor), blue * (1 - factor), alpha)

/** Ambient color that is never too dark to glow nor too bright to read text on. */
fun Color.forGlow(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(255, (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()),
        hsv,
    )
    hsv[1] = hsv[1].coerceIn(0.35f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.45f, 0.8f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private val placeholderPalette = listOf(
    Color(0xFF2E6BB0), Color(0xFFB23A2A), Color(0xFF6D3AD6), Color(0xFF3F7A33), Color(0xFFD9643A), Color(0xFFB8862A),
)

fun placeholderColor(id: String): Color = placeholderPalette[(id.hashCode() and 0x7fffffff) % placeholderPalette.size]

fun LibraryItem.glowColor(): Color = (color?.let { Color(it) } ?: placeholderColor(comic.id)).forGlow()
