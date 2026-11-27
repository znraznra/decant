package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.domain.ColorblindPattern
import com.example.domain.StudioThemeId

enum class BottleShapeStyle {
    ROUNDED_LAB,
    APOTHECARY,
    SLIM_NEON,
    CERAMIC_VIAL
}

data class LiquidColorSpec(
    val id: Int,
    val name: String,
    val topColor: Color,
    val bottomColor: Color,
    val symbolColor: Color,
    val pattern: ColorblindPattern,
    val gradientColors: List<Color> = listOf(topColor, bottomColor)
)

data class StudioThemeSpec(
    val id: StudioThemeId,
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val ambientAccent: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val tubeGlassFill: Color,
    val tubeStroke: Color,
    val tubeSelectedStroke: Color,
    val tubeCompletedGlow: Color,
    val bottleStyle: BottleShapeStyle,
    val liquids: List<LiquidColorSpec>
)

object StudioThemes {

    private val highContrastPatterns = listOf(
        ColorblindPattern.CIRCLE,
        ColorblindPattern.TRIANGLE,
        ColorblindPattern.DIAMOND,
        ColorblindPattern.WAVES,
        ColorblindPattern.STRIPES,
        ColorblindPattern.DOTS,
        ColorblindPattern.STAR,
        ColorblindPattern.PLUS,
        ColorblindPattern.RING,
        ColorblindPattern.HEXAGON,
        ColorblindPattern.CHEVRON,
        ColorblindPattern.GRID
    )

    private val cleanGlassLiquids = listOf(
        LiquidColorSpec(0, "Crimson", Color(0xFFFF5E6C), Color(0xFFD92B3E), Color.White, highContrastPatterns[0]),
        LiquidColorSpec(1, "Cobalt", Color(0xFF3E8CFF), Color(0xFF175BD1), Color.White, highContrastPatterns[1]),
        LiquidColorSpec(2, "Amber", Color(0xFFFFC145), Color(0xFFE58C0A), Color(0xFF2B1800), highContrastPatterns[2]),
        LiquidColorSpec(3, "Emerald", Color(0xFF28D19A), Color(0xFF0A9868), Color.White, highContrastPatterns[3]),
        LiquidColorSpec(4, "Violet", Color(0xFF9F6BFF), Color(0xFF6B32D6), Color.White, highContrastPatterns[4]),
        LiquidColorSpec(5, "Tangerine", Color(0xFFFF8847), Color(0xFFD95414), Color.White, highContrastPatterns[5]),
        LiquidColorSpec(6, "Cyan", Color(0xFF2CD7EC), Color(0xFF009DB5), Color(0xFF00242B), highContrastPatterns[6]),
        LiquidColorSpec(7, "Magenta", Color(0xFFF45BC2), Color(0xFFC1208C), Color.White, highContrastPatterns[7]),
        LiquidColorSpec(8, "Lime", Color(0xFF9DE332), Color(0xFF69AD07), Color(0xFF1A2C00), highContrastPatterns[8]),
        LiquidColorSpec(9, "Indigo", Color(0xFF5A65FF), Color(0xFF2F38BD), Color.White, highContrastPatterns[9]),
        LiquidColorSpec(10, "Cocoa", Color(0xFFA86F4D), Color(0xFF734124), Color.White, highContrastPatterns[10]),
        LiquidColorSpec(11, "Slate", Color(0xFF7187A8), Color(0xFF475B78), Color.White, highContrastPatterns[11])
    )

    private val cozyPastelLiquids = listOf(
        LiquidColorSpec(0, "Blush", Color(0xFFF38B9B), Color(0xFFD15B6E), Color.White, highContrastPatterns[0]),
        LiquidColorSpec(1, "Periwinkle", Color(0xFF7CA7F9), Color(0xFF4C7AD4), Color.White, highContrastPatterns[1]),
        LiquidColorSpec(2, "Buttercup", Color(0xFFFBD16C), Color(0xFFD9A32A), Color(0xFF332200), highContrastPatterns[2]),
        LiquidColorSpec(3, "Mint", Color(0xFF6CD9B4), Color(0xFF37A681), Color(0xFF032B1D), highContrastPatterns[3]),
        LiquidColorSpec(4, "Lavender", Color(0xFFB799F7), Color(0xFF8361CC), Color.White, highContrastPatterns[4]),
        LiquidColorSpec(5, "Peach", Color(0xFFFB9D75), Color(0xFFD66A3E), Color.White, highContrastPatterns[5]),
        LiquidColorSpec(6, "Sky", Color(0xFF71D3E8), Color(0xFF379DB5), Color(0xFF02262E), highContrastPatterns[6]),
        LiquidColorSpec(7, "Berry", Color(0xFFE075BC), Color(0xFFB0438C), Color.White, highContrastPatterns[7]),
        LiquidColorSpec(8, "Pistachio", Color(0xFFB3DA6C), Color(0xFF7DA634), Color(0xFF1F2B04), highContrastPatterns[8]),
        LiquidColorSpec(9, "Denim", Color(0xFF6C80D6), Color(0xFF4255A6), Color.White, highContrastPatterns[9]),
        LiquidColorSpec(10, "Caramel", Color(0xFFC8916A), Color(0xFF965E38), Color.White, highContrastPatterns[10]),
        LiquidColorSpec(11, "Dove", Color(0xFF93A3B8), Color(0xFF64758A), Color.White, highContrastPatterns[11])
    )

    private val darkNeonLiquids = listOf(
        LiquidColorSpec(0, "Laser Red", Color(0xFFFF4D6D), Color(0xFFC9184A), Color.White, highContrastPatterns[0]),
        LiquidColorSpec(1, "Electric Blue", Color(0xFF3A86FF), Color(0xFF1652B8), Color.White, highContrastPatterns[1]),
        LiquidColorSpec(2, "Solar Gold", Color(0xFFFFBE0B), Color(0xFFD48800), Color(0xFF261700), highContrastPatterns[2]),
        LiquidColorSpec(3, "Bio Mint", Color(0xFF06D6A0), Color(0xFF00966C), Color(0xFF00261B), highContrastPatterns[3]),
        LiquidColorSpec(4, "Ultra Violet", Color(0xFFA855F7), Color(0xFF7224C4), Color.White, highContrastPatterns[4]),
        LiquidColorSpec(5, "Plasma Orange", Color(0xFFFB5607), Color(0xFFC43900), Color.White, highContrastPatterns[5]),
        LiquidColorSpec(6, "Cyber Cyan", Color(0xFF00E5FF), Color(0xFF009BB5), Color(0xFF00242B), highContrastPatterns[6]),
        LiquidColorSpec(7, "Hyper Pink", Color(0xFFFF47B8), Color(0xFFC71884), Color.White, highContrastPatterns[7]),
        LiquidColorSpec(8, "Acid Lime", Color(0xFFB2F42E), Color(0xFF79BA00), Color(0xFF192900), highContrastPatterns[8]),
        LiquidColorSpec(9, "Cobalt Pulse", Color(0xFF6366F1), Color(0xFF393BC4), Color.White, highContrastPatterns[9]),
        LiquidColorSpec(10, "Copper Glow", Color(0xFFD97D54), Color(0xFFA14923), Color.White, highContrastPatterns[10]),
        LiquidColorSpec(11, "Ice Steel", Color(0xFF94A3B8), Color(0xFF5D6D84), Color.White, highContrastPatterns[11])
    )

    private val botanicalZenLiquids = listOf(
        LiquidColorSpec(0, "Persimmon", Color(0xFFE06453), Color(0xFFB33B2B), Color.White, highContrastPatterns[0]),
        LiquidColorSpec(1, "Aegean", Color(0xFF3D7EA6), Color(0xFF1E567A), Color.White, highContrastPatterns[1]),
        LiquidColorSpec(2, "Ochre", Color(0xFFE0AB46), Color(0xFFB07B1B), Color(0xFF291A00), highContrastPatterns[2]),
        LiquidColorSpec(3, "Moss", Color(0xFF4CA87B), Color(0xFF287851), Color.White, highContrastPatterns[3]),
        LiquidColorSpec(4, "Plum", Color(0xFF9C66B3), Color(0xFF6D3C82), Color.White, highContrastPatterns[4]),
        LiquidColorSpec(5, "Terracotta", Color(0xFFDB7F48), Color(0xFFA8501F), Color.White, highContrastPatterns[5]),
        LiquidColorSpec(6, "Lagoon", Color(0xFF3EB4BC), Color(0xFF198087), Color.White, highContrastPatterns[6]),
        LiquidColorSpec(7, "Hibiscus", Color(0xFFD45D91), Color(0xFFA13063), Color.White, highContrastPatterns[7]),
        LiquidColorSpec(8, "Matcha", Color(0xFF95C447), Color(0xFF648F1F), Color(0xFF182602), highContrastPatterns[8]),
        LiquidColorSpec(9, "Indigo Dye", Color(0xFF5463AD), Color(0xFF313E82), Color.White, highContrastPatterns[9]),
        LiquidColorSpec(10, "Chestnut", Color(0xFF9A6847), Color(0xFF6B3E21), Color.White, highContrastPatterns[10]),
        LiquidColorSpec(11, "Pebble", Color(0xFF7B8C88), Color(0xFF4E5E5A), Color.White, highContrastPatterns[11])
    )

    private val cleanGlassSpec = StudioThemeSpec(
        id = StudioThemeId.CLEAN_GLASS,
        isDark = false,
        backgroundTop = Color(0xFFF3F7FF),
        backgroundBottom = Color(0xFFE3ECFB),
        ambientAccent = Color(0xFF4F8BFF),
        cardBackground = Color(0xE6FFFFFF),
        cardBorder = Color(0xFFCFE0FA),
        textPrimary = Color(0xFF13203A),
        textSecondary = Color(0xFF526384),
        accent = Color(0xFF1D63DC),
        onAccent = Color.White,
        accentSoft = Color(0xFFDCE8FF),
        tubeGlassFill = Color(0x78FFFFFF),
        tubeStroke = Color(0xFF8AA4CC),
        tubeSelectedStroke = Color(0xFF1D63DC),
        tubeCompletedGlow = Color(0xFF0E9F7F),
        bottleStyle = BottleShapeStyle.ROUNDED_LAB,
        liquids = cleanGlassLiquids
    )

    private val cozyPastelSpec = StudioThemeSpec(
        id = StudioThemeId.COZY_PASTEL,
        isDark = false,
        backgroundTop = Color(0xFFFFF7F2),
        backgroundBottom = Color(0xFFF8E9E0),
        ambientAccent = Color(0xFFF0917B),
        cardBackground = Color(0xEBFFFDFB),
        cardBorder = Color(0xFFF0D5C8),
        textPrimary = Color(0xFF332020),
        textSecondary = Color(0xFF735755),
        accent = Color(0xFFD45A6A),
        onAccent = Color.White,
        accentSoft = Color(0xFFFFE2E6),
        tubeGlassFill = Color(0x88FFF9F5),
        tubeStroke = Color(0xFFC59E96),
        tubeSelectedStroke = Color(0xFFD45A6A),
        tubeCompletedGlow = Color(0xFF37A681),
        bottleStyle = BottleShapeStyle.APOTHECARY,
        liquids = cozyPastelLiquids
    )

    private val darkNeonSpec = StudioThemeSpec(
        id = StudioThemeId.DARK_NEON,
        isDark = true,
        backgroundTop = Color(0xFF0B101D),
        backgroundBottom = Color(0xFF161E35),
        ambientAccent = Color(0xFF00E5FF),
        cardBackground = Color(0xCC172038),
        cardBorder = Color(0xFF2D3C63),
        textPrimary = Color(0xFFF0F5FF),
        textSecondary = Color(0xFF9BACD1),
        accent = Color(0xFF00D4F5),
        onAccent = Color(0xFF002129),
        accentSoft = Color(0xFF173A52),
        tubeGlassFill = Color(0x2E8EB2FF),
        tubeStroke = Color(0xFF4F6699),
        tubeSelectedStroke = Color(0xFF00E5FF),
        tubeCompletedGlow = Color(0xFF06D6A0),
        bottleStyle = BottleShapeStyle.SLIM_NEON,
        liquids = darkNeonLiquids
    )

    private val botanicalZenSpec = StudioThemeSpec(
        id = StudioThemeId.BOTANICAL_ZEN,
        isDark = false,
        backgroundTop = Color(0xFFF1F6F1),
        backgroundBottom = Color(0xFFDFEADF),
        ambientAccent = Color(0xFF4CA87B),
        cardBackground = Color(0xEBFAFCFA),
        cardBorder = Color(0xFFC2D7C3),
        textPrimary = Color(0xFF1A2B20),
        textSecondary = Color(0xFF536B5A),
        accent = Color(0xFF287851),
        onAccent = Color.White,
        accentSoft = Color(0xFFD6EEDF),
        tubeGlassFill = Color(0x82F7FBF7),
        tubeStroke = Color(0xFF839E88),
        tubeSelectedStroke = Color(0xFF287851),
        tubeCompletedGlow = Color(0xFF287851),
        bottleStyle = BottleShapeStyle.CERAMIC_VIAL,
        liquids = botanicalZenLiquids
    )

    fun get(id: StudioThemeId): StudioThemeSpec = when (id) {
        StudioThemeId.CLEAN_GLASS -> cleanGlassSpec
        StudioThemeId.COZY_PASTEL -> cozyPastelSpec
        StudioThemeId.DARK_NEON -> darkNeonSpec
        StudioThemeId.BOTANICAL_ZEN -> botanicalZenSpec
    }
}
