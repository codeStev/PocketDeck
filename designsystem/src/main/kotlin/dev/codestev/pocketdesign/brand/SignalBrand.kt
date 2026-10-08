package dev.codestev.pocketdesign.brand

import androidx.compose.ui.graphics.Color
import dev.codestev.pocketdesign.theme.PdBrand
import dev.codestev.pocketdesign.theme.PdColors
import dev.codestev.pocketdesign.theme.PdTint

/**
 * The reference brand: neutral cool surfaces and one warm orange-red accent, with ten categorical tints named by hue
 * (`green`, `orange`, `blue`, `yellow`, `purple`, `brown`, `slate`, `steel`, `teal`, `gray`). Every text pair holds
 * 4.5:1 and every graphic 3:1 in both themes (checked by a unit test).
 */
object SignalBrand {
    const val GREEN = "green"
    const val ORANGE = "orange"
    const val BLUE = "blue"
    const val YELLOW = "yellow"
    const val PURPLE = "purple"
    const val BROWN = "brown"
    const val SLATE = "slate"
    const val STEEL = "steel"
    const val TEAL = "teal"
    const val GRAY = "gray"

    private fun tint(
        strong: Long,
        soft: Long,
    ) = PdTint(Color(strong), Color(soft))

    private val lightTints =
        mapOf(
            GREEN to tint(0xFF2E8B3A, 0xFFE4F3E6),
            ORANGE to tint(0xFFD9531E, 0xFFFDE8DF),
            BLUE to tint(0xFF1F78C8, 0xFFE0EEFB),
            YELLOW to tint(0xFFA67C00, 0xFFFBF1CC),
            PURPLE to tint(0xFF9B3FB0, 0xFFF4E4F8),
            BROWN to tint(0xFFA0522D, 0xFFF5E6DC),
            SLATE to tint(0xFF4A4E69, 0xFFE6E7EE),
            STEEL to tint(0xFF5F7185, 0xFFE4E9EF),
            TEAL to tint(0xFF0E7C86, 0xFFD9F0F2),
            GRAY to tint(0xFF6F7480, 0xFFECEEF2),
        )

    private val darkTints =
        mapOf(
            GREEN to tint(0xFF62C46E, 0xFF16301A),
            ORANGE to tint(0xFFFF8A5C, 0xFF3A1D12),
            BLUE to tint(0xFF6DB6F5, 0xFF10263A),
            YELLOW to tint(0xFFF2C230, 0xFF352B08),
            PURPLE to tint(0xFFD68BE8, 0xFF2E1536),
            BROWN to tint(0xFFD9936A, 0xFF33201A),
            SLATE to tint(0xFFA3A8C3, 0xFF1F2130),
            STEEL to tint(0xFFA9B8C8, 0xFF1C242D),
            TEAL to tint(0xFF4FCDD6, 0xFF0F2B2E),
            GRAY to tint(0xFFB3B8C2, 0xFF22252B),
        )

    val light =
        PdColors(
            isLight = true,
            surfacePage = Color(0xFFF3F5F9),
            surfaceCard = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFFFFFFF),
            surfaceSunken = Color(0xFFE8ECF3),
            line = Color(0xFFD5DBE5),
            lineStrong = Color(0xFF727D8F),
            ink = Color(0xFF131822),
            inkMuted = Color(0xFF4F5A6B),
            accent = Color(0xFFCC3518),
            onAccent = Color(0xFFFFFFFF),
            accentSoft = Color(0xFFFDE9E4),
            onAccentSoft = Color(0xFF8C2410),
            focus = Color(0xFF1456D6),
            success = Color(0xFF1B7A43),
            successSoft = Color(0xFFE3F4EA),
            warning = Color(0xFF8A5A00),
            warningSoft = Color(0xFFFFF1D6),
            danger = Color(0xFFB3261E),
            dangerSoft = Color(0xFFFBE7E5),
            info = Color(0xFF0B6BA8),
            infoSoft = Color(0xFFE1F0FA),
            tints = lightTints,
        )

    val dark =
        PdColors(
            isLight = false,
            surfacePage = Color(0xFF0D1015),
            surfaceCard = Color(0xFF171B22),
            surfaceRaised = Color(0xFF1F2530),
            surfaceSunken = Color(0xFF0A0C10),
            line = Color(0xFF2A313C),
            lineStrong = Color(0xFF8995A8),
            ink = Color(0xFFF1F4F9),
            inkMuted = Color(0xFFA9B3C2),
            accent = Color(0xFFFF7455),
            onAccent = Color(0xFF1A0B07),
            accentSoft = Color(0xFF3A1B14),
            onAccentSoft = Color(0xFFFFB5A3),
            focus = Color(0xFF7FB1FF),
            success = Color(0xFF5CD08A),
            successSoft = Color(0xFF14301F),
            warning = Color(0xFFF3B442),
            warningSoft = Color(0xFF35280B),
            danger = Color(0xFFFF8A80),
            dangerSoft = Color(0xFF3A1715),
            info = Color(0xFF6CC1F2),
            infoSoft = Color(0xFF112A3A),
            tints = darkTints,
        )

    val brand = PdBrand(name = "Signal", light = light, dark = dark)
}
