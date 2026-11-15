package eotaek.juyulow.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/** Pleos Foundations/Typography. See docs/DESIGN.md for font and weight assumptions. */
@Immutable
class PleosTypography(
    private val fontFamily: FontFamily = FontFamily.SansSerif,
) {
    val headlineLarge = textStyle(60, 80, FontWeight.Bold)
    val headlineMedium = textStyle(56, 72, FontWeight.Bold)
    val headlineSmall = textStyle(48, 64, FontWeight.Bold)
    val headlineLargeExtraBold = headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
    val headlineMediumExtraBold = headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
    val headlineSmallExtraBold = headlineSmall.copy(fontWeight = FontWeight.ExtraBold)

    val titleLarge = textStyle(40, 52, FontWeight.Normal)
    val titleMedium = textStyle(36, 44, FontWeight.Normal)
    val titleSmall = textStyle(32, 40, FontWeight.Normal)
    val titleLargeBold = titleLarge.copy(fontWeight = FontWeight.Bold)
    val titleMediumBold = titleMedium.copy(fontWeight = FontWeight.Bold)
    val titleSmallBold = titleSmall.copy(fontWeight = FontWeight.Bold)
    val titleLargeExtraBold = titleLarge.copy(fontWeight = FontWeight.ExtraBold)
    val titleMediumExtraBold = titleMedium.copy(fontWeight = FontWeight.ExtraBold)
    val titleSmallExtraBold = titleSmall.copy(fontWeight = FontWeight.ExtraBold)

    val bodyLarge = textStyle(30, 38, FontWeight.Normal)
    val bodyMedium = textStyle(28, 36, FontWeight.Normal)
    val bodySmall = textStyle(26, 34, FontWeight.Normal)

    // The guide does not specify a numeric weight for Strong; use SemiBold (600).
    val bodyLargeStrong = bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val bodyMediumStrong = bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val bodySmallStrong = bodySmall.copy(fontWeight = FontWeight.SemiBold)

    val labelMedium = textStyle(24, 28, FontWeight.Normal)
    val labelSmall = textStyle(20, 24, FontWeight.Normal)

    private fun textStyle(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp,
        localeList = LocaleList("ko-KR"),
        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
        hyphens = Hyphens.None,
    )
}

internal val DefaultPleosTypography = PleosTypography()

/** Material-only roles reuse the nearest Pleos role; they do not introduce another scale. */
internal fun PleosTypography.toMaterialTypography() = Typography(
    displayLarge = headlineLarge,
    displayMedium = headlineMedium,
    displaySmall = headlineSmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelMedium,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
