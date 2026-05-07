// image/core/background/BackgroundGenerator.kt
package com.thgiang.image.core.background

import android.graphics.Bitmap
import com.thgiang.image.core.model.PresetStyle

/**
 * Giao diện sinh ảnh nền dùng chung cho toàn app.
 * Thay thế PresetBackgroundComposer và BackgroundLayerComposer.
 */
interface BackgroundGenerator {
    /**
     * Tạo nền theo preset style.
     */
    suspend fun generatePreset(style: PresetStyle, width: Int, height: Int): Bitmap

    /**
     * Tạo nền từ một màu đơn.
     */
    fun generateColor(color: Int, width: Int, height: Int): Bitmap

    /**
     * Tạo nền gradient (tuyến tính) với danh sách màu và hướng.
     */
    fun generateGradient(colors: List<Int>, direction: GradientDirection, width: Int, height: Int): Bitmap

    /**
     * Tạo nền từ ảnh (Uri) – scale về đúng kích thước.
     */
    suspend fun generateFromUri(uri: android.net.Uri, targetWidth: Int, targetHeight: Int, context: android.content.Context): Bitmap
}

enum class GradientDirection {
    TOP_TO_BOTTOM,
    LEFT_TO_RIGHT,
    DIAGONAL,
    TOPLEFT_TO_BOTTOMRIGHT
}