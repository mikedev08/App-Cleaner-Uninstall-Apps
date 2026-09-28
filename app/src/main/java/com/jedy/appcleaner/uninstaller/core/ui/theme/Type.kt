package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jedy.appcleaner.uninstaller.R

/**
 * Plus Jakarta Sans (OFL), bundled as one variable font. Scripts it lacks (Arabic, Hebrew,
 * Devanagari, CJK) fall back to the system font per glyph, so every language still renders.
 */
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Jakarta = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 56, 60, -1.5),
    displayMedium = style(FontWeight.ExtraBold, 44, 50, -1.0),
    displaySmall = style(FontWeight.Bold, 34, 40, -0.6),
    headlineLarge = style(FontWeight.Bold, 30, 36, -0.5),
    headlineMedium = style(FontWeight.Bold, 26, 32, -0.4),
    headlineSmall = style(FontWeight.Bold, 22, 28, -0.2),
    titleLarge = style(FontWeight.Bold, 20, 26, -0.2),
    titleMedium = style(FontWeight.SemiBold, 16, 22),
    titleSmall = style(FontWeight.SemiBold, 14, 20),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Medium, 12, 16),
    labelLarge = style(FontWeight.Bold, 16, 20),
    labelMedium = style(FontWeight.SemiBold, 13, 18),
    labelSmall = style(FontWeight.Bold, 11, 14, 0.4),
)

/**
 * Arabic and Hebrew get no tracking: negative letter spacing breaks Arabic joining and throws off
 * line-break measurement, so a one-word title like "الإعدادات" wrapped mid-word.
 */
val RtlTypography: Typography = with(AppTypography) {
    fun TextStyle.untracked() = copy(letterSpacing = 0.sp)
    Typography(
        displayLarge = displayLarge.untracked(), displayMedium = displayMedium.untracked(),
        displaySmall = displaySmall.untracked(), headlineLarge = headlineLarge.untracked(),
        headlineMedium = headlineMedium.untracked(), headlineSmall = headlineSmall.untracked(),
        titleLarge = titleLarge.untracked(), titleMedium = titleMedium.untracked(),
        titleSmall = titleSmall.untracked(), bodyLarge = bodyLarge.untracked(),
        bodyMedium = bodyMedium.untracked(), bodySmall = bodySmall.untracked(),
        labelLarge = labelLarge.untracked(), labelMedium = labelMedium.untracked(),
        labelSmall = labelSmall.untracked(),
    )
}
