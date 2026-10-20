package io.github.christiandonatti.fwip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.christiandonatti.fwip.R

object FwipColors {
    val Ink = Color(0xFF0B0C10)
    val ReaderBg = Color(0xFF121318)
    val Surface = Color(0x0FFFFFFF)
    val Stroke = Color(0x1AFFFFFF)
    val Text = Color(0xFFF2F2F5)
    val TextSoft = Color(0xFFC9CBD6)
    val TextMuted = Color(0xFFA3A6B4)
    val Amber = Color(0xFFF2B544)
    val AmberInk = Color(0xFF1A1405)
    val Glow = Color(0xFF2E6BB0)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Unbounded = FontFamily(
    variable(R.font.unbounded, 500),
    variable(R.font.unbounded, 600),
    variable(R.font.unbounded, 700),
)

val Manrope = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
)

private val base = TextStyle(fontFamily = Manrope, color = FwipColors.Text)

private val FwipTypography = Typography(
    displaySmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(700), fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(600), fontSize = 18.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontWeight = FontWeight(700), fontSize = 15.sp),
    labelMedium = base.copy(fontWeight = FontWeight(600), fontSize = 13.sp),
    labelSmall = base.copy(fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 1.8.sp),
)

@Composable
fun FwipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FwipColors.Text,
            onPrimary = FwipColors.Ink,
            secondary = FwipColors.Amber,
            onSecondary = FwipColors.AmberInk,
            background = FwipColors.Ink,
            onBackground = FwipColors.Text,
            surface = Color(0xFF16171E),
            onSurface = FwipColors.Text,
            surfaceVariant = Color(0xFF1E1F27),
            onSurfaceVariant = FwipColors.TextSoft,
        ),
        typography = FwipTypography,
        content = content,
    )
}
