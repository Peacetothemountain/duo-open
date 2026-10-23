package com.duoglass.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.duoglass.R

/**
 * iOS 27 system palette (HIG colour values), light and dark.
 * Label tiers are the UIKit opacities over the base label colour.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val blue: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val yellow: Color,
    val indigo: Color,
    val purple: Color,
    val teal: Color,
    val pink: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    val separator: Color,
    val background: Color,
    val groupedBackground: Color,
    val cell: Color,
    val cellPressed: Color,
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,
    /** Switch track when off. */
    val switchOff: Color,
)

val DarkPalette = Palette(
    dark = true,
    blue = Color(0xFF0091FF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9230),
    red = Color(0xFFFF4245),
    yellow = Color(0xFFFFD600),
    indigo = Color(0xFF6D7CFF),
    purple = Color(0xFFDB34F2),
    teal = Color(0xFF00D2E0),
    pink = Color(0xFFFF375F),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x29EBEBF5),
    separator = Color(0x99545458),
    background = Color.Black,
    groupedBackground = Color.Black,
    cell = Color(0xFF1C1C1E),
    cellPressed = Color(0xFF2C2C2E),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    switchOff = Color(0x63787880),
)

val LightPalette = Palette(
    dark = false,
    blue = Color(0xFF0088FF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF8D28),
    red = Color(0xFFFF383C),
    yellow = Color(0xFFFFCC00),
    indigo = Color(0xFF6155F5),
    purple = Color(0xFFCB30E0),
    teal = Color(0xFF00C3D0),
    pink = Color(0xFFFF2D55),
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    separator = Color(0x4A3C3C43),
    background = Color.White,
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color.White,
    cellPressed = Color(0xFFE5E5EA),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    switchOff = Color(0x29787880),
)

/** Inter (OFL) standing in for SF Pro: text optical size below 20 pt, display above. */
@OptIn(ExperimentalTextApi::class)
private fun inter(opsz: Float): FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.inter_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opsz)),
        )
    },
)

val InterText = inter(14f)
val InterDisplay = inter(32f)

/** The iOS Dynamic Type ramp at the default size (pt → sp), tracking tightened for Inter. */
@Immutable
data class Typography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
    /** Big rounded numerals for live readouts. */
    val numeral: TextStyle,
)

private fun style(family: FontFamily, size: Int, line: Int, weight: Int, tracking: TextUnit) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking,
)

val AppTypography = Typography(
    largeTitle = style(InterDisplay, 34, 41, 700, (-0.022).em),
    title1 = style(InterDisplay, 28, 34, 700, (-0.02).em),
    title2 = style(InterDisplay, 22, 28, 700, (-0.017).em),
    title3 = style(InterDisplay, 20, 25, 600, (-0.015).em),
    headline = style(InterText, 17, 22, 600, (-0.012).em),
    body = style(InterText, 17, 22, 400, (-0.012).em),
    callout = style(InterText, 16, 21, 400, (-0.01).em),
    subhead = style(InterText, 15, 20, 400, (-0.008).em),
    footnote = style(InterText, 13, 18, 400, (-0.004).em),
    caption1 = style(InterText, 12, 16, 400, 0.em),
    caption2 = style(InterText, 11, 13, 500, 0.01.em),
    numeral = style(InterDisplay, 64, 68, 700, (-0.04).em),
)

/** Springs in the spirit of SwiftUI's .smooth / .snappy / .bouncy (≈0.5 s response). */
object Motion {
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 260f)
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.68f, stiffness = 380f)
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val LocalTypography = staticCompositionLocalOf { AppTypography }

object Glass {
    val colors: Palette @Composable get() = LocalPalette.current
    val type: Typography @Composable get() = LocalTypography.current
}

@Composable
fun GlassTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides if (dark) DarkPalette else LightPalette,
        LocalTypography provides AppTypography,
        content = content,
    )
}
