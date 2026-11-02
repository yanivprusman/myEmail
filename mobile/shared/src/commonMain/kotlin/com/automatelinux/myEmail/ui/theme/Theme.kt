package com.automatelinux.myEmail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

/**
 * myEmail's palette is the icon's: deep ink night, cream paper, one amber seal.
 * Amber is spent on exactly one meaning — "a person is waiting for you" (unread
 * seal, primary action) — so it never competes with itself.
 */
object Ink {
    val Night = Color(0xFF141C33)
    val Navy = Color(0xFF24315C)
    val Dusk = Color(0xFF2B3A66)
    val Text = Color(0xFF1B2340)
    val Soft = Color(0xFF5D6478)
    val Faint = Color(0xFF9097A8)
    val Paper = Color(0xFFF6F3EC)
    val Card = Color(0xFFFFFFFF)
    val Hairline = Color(0xFFE6E0D3)
    val Seal = Color(0xFFE39A2E)
    val SealDeep = Color(0xFFB8661A)
    val SealGlow = Color(0xFFFFCB6B)
    val Cream = Color(0xFFFFFDF8)
    val Danger = Color(0xFFB3261E)
    val Archive = Color(0xFF2E6B57)

    val NightBrush = Brush.linearGradient(listOf(Dusk, Night))
    val SealBrush = Brush.radialGradient(listOf(SealGlow, Seal, SealDeep))
}

private val Colors = lightColorScheme(
    primary = Ink.Navy,
    onPrimary = Color.White,
    secondary = Ink.Seal,
    onSecondary = Ink.Night,
    background = Ink.Paper,
    onBackground = Ink.Text,
    surface = Ink.Paper,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Card,
    onSurfaceVariant = Ink.Soft,
    outline = Ink.Hairline,
    outlineVariant = Ink.Hairline,
    error = Ink.Danger,
)

private fun typography(f: FontFamily): Typography {
    // Every text style takes its direction from its own content, so a Hebrew
    // subject sits right-aligned in an English UI without anyone asking.
    fun s(size: Int, weight: FontWeight, line: Int, track: Double = 0.0) = TextStyle(
        fontFamily = f, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = track.sp, textDirection = TextDirection.Content,
    )
    return Typography(
        displaySmall = s(30, FontWeight.Bold, 36, -0.4),
        headlineSmall = s(23, FontWeight.Bold, 30, -0.2),
        titleLarge = s(19, FontWeight.Bold, 25),
        titleMedium = s(16, FontWeight.Medium, 22),
        titleSmall = s(14, FontWeight.Medium, 20),
        bodyLarge = s(16, FontWeight.Normal, 25),
        bodyMedium = s(14, FontWeight.Normal, 20),
        bodySmall = s(12, FontWeight.Normal, 16),
        labelLarge = s(14, FontWeight.Medium, 20, 0.1),
        labelMedium = s(12, FontWeight.Medium, 16, 0.3),
        labelSmall = s(11, FontWeight.Medium, 14, 0.8),
    )
}

@Composable
fun AppTheme(fonts: FontFamily, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = typography(fonts), content = content)
}
