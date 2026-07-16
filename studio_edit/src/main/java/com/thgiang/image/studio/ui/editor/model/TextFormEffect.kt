package com.thgiang.image.studio.ui.editor.model

enum class TextFormCategory {
    NONE,
    FOLLOW_PATH,
    WARP,
}

enum class TextFormPreset(val id: String, val category: TextFormCategory) {
    NONE("none", TextFormCategory.NONE),

    PATH_WAVE("path_wave", TextFormCategory.FOLLOW_PATH),
    PATH_ARC_UP("path_arc_up", TextFormCategory.FOLLOW_PATH),
    PATH_ARC_DOWN("path_arc_down", TextFormCategory.FOLLOW_PATH),
    PATH_CIRCLE("path_circle", TextFormCategory.FOLLOW_PATH),

    WARP_ARCH_UP("warp_arch_up", TextFormCategory.WARP),
    WARP_ARCH_DOWN("warp_arch_down", TextFormCategory.WARP),
    WARP_BULGE("warp_bulge", TextFormCategory.WARP),
    WARP_WAVE("warp_wave", TextFormCategory.WARP),
    WARP_FLAG("warp_flag", TextFormCategory.WARP),
    WARP_RISE("warp_rise", TextFormCategory.WARP),
    WARP_FALL("warp_fall", TextFormCategory.WARP),
    WARP_CHEVRON_UP("warp_chevron_up", TextFormCategory.WARP),
    WARP_CHEVRON_DOWN("warp_chevron_down", TextFormCategory.WARP),
    ;

    companion object {
        fun fromId(id: String?): TextFormPreset {
            if (id.equals("path_ring", ignoreCase = true)) return NONE
            if (id.equals("warp_inflate", ignoreCase = true)) return NONE
            if (id.equals("warp_deflate", ignoreCase = true)) return NONE
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
        }
    }
}

data class TextFormEffect(
    val category: TextFormCategory = TextFormCategory.NONE,
    val preset: TextFormPreset = TextFormPreset.NONE,
    val amount: Float = 0.5f,
    val reversePath: Boolean = false,
) : java.io.Serializable {
    val isActive: Boolean get() = preset != TextFormPreset.NONE

    fun normalizedAmount(): Float = amount.coerceIn(0f, MAX_AMOUNT)

    companion object {
        /** Slider/render cap: 3× former 0..1 maximum (≈300% intensity). */
        const val MAX_AMOUNT = 3f
    }
}

fun TextFormEffect.withPreset(preset: TextFormPreset): TextFormEffect = when (preset) {
    TextFormPreset.NONE -> TextFormEffect()
    else -> copy(
        category = preset.category,
        preset = preset,
        amount = if (amount <= 0.01f) 0.5f else amount,
    )
}
