package io.github.christiandonatti.fwip.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str

/** Light or dark look for the library (the reader itself always stays dark). */
enum class ThemeMode(@StringRes private val labelRes: Int) {
    Dark(R.string.theme_dark), Light(R.string.theme_light), System(R.string.theme_system);

    val label: String get() = str(labelRes)
}

/** Highlight colour: a bright tone for dark backgrounds and a deeper one that reads on light ones. */
enum class Accent(@StringRes private val labelRes: Int, val onDark: Color, val onLight: Color) {
    Amber(R.string.accent_amber, Color(0xFFF2B544), Color(0xFFA86F08)),
    Blue(R.string.accent_blue, Color(0xFF6AA8FF), Color(0xFF2360C4)),
    Red(R.string.accent_red, Color(0xFFFF7A6B), Color(0xFFC2382B)),
    Green(R.string.accent_green, Color(0xFF5DD39E), Color(0xFF1C8556)),
    Violet(R.string.accent_violet, Color(0xFFB394FF), Color(0xFF6A43D2)),
    Pink(R.string.accent_pink, Color(0xFFFF86BC), Color(0xFFBE3377));

    val label: String get() = str(labelRes)
}

/** Every colour the interface uses, for one theme and accent. */
data class Palette(
    val light: Boolean,
    val ink: Color,
    val surface: Color,
    val stroke: Color,
    val text: Color,
    val textSoft: Color,
    val textMuted: Color,
    val accent: Color,
    val accentInk: Color,
    val panel: Color,
    val panelHigh: Color,
    val danger: Color,
) {
    companion object {
        fun dark(accent: Accent) = Palette(
            light = false,
            ink = Color(0xFF0B0C10),
            surface = Color(0x0FFFFFFF),
            stroke = Color(0x1AFFFFFF),
            text = Color(0xFFF2F2F5),
            textSoft = Color(0xFFC9CBD6),
            textMuted = Color(0xFFA3A6B4),
            accent = accent.onDark,
            accentInk = Color(0xFF16130C),
            panel = Color(0xFF16171E),
            panelHigh = Color(0xFF1E1F27),
            danger = Color(0xFFFF8A80),
        )

        fun light(accent: Accent) = Palette(
            light = true,
            ink = Color(0xFFF3F1EC),
            surface = Color(0x99FFFFFF),
            stroke = Color(0x17000000),
            text = Color(0xFF17181E),
            textSoft = Color(0xFF3B3E4A),
            textMuted = Color(0xFF686C7B),
            accent = accent.onLight,
            accentInk = Color(0xFFFFFFFF),
            panel = Color(0xFFFFFFFF),
            panelHigh = Color(0xFFEFECE6),
            danger = Color(0xFFC62828),
        )
    }
}

/**
 * The interface colours. They are state: changing the theme or accent redraws everything, and
 * [dim] slides the whole library towards the dark palette while the reader opens over it.
 */
object FwipColors {
    var chosen by mutableStateOf(Palette.dark(Accent.Amber))
        internal set
    private var dark by mutableStateOf(Palette.dark(Accent.Amber))
    /** 0 = the chosen palette, 1 = fully dark (reader on screen). */
    var dim by mutableFloatStateOf(0f)

    internal fun set(palette: Palette, accent: Accent) {
        chosen = palette
        dark = Palette.dark(accent)
    }

    private inline fun pick(f: (Palette) -> Color): Color {
        val c = chosen
        // A dark palette never needs dimming, so it does not even watch for it.
        if (!c.light) return f(c)
        val d = dim
        return when {
            d <= 0f -> f(c)
            d >= 1f -> f(dark)
            else -> lerp(f(chosen), f(dark), d)
        }
    }

    val isLight: Boolean get() = chosen.light && dim < 0.5f

    val Ink get() = pick { it.ink }
    val ReaderBg = Color(0xFF121318)
    val Surface get() = pick { it.surface }
    val Stroke get() = pick { it.stroke }
    val Text get() = pick { it.text }
    val TextSoft get() = pick { it.textSoft }
    val TextMuted get() = pick { it.textMuted }
    /** The accent colour (amber unless changed). */
    val Amber get() = pick { it.accent }
    val AmberInk get() = pick { it.accentInk }
    /** Dialogs and floating bars. */
    val Panel get() = pick { it.panel }
    val PanelHigh get() = pick { it.panelHigh }
    val Danger get() = pick { it.danger }
    val Glow = Color(0xFF2E6BB0)
}

// Static weights generated from the variable fonts (variable axes were not applied on device).
val Unbounded = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium),
    Font(R.font.unbounded_600, FontWeight.SemiBold),
    Font(R.font.unbounded_700, FontWeight.Bold),
)

val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

// No colour here: text takes LocalContentColor, which follows the theme.
private val base = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium)
private val display = TextStyle(fontFamily = Unbounded)

private val FwipTypography = Typography(
    displaySmall = display.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
    headlineSmall = display.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
    titleLarge = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    titleMedium = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontWeight = FontWeight(700), fontSize = 15.sp),
    labelMedium = base.copy(fontWeight = FontWeight(600), fontSize = 13.sp),
    labelSmall = base.copy(fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 1.8.sp),
)

@Composable
fun FwipTheme(mode: ThemeMode = ThemeMode.Dark, accent: Accent = Accent.Amber, content: @Composable () -> Unit) {
    val light = when (mode) {
        ThemeMode.Dark -> false
        ThemeMode.Light -> true
        ThemeMode.System -> !isSystemInDarkTheme()
    }
    val palette = if (light) Palette.light(accent) else Palette.dark(accent)
    if (FwipColors.chosen != palette) SideEffect { FwipColors.set(palette, accent) }
    // Read through FwipColors so the scheme follows the reader's dimming too.
    val scheme = if (FwipColors.isLight) {
        lightColorScheme(
            primary = FwipColors.Text,
            onPrimary = FwipColors.Ink,
            secondary = FwipColors.Amber,
            onSecondary = FwipColors.AmberInk,
            background = FwipColors.Ink,
            onBackground = FwipColors.Text,
            surface = FwipColors.Panel,
            onSurface = FwipColors.Text,
            surfaceVariant = FwipColors.PanelHigh,
            onSurfaceVariant = FwipColors.TextSoft,
        )
    } else {
        darkColorScheme(
            primary = FwipColors.Text,
            onPrimary = FwipColors.Ink,
            secondary = FwipColors.Amber,
            onSecondary = FwipColors.AmberInk,
            background = FwipColors.Ink,
            onBackground = FwipColors.Text,
            surface = FwipColors.Panel,
            onSurface = FwipColors.Text,
            surfaceVariant = FwipColors.PanelHigh,
            onSurfaceVariant = FwipColors.TextSoft,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = FwipTypography) {
        // Text drawn outside a Surface would otherwise default to black.
        CompositionLocalProvider(LocalContentColor provides FwipColors.Text, content = content)
    }
}
