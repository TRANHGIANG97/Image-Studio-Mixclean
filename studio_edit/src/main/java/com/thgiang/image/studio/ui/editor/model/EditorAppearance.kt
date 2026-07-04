package com.thgiang.image.studio.ui.editor.model

data class EditorAppearance(
    val shadowIntensity: Float = 0.3f,
    val alpha: Float = 1f,
    val shadowAngle: Float = 45f,
    val shadowDistance: Float = 12f,
    val shadowColorArgb: Int = 0xFF000000.toInt(),
    /** When set, overrides [shadowBlurRadiusFromIntensity] (from admin_web shadowBlur). */
    val shadowBlur: Float? = null,
    /** Normalized depth (0 = flat). Maps to up to 60px extrusion at render time. */
    val elevationIntensity: Float = 0f,
    val elevationStyle: ShapeElevationStyle = ShapeElevationStyle.RAISED,
    /** Explicit depth in template px (Word "Depth Size"). Overrides intensity mapping when set. */
    val depthSizePx: Float? = null,
    /** Color of extruded side faces (Word "Depth" color). */
    val depthColorArgb: Int? = null,
    /** Extrusion / lighting direction in degrees (Word 3-D rotation lighting). */
    val extrusionAngle: Float = 225f,
    /** Shape tab → [ElevationTarget.SHAPE]; Label text tab → [ElevationTarget.TEXT]. */
    val elevationTarget: ElevationTarget = ElevationTarget.SHAPE,
) : java.io.Serializable {
    init {
        require(shadowIntensity in 0f..1f) { "Shadow intensity must be in 0..1" }
        require(alpha in 0f..1f) { "Alpha must be in 0..1" }
        require(elevationIntensity in 0f..1f) { "Elevation intensity must be in 0..1" }
        depthSizePx?.let { require(it in 0f..MAX_SHAPE_DEPTH_PX) { "Depth size out of range" } }
    }

    companion object {
        const val MAX_SHAPE_DEPTH_PX: Float = 60f
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

/** Explicit blur for 3-D depth shadow (tab Độ mờ bóng). Null = no soft depth shadow. */
fun EditorAppearance.depthShadowBlurPx(scale: Float = 1f): Float? {
    val blur = shadowBlur ?: return null
    return blur.coerceAtLeast(0f) * scale.coerceAtLeast(0.01f)
}

fun EditorAppearance.appliesShapeElevation(): Boolean =
    elevationTarget == ElevationTarget.SHAPE

fun EditorAppearance.appliesTextElevation(): Boolean =
    elevationTarget == ElevationTarget.TEXT

fun shadowOffset(angle: Float, distance: Float): Pair<Float, Float> {
    val rad = Math.toRadians(angle.toDouble())
    return (distance * kotlin.math.cos(rad)).toFloat() to (distance * kotlin.math.sin(rad)).toFloat()
}
