package com.thgiang.image.studio.ui.editor.model

data class EditorAppearance(
    val shadowIntensity: Float = 0.3f,
    val alpha: Float = 1f,
    val shadowAngle: Float = 45f,
    val shadowDistance: Float = 12f,
    val shadowColorArgb: Int = 0xFF000000.toInt(),
    /** When set, overrides [shadowBlurRadiusFromIntensity] (from admin_web shadowBlur). */
    val shadowBlur: Float? = null,
) : java.io.Serializable {
    init {
        require(shadowIntensity in 0f..1f) { "Shadow intensity must be in 0..1" }
        require(alpha in 0f..1f) { "Alpha must be in 0..1" }
    }
}

fun shadowOpacityFromIntensity(intensity: Float): Float {
    return (0.10f + (intensity.coerceIn(0f, 1f) * 0.80f)).coerceIn(0.10f, 0.90f)
}

fun shadowBlurRadiusFromIntensity(intensity: Float): Float {
    return (18f - (intensity.coerceIn(0f, 1f) * 12f)).coerceAtLeast(4f)
}

fun EditorAppearance.resolvedShadowBlurRadius(): Float =
    shadowBlur?.coerceAtLeast(0f) ?: shadowBlurRadiusFromIntensity(shadowIntensity)

fun shadowOffset(angle: Float, distance: Float): Pair<Float, Float> {
    val rad = Math.toRadians(angle.toDouble())
    return (distance * kotlin.math.cos(rad)).toFloat() to (distance * kotlin.math.sin(rad)).toFloat()
}
