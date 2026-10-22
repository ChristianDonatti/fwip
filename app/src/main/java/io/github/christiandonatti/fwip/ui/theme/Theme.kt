package io.github.christiandonatti.fwip.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str

/** A highlight colour offered by a theme. */
class AccentColor(@StringRes private val labelRes: Int, val color: Color) {
    val label: String get() = str(labelRes)
}

/** Fonts for titles. Body text always stays Manrope. */
enum class TitleFont(@StringRes private val labelRes: Int, val family: FontFamily, val scale: Float, val tracking: Float) {
    Unbounded(R.string.font_current, UnboundedFamily, 1f, -0.02f),
    Classic(R.string.font_classic, FrauncesFamily, 1.08f, -0.01f),
    Comic(R.string.font_comic, BangersFamily, 1.28f, 0.03f),
    Simple(R.string.font_simple, ManropeFamily, 1.04f, -0.01f);

    val label: String get() = str(labelRes)
}

/**
 * Ready-made looks. Each sets the background, text and panel colours, its own highlight colours
 * and a title font; [System] follows the system's dark mode with Paper by day and Night at night.
 */
enum class ThemePreset(
    @StringRes private val labelRes: Int,
    @StringRes private val descRes: Int,
    val font: TitleFont,
) {
    Night(R.string.theme_night, R.string.theme_night_desc, TitleFont.Unbounded),
    Paper(R.string.theme_paper, R.string.theme_paper_desc, TitleFont.Classic),
    Oled(R.string.theme_oled, R.string.theme_oled_desc, TitleFont.Unbounded),
    Comic(R.string.theme_comic, R.string.theme_comic_desc, TitleFont.Comic),
    Manga(R.string.theme_manga, R.string.theme_manga_desc, TitleFont.Unbounded),
    System(R.string.theme_system, R.string.theme_system_desc, TitleFont.Unbounded);

    val label: String get() = str(labelRes)
    val description: String get() = str(descRes)

    /** The preset actually drawn: System picks Paper or Night. */
    fun resolve(systemDark: Boolean): ThemePreset = if (this == System) (if (systemDark) Night else Paper) else this

    val accents: List<AccentColor>
        get() = when (this) {
            Night, System -> listOf(
                AccentColor(R.string.accent_amber, Color(0xFFF2B544)),
                AccentColor(R.string.accent_blue, Color(0xFF6AA8FF)),
                AccentColor(R.string.accent_coral, Color(0xFFFF7A6B)),
                AccentColor(R.string.accent_mint, Color(0xFF5DD39E)),
                AccentColor(R.string.accent_lilac, Color(0xFFB394FF)),
                AccentColor(R.string.accent_pink, Color(0xFFFF86BC)),
            )
            Paper -> listOf(
                AccentColor(R.string.accent_tomato, Color(0xFFB8452B)),
                AccentColor(R.string.accent_ink_blue, Color(0xFF2F5C8A)),
                AccentColor(R.string.accent_moss, Color(0xFF4F7A3A)),
                AccentColor(R.string.accent_ochre, Color(0xFFA8741A)),
                AccentColor(R.string.accent_wine, Color(0xFF86304A)),
            )
            Oled -> listOf(
                AccentColor(R.string.accent_cyan, Color(0xFF4FD8E8)),
                AccentColor(R.string.accent_amber, Color(0xFFF2B544)),
                AccentColor(R.string.accent_lime, Color(0xFFB6F04A)),
                AccentColor(R.string.accent_magenta, Color(0xFFFF5FC8)),
            )
            Comic -> listOf(
                AccentColor(R.string.accent_yellow, Color(0xFFFFD23F)),
                AccentColor(R.string.accent_red, Color(0xFFE63946)),
                AccentColor(R.string.accent_cyan, Color(0xFF3EC1D3)),
                AccentColor(R.string.accent_orange, Color(0xFFFF8C42)),
            )
            Manga -> listOf(
                AccentColor(R.string.accent_red, Color(0xFFD7263D)),
                AccentColor(R.string.accent_indigo, Color(0xFF3A4BA0)),
                AccentColor(R.string.accent_graphite, Color(0xFF2B2B2B)),
            )
        }
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
    /** Comic dots over the background. */
    val halftone: Boolean = false,
) {
    companion object {
        fun inkFor(accent: Color): Color = if (accent.luminance() > 0.38f) Color(0xFF16130C) else Color.White

        fun of(preset: ThemePreset, accent: Color): Palette = when (preset) {
            ThemePreset.Night, ThemePreset.System -> Palette(
                light = false,
                ink = Color(0xFF0B0C10), surface = Color(0x0FFFFFFF), stroke = Color(0x1AFFFFFF),
                text = Color(0xFFF2F2F5), textSoft = Color(0xFFC9CBD6), textMuted = Color(0xFFA3A6B4),
                accent = accent, accentInk = inkFor(accent),
                panel = Color(0xFF16171E), panelHigh = Color(0xFF1E1F27), danger = Color(0xFFFF8A80),
            )
            ThemePreset.Paper -> Palette(
                light = true,
                ink = Color(0xFFF1E7D3), surface = Color(0xA6FFFBF2), stroke = Color(0x1F3A2A10),
                text = Color(0xFF2A2118), textSoft = Color(0xFF4A3D2E), textMuted = Color(0xFF75654F),
                accent = accent, accentInk = inkFor(accent),
                panel = Color(0xFFFFF9EE), panelHigh = Color(0xFFEDE2CC), danger = Color(0xFFB3261E),
            )
            ThemePreset.Oled -> Palette(
                light = false,
                ink = Color(0xFF000000), surface = Color(0x12FFFFFF), stroke = Color(0x1FFFFFFF),
                text = Color(0xFFFFFFFF), textSoft = Color(0xFFD0D0D6), textMuted = Color(0xFF9A9AA2),
                accent = accent, accentInk = inkFor(accent),
                panel = Color(0xFF0E0E10), panelHigh = Color(0xFF18181B), danger = Color(0xFFFF8A80),
            )
            ThemePreset.Comic -> Palette(
                light = false,
                ink = Color(0xFF1C2C74), surface = Color(0x1FFFFFFF), stroke = Color(0x33FFFFFF),
                text = Color(0xFFFFFFFF), textSoft = Color(0xFFE2E7FF), textMuted = Color(0xFFC3CBF2),
                accent = accent, accentInk = inkFor(accent),
                panel = Color(0xFF22348A), panelHigh = Color(0xFF2A3E9C), danger = Color(0xFFFF9A8F),
                halftone = true,
            )
            ThemePreset.Manga -> Palette(
                light = true,
                ink = Color(0xFFF6F5F1), surface = Color(0xE6FFFFFF), stroke = Color(0x1F000000),
                text = Color(0xFF111111), textSoft = Color(0xFF2E2E31), textMuted = Color(0xFF5E5E62),
                accent = accent, accentInk = inkFor(accent),
                panel = Color(0xFFFFFFFF), panelHigh = Color(0xFFECEBE6), danger = Color(0xFFC62828),
            )
        }

        val defaultDark = of(ThemePreset.Night, Color(0xFFF2B544))
    }
}

/**
 * The interface colours. They are state: changing the theme or accent redraws everything, and
 * [dim] slides the whole library towards the dark palette while the reader opens over it.
 */
object FwipColors {
    var chosen by mutableStateOf(Palette.defaultDark)
        internal set
    private var dark by mutableStateOf(Palette.defaultDark)
    /** 0 = the chosen palette, 1 = fully dark (reader on screen). */
    var dim by mutableFloatStateOf(0f)
    /** The title font of the chosen look. */
    var titleFont by mutableStateOf(TitleFont.Unbounded)
        internal set

    internal fun set(palette: Palette, font: TitleFont) {
        chosen = palette
        dark = Palette.of(ThemePreset.Night, if (palette.light) Color(0xFFF2B544) else palette.accent)
        titleFont = font
    }

    private inline fun pick(f: (Palette) -> Color): Color {
        val c = chosen
        // A dark palette never needs dimming, so it does not even watch for it.
        if (!c.light) return f(c)
        val d = dim
        return when {
            d <= 0f -> f(c)
            d >= 1f -> f(dark)
            else -> lerp(f(c), f(dark), d)
        }
    }

    val isLight: Boolean get() = chosen.light && dim < 0.5f
    val halftone: Boolean get() = chosen.halftone

    val Ink get() = pick { it.ink }
    /** Behind the pages; the reader settings can make it grey or paper. */
    var ReaderBg by mutableStateOf(Color(0xFF121318))
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
val UnboundedFamily = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium),
    Font(R.font.unbounded_600, FontWeight.SemiBold),
    Font(R.font.unbounded_700, FontWeight.Bold),
)

val ManropeFamily = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

val FrauncesFamily = FontFamily(
    Font(R.font.fraunces_600, FontWeight.Medium),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_700, FontWeight.Bold),
)

// Bangers has a single weight; every weight maps to it so nothing gets faked bold.
val BangersFamily = FontFamily(
    Font(R.font.bangers_400, FontWeight.Normal),
    Font(R.font.bangers_400, FontWeight.Medium),
    Font(R.font.bangers_400, FontWeight.SemiBold),
    Font(R.font.bangers_400, FontWeight.Bold),
)

// The wordmark always uses Unbounded, whatever the title font.
val Unbounded = UnboundedFamily
val Manrope = ManropeFamily

private val base = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Medium)

/** Titles in the chosen font, scaled so every font reads at about the same size. */
fun typographyFor(font: TitleFont): Typography {
    val display = TextStyle(fontFamily = font.family)
    fun d(weight: FontWeight, size: Float, line: Float) = display.copy(
        fontWeight = weight,
        fontSize = (size * font.scale).sp,
        lineHeight = (line * font.scale).sp,
        letterSpacing = (size * font.scale * font.tracking).sp,
    )
    return Typography(
        displaySmall = d(FontWeight.Bold, 40f, 44f),
        headlineSmall = d(FontWeight.Bold, 22f, 26f),
        titleLarge = d(FontWeight.SemiBold, 18f, 22f),
        titleMedium = d(FontWeight.SemiBold, 15f, 20f),
        bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.copy(fontWeight = FontWeight(700), fontSize = 15.sp),
        labelMedium = base.copy(fontWeight = FontWeight(600), fontSize = 13.sp),
        labelSmall = base.copy(fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 1.8.sp),
    )
}

/** Wraps the app: colours and fonts come from [FwipColors], which the look settings fill in. */
@Composable
fun FwipTheme(content: @Composable () -> Unit) {
    val font = FwipColors.titleFont
    val typography = remember(font) { typographyFor(font) }
    // Read through FwipColors so the scheme follows the reader's dimming too.
    val scheme = if (FwipColors.isLight) {
        lightColorScheme(
            primary = FwipColors.Text, onPrimary = FwipColors.Ink,
            secondary = FwipColors.Amber, onSecondary = FwipColors.AmberInk,
            background = FwipColors.Ink, onBackground = FwipColors.Text,
            surface = FwipColors.Panel, onSurface = FwipColors.Text,
            surfaceVariant = FwipColors.PanelHigh, onSurfaceVariant = FwipColors.TextSoft,
        )
    } else {
        darkColorScheme(
            primary = FwipColors.Text, onPrimary = FwipColors.Ink,
            secondary = FwipColors.Amber, onSecondary = FwipColors.AmberInk,
            background = FwipColors.Ink, onBackground = FwipColors.Text,
            surface = FwipColors.Panel, onSurface = FwipColors.Text,
            surfaceVariant = FwipColors.PanelHigh, onSurfaceVariant = FwipColors.TextSoft,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text drawn outside a Surface would otherwise default to black.
        CompositionLocalProvider(LocalContentColor provides FwipColors.Text, content = content)
    }
}
