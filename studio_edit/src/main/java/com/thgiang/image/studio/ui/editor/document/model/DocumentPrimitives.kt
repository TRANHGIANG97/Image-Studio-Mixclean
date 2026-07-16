package com.thgiang.image.studio.ui.editor.document.model

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.ShapeElevationStyle
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import java.io.Serializable

/** Horizontal text alignment (semantic). */
enum class DocTextAlign : Serializable {
    LEFT, CENTER, RIGHT;

    companion object {
        fun fromLegacy(value: String?): DocTextAlign = when (value?.lowercase()) {
            "left", "start" -> LEFT
            "right", "end" -> RIGHT
            else -> CENTER
        }
    }

    fun toLegacy(): String = when (this) {
        LEFT -> "left"
        CENTER -> "center"
        RIGHT -> "right"
    }
}

enum class DocTextTransform : Serializable {
    NONE, UPPERCASE, LOWERCASE, CAPITALIZE;

    companion object {
        fun fromLegacy(value: String?): DocTextTransform = when (value?.lowercase()) {
            "uppercase" -> UPPERCASE
            "lowercase" -> LOWERCASE
            "capitalize" -> CAPITALIZE
            else -> NONE
        }
    }

    fun toLegacy(): String? = when (this) {
        NONE -> null
        UPPERCASE -> "uppercase"
        LOWERCASE -> "lowercase"
        CAPITALIZE -> "capitalize"
    }
}

data class EdgeInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) : Serializable {
    companion object {
        val ZERO = EdgeInsets()
        /** CARD / shape-with-text default padding (matches ShapeTextBoundsResolver). */
        val SHAPE_TEXT = EdgeInsets(left = 12f, top = 6f, right = 12f, bottom = 6f)
    }
}

/**
 * Single text run (Phase A: one run covers whole string).
 * Multi-run rich text can extend later without changing node shape.
 */
data class TextRun(
    val text: String,
    val fontFamily: String? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textSizeSp: Float = 16f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val colorGradient: CloudGradient? = null,
    val underline: Boolean = false,
    val linethrough: Boolean = false,
) : Serializable

data class TextContent(
    val text: String = "",
    val runs: List<TextRun> = listOf(TextRun(text = "")),
    val textAlign: DocTextAlign = DocTextAlign.CENTER,
    val textTransform: DocTextTransform = DocTextTransform.NONE,
    val lineHeight: Float? = null,
    val charSpacing: Float = 0f,
) : Serializable {
    val primaryRun: TextRun get() = runs.firstOrNull() ?: TextRun(text = text)

    companion object {
        fun fromPlain(
            text: String,
            textSizeSp: Float = 16f,
            colorArgb: Int = 0xFFFFFFFF.toInt(),
            fontFamily: String? = null,
            fontWeight: String? = null,
            fontStyle: String? = null,
            textAlign: DocTextAlign = DocTextAlign.CENTER,
            textTransform: DocTextTransform = DocTextTransform.NONE,
            underline: Boolean = false,
            linethrough: Boolean = false,
            lineHeight: Float? = null,
            charSpacing: Float = 0f,
            colorGradient: CloudGradient? = null,
        ): TextContent {
            val run = TextRun(
                text = text,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textSizeSp = textSizeSp,
                colorArgb = colorArgb,
                colorGradient = colorGradient,
                underline = underline,
                linethrough = linethrough,
            )
            return TextContent(
                text = text,
                runs = listOf(run),
                textAlign = textAlign,
                textTransform = textTransform,
                lineHeight = lineHeight,
                charSpacing = charSpacing,
            )
        }
    }
}

enum class WidthConstraint : Serializable { Fixed, Hug }
enum class HeightConstraint : Serializable { Fixed, Hug }

data class NodeTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flippedH: Boolean = false,
    val flippedV: Boolean = false,
) : Serializable {
    val offset: Offset get() = Offset(offsetX, offsetY)

    fun toViewport(): EditorViewport = EditorViewport(
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        rotation = rotation,
        flippedH = flippedH,
        flippedV = flippedV,
    )

    companion object {
        fun fromViewport(v: EditorViewport): NodeTransform = NodeTransform(
            offsetX = v.offsetX,
            offsetY = v.offsetY,
            scale = v.scale,
            rotation = v.rotation,
            flippedH = v.flippedH,
            flippedV = v.flippedV,
        )
    }
}

data class LayoutConstraints(
    val width: WidthConstraint = WidthConstraint.Fixed,
    val height: HeightConstraint = HeightConstraint.Hug,
    val boxWidthPx: Float = 240f,
    val boxHeightPx: Float = 100f,
    val padding: EdgeInsets = EdgeInsets.ZERO,
    val transform: NodeTransform = NodeTransform(),
) : Serializable

sealed class Fill : Serializable {
    data class Solid(val argb: Int) : Fill()
    data class Gradient(val gradient: CloudGradient, val fallbackArgb: Int = 0xFFFFFFFF.toInt()) : Fill()
}

data class StrokeSpec(
    val colorArgb: Int,
    val widthPx: Float,
    val dashArray: List<Float> = emptyList(),
    val dashGapPx: Float = 6f,
) : Serializable

sealed class Effect : Serializable {
    data class DropShadow(
        val intensity: Float = 0.3f,
        val angle: Float = 45f,
        val distance: Float = 12f,
        val colorArgb: Int = 0xFF000000.toInt(),
        val blurPx: Float? = null,
    ) : Effect()

    data class Elevation3D(
        val intensity: Float = 0f,
        val style: ShapeElevationStyle = ShapeElevationStyle.RAISED,
        val depthSizePx: Float? = null,
        val depthColorArgb: Int? = null,
        val extrusionAngle: Float = 225f,
        val softBlurPx: Float? = null,
    ) : Effect()
}

data class StyleBag(
    val fills: List<Fill> = emptyList(),
    val strokes: List<StrokeSpec> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val textForm: TextFormEffect = TextFormEffect(),
    val blendMode: String? = null,
    /** Whole-node opacity (not fill alpha). */
    val opacity: Float = 1f,
) : Serializable {
    val primaryFill: Fill? get() = fills.firstOrNull()
    val dropShadow: Effect.DropShadow? get() = effects.filterIsInstance<Effect.DropShadow>().firstOrNull()
    val elevation: Effect.Elevation3D? get() = effects.filterIsInstance<Effect.Elevation3D>().firstOrNull()
}

data class FrameGeometry(
    val shapeType: ShapeType = ShapeType.CARD,
    val cornerRadiusX: Float? = null,
    val cornerRadiusY: Float? = null,
    val pathData: String? = null,
    val polygonPoints: List<Float> = emptyList(),
) : Serializable
