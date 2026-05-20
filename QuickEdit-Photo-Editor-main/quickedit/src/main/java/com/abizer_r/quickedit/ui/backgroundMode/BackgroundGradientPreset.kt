package com.abizer_r.quickedit.ui.backgroundMode

enum class GradientDirection {
    TOP_LEFT_TO_BOTTOM_RIGHT,
    BOTTOM_LEFT_TO_TOP_RIGHT
}

data class BackgroundGradientPreset(
    val id: String,
    val title: String,
    val colors: List<Int>,
    val direction: GradientDirection = GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT
)

object BackgroundGradientPresets {
    val modernPresets: List<BackgroundGradientPreset> = listOf(
        BackgroundGradientPreset(
            id = "aurora_mist",
            title = "Aurora Mist",
            colors = listOf(0xFF00C6FF.toInt(), 0xFF0072FF.toInt(), 0xFF6C63FF.toInt())
        ),
        BackgroundGradientPreset(
            id = "neon_bloom",
            title = "Neon Bloom",
            colors = listOf(0xFFFC466B.toInt(), 0xFF3F5EFB.toInt(), 0xFF00DBDE.toInt()),
            direction = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BackgroundGradientPreset(
            id = "sunset_pulse",
            title = "Sunset Pulse",
            colors = listOf(0xFFFF512F.toInt(), 0xFFF09819.toInt(), 0xFFFF8A5B.toInt())
        ),
        BackgroundGradientPreset(
            id = "velvet_sky",
            title = "Velvet Sky",
            colors = listOf(0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt(), 0xFFB06AB3.toInt()),
            direction = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BackgroundGradientPreset(
            id = "ocean_drive",
            title = "Ocean Drive",
            colors = listOf(0xFF2193B0.toInt(), 0xFF6DD5ED.toInt(), 0xFFB2FEFA.toInt())
        ),
        BackgroundGradientPreset(
            id = "peach_cloud",
            title = "Peach Cloud",
            colors = listOf(0xFFFF9A9E.toInt(), 0xFFFAD0C4.toInt(), 0xFFFBC2EB.toInt()),
            direction = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BackgroundGradientPreset(
            id = "mint_circuit",
            title = "Mint Circuit",
            colors = listOf(0xFF00F260.toInt(), 0xFF0575E6.toInt(), 0xFF43E97B.toInt())
        ),
        BackgroundGradientPreset(
            id = "midnight_fade",
            title = "Midnight Fade",
            colors = listOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()),
            direction = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BackgroundGradientPreset(
            id = "rose_quartz",
            title = "Rose Quartz",
            colors = listOf(0xFFF953C6.toInt(), 0xFFB91D73.toInt(), 0xFFFAD0C4.toInt())
        ),
        BackgroundGradientPreset(
            id = "lime_flash",
            title = "Lime Flash",
            colors = listOf(0xFFA8FF78.toInt(), 0xFF78FFD6.toInt(), 0xFFF5F7FA.toInt()),
            direction = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        BackgroundGradientPreset(
            id = "cosmic_lavender",
            title = "Cosmic Lavender",
            colors = listOf(0xFF7F00FF.toInt(), 0xFFE100FF.toInt(), 0xFFF8B195.toInt())
        ),
        BackgroundGradientPreset(
            id = "ember_glass",
            title = "Ember Glass",
            colors = listOf(0xFFFF9966.toInt(), 0xFFFF5E62.toInt(), 0xFFFFD194.toInt()),
            direction = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        )
    )
}
