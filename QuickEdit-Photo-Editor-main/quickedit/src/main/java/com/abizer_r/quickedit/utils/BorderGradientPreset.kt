package com.abizer_r.quickedit.utils

enum class BorderGradientDirection {
    TOP_LEFT_TO_BOTTOM_RIGHT,
    BOTTOM_LEFT_TO_TOP_RIGHT
}

data class BorderGradientPreset(
    val id: String,
    val title: String,
    val colors: IntArray,
    val direction: BorderGradientDirection = BorderGradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT
)

object BorderGradientPresets {
    val modernPresets: List<BorderGradientPreset> = listOf(
        BorderGradientPreset(
            id = "aurora_mist",
            title = "Aurora Mist",
            colors = intArrayOf(0xFF00C6FF.toInt(), 0xFF0072FF.toInt(), 0xFF6C63FF.toInt())
        ),
        BorderGradientPreset(
            id = "neon_bloom",
            title = "Neon Bloom",
            colors = intArrayOf(0xFFFC466B.toInt(), 0xFF3F5EFB.toInt(), 0xFF00DBDE.toInt()),
            direction = BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BorderGradientPreset(
            id = "sunset_pulse",
            title = "Sunset Pulse",
            colors = intArrayOf(0xFFFF512F.toInt(), 0xFFF09819.toInt(), 0xFFFF8A5B.toInt())
        ),
        BorderGradientPreset(
            id = "velvet_sky",
            title = "Velvet Sky",
            colors = intArrayOf(0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt(), 0xFFB06AB3.toInt()),
            direction = BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BorderGradientPreset(
            id = "ocean_drive",
            title = "Ocean Drive",
            colors = intArrayOf(0xFF2193B0.toInt(), 0xFF6DD5ED.toInt(), 0xFFB2FEFA.toInt())
        ),
        BorderGradientPreset(
            id = "peach_cloud",
            title = "Peach Cloud",
            colors = intArrayOf(0xFFFF9A9E.toInt(), 0xFFFAD0C4.toInt(), 0xFFFBC2EB.toInt()),
            direction = BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BorderGradientPreset(
            id = "midnight_fade",
            title = "Midnight Fade",
            colors = intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()),
            direction = BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BorderGradientPreset(
            id = "ember_glass",
            title = "Ember Glass",
            colors = intArrayOf(0xFFFF9966.toInt(), 0xFFFF5E62.toInt(), 0xFFFFD194.toInt())
        )
    )
}
