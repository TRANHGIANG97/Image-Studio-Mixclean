package com.thgiang.image.core.data.backgroundremove

/**
 * Subject confidence per pixel, row-major order, length [width * height], values in 0f..1f.
 */
class PortraitConfidenceMask(
    val width: Int,
    val height: Int,
    val values: FloatArray
)
