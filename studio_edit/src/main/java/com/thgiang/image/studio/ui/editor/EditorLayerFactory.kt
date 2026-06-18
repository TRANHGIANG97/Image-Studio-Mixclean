package com.thgiang.image.studio.ui.editor

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorLayerFactory @Inject constructor() {
    fun createShapeTextLayer(templateWidth: Float, shapeType: ShapeType): EditorLayer {
        val defaultW = (if (templateWidth > 0f) templateWidth * 0.40f else 240f).coerceIn(120f, 600f)
        val defaultH = (defaultW * 0.42f).coerceIn(60f, 220f)
        val fillColor = 0xFFE53935.toInt()
        val isLine = shapeType == ShapeType.LINE

        return EditorLayer(
            type = LayerType.SHAPE_TEXT,
            text = "Label",
            textColorArgb = 0xFFFFFFFF.toInt(),
            textSizeSp = 16f,
            shapeType = shapeType,
            shapeColorArgb = if (isLine) 0x00FFFFFF else fillColor,
            shapeWidthPx = defaultW,
            shapeHeightPx = defaultH,
            strokeColorArgb = if (isLine) fillColor else null,
            strokeWidthPx = if (isLine) 6f else 0f,
            viewport = EditorViewport(scale = 1f),
            appearance = EditorAppearance(shadowIntensity = 0f, alpha = 1f),
        )
    }

    fun createTextLayer(templateWidth: Float): EditorLayer {
        val defaultW = (if (templateWidth > 0f) templateWidth * 0.58f else 360f).coerceIn(180f, 760f)
        val defaultH = (defaultW * 0.24f).coerceIn(72f, 220f)

        return EditorLayer(
            type = LayerType.SHAPE_TEXT,
            text = "Nhập chữ...",
            textColorArgb = 0xFF5B21B6.toInt(),
            textSizeSp = 28f,
            shapeType = ShapeType.CARD,
            shapeColorArgb = 0x00FFFFFF,
            shapeWidthPx = defaultW,
            shapeHeightPx = defaultH,
            fontFamily = "sans-serif",
            viewport = EditorViewport(scale = 1f),
            appearance = EditorAppearance(shadowIntensity = 0f, alpha = 1f),
        )
    }

    fun createStickerLayer(
        stickerPath: String,
        stickerWidth: Int,
        stickerHeight: Int,
        initialScale: Float,
    ): EditorLayer = EditorLayer(
        product = EditorProduct(
            originalUriString = stickerPath,
            foregroundUriString = stickerPath,
            isBackgroundRemoved = true,
            baseWidth = stickerWidth,
            baseHeight = stickerHeight,
            processing = false,
            isSample = false,
        ),
        viewport = EditorViewport(scale = initialScale),
        appearance = EditorAppearance(
            shadowIntensity = 0f,
            alpha = 1f,
            shadowAngle = 45f,
            shadowDistance = 12f,
            shadowColorArgb = 0xFF000000.toInt(),
        ),
        cropRatio = CropRatio.ORIGINAL,
    )
}
