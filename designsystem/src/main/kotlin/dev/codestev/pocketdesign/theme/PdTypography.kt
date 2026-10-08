package dev.codestev.pocketdesign.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.R

/** Fredoka (rounded, friendly) for titles and numerals; bundled, so it works offline. SIL Open Font License. */
private val DisplayFamily =
    FontFamily(
        listOf(500, 600, 700).map { weight ->
            Font(
                R.font.fredoka,
                FontWeight(weight),
                variationSettings =
                    FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.width(WIDTH_NORMAL),
                    ),
            )
        },
    )

/** Nunito for everything that is read: titles of rows, body text, labels. SIL Open Font License. */
private val BodyFamily =
    FontFamily(
        listOf(500, 600, 700, 800).map { weight ->
            Font(
                R.font.nunito,
                FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

private const val WIDTH_NORMAL = 100f
private const val TABULAR_NUMERALS = "tnum"

/** The type scale. Use only these styles; numbers that line up in columns use [numeric]. */
@Immutable
class PdTypography(
    /** Hero numbers such as an owned percentage. */
    val displayXl: TextStyle =
        TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
    /** Screen titles and section headers. */
    val titleLg: TextStyle =
        TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    /** Card, dialog and deck titles. */
    val titleMd: TextStyle =
        TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    /** Row titles, button labels, tab labels. */
    val titleSm: TextStyle =
        TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp),
    /** Intro paragraphs. */
    val bodyLg: TextStyle =
        TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 26.sp),
    /** Default text. */
    val body: TextStyle =
        TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    /** Captions, secondary values, helper text. */
    val bodySm: TextStyle =
        TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    /** Chips, badges, table headers. */
    val label: TextStyle =
        TextStyle(
            fontFamily = BodyFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.04.em,
        ),
) {
    /** [titleMd] with tabular numerals, for counts and percentages that must not jump when digits change. */
    val numeric: TextStyle get() = titleMd.copy(fontFeatureSettings = TABULAR_NUMERALS)
}
