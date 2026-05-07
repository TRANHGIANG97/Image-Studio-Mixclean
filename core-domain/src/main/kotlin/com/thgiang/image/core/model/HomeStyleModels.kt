package com.thgiang.image.core.model

enum class BorderPresetStyle {
    SOLID,
    WHITE,
    GRADIENT,
    NEON
}

sealed interface BorderRenderMode {
    data class Solid(val colorArgb: Int) : BorderRenderMode
    data class Gradient(val colors: List<Int>) : BorderRenderMode
}

sealed interface HomeStyleRequest {
    data class Background(val style: PresetStyle) : HomeStyleRequest
    data class Border(
        val style: BorderPresetStyle,
        val renderMode: BorderRenderMode,
        val borderWidthPx: Int
    ) : HomeStyleRequest
}
