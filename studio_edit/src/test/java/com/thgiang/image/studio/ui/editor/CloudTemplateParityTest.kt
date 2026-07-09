package com.thgiang.image.studio.ui.editor

import com.thgiang.image.core.domain.model.template.CloudTemplateParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.thgiang.image.studio.ui.editor.mapper.CloudLayerToEditorMapper
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry

/**
 * Parity fixture mirroring admin_web template-converter export for gradient + shadow shapes.
 */
class CloudTemplateParityTest {

    @Test
    fun `parity fixture maps gradient shadow and vector shapes`() {
        val template = CloudTemplateParser.parse(
            templateId = "parity_tpl",
            categoryId = "cat_shapes",
            canvasData = JSONObject(PARITY_FIXTURE),
        )

        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)
        assertEquals(7, layers.size)

        val gradientRect = layers.first { it.id == "shape_gradient_rect" }
        assertEquals(LayerType.SHAPE, gradientRect.type)
        assertEquals(ShapeType.CARD, gradientRect.shapeType)
        assertNotNull(gradientRect.fillGradient)
        assertEquals(22f, gradientRect.appearance.shadowBlur)
        assertEquals(0.4f, gradientRect.appearance.shadowIntensity, 0.01f)

        val triangle = layers.first { it.id == "shape_triangle" }
        assertEquals(ShapeType.TRIANGLE, triangle.shapeType)

        val line = layers.first { it.id == "shape_line" }
        assertEquals(ShapeType.LINE, line.shapeType)
        assertEquals(8f, line.strokeWidthPx, 0.01f)
        assertNotNull(line.strokeColorArgb)

        val arrow = layers.first { it.id == "shape_arrow" }
        assertEquals(ShapeType.ARROW, arrow.shapeType)
        assertEquals(EditorShapeGeometry.FABRIC_ARROW_PATH, arrow.pathData)

        val textGradient = layers.first { it.id == "text_gradient" }
        assertEquals(LayerType.TEXT, textGradient.type)
        assertNotNull(textGradient.textColorGradient)
        val textGradientFrame = layers.firstOrNull {
            it.groupId == textGradient.groupId && it.groupRole == com.thgiang.image.studio.ui.editor.model.LayerGroupRole.FRAME
        }
        assertNotNull(textGradientFrame)
        assertEquals(18f, textGradientFrame!!.appearance.shadowBlur)

        val decorationText = layers.first { it.id == "decoration_text_fallback" }
        assertEquals("Hello", decorationText.text)
        assertEquals(LayerType.TEXT, decorationText.type)
    }

    @Test
    fun `radial gradient ground shadow strips drop shadow intensity`() {
        val template = CloudTemplateParser.parse(
            templateId = "ground_shadow_tpl",
            categoryId = "cat",
            canvasData = JSONObject(GROUND_SHADOW_FIXTURE),
        )

        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)
        val groundShadow = layers.first { it.id == "layer_ground_shadow" }
        assertEquals(LayerType.SHAPE, groundShadow.type)
        assertEquals(ShapeType.CIRCLE, groundShadow.shapeType)
        assertNotNull(groundShadow.fillGradient)
        assertEquals(0f, groundShadow.appearance.shadowIntensity, 0.001f)

        val product = layers.first { it.id == "layer_product" }
        assertEquals(LayerType.IMAGE, product.type)
        assertEquals(0.3f, product.appearance.shadowIntensity, 0.01f)
        assertEquals(true, product.product.isSample)
    }

    @Test
    fun `IMAGE with replaceable flag maps to isSample`() {
        val template = CloudTemplateParser.parse(
            templateId = "replaceable_image_tpl",
            categoryId = "cat",
            canvasData = JSONObject(REPLACEABLE_IMAGE_FIXTURE),
        )

        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)
        val product = layers.single { it.id == "layer_replaceable_image" }
        assertEquals(LayerType.IMAGE, product.type)
        assertEquals(true, product.product.isSample)
    }

    @Test
    fun `cloud tracking maps to app spacing in px`() {
        val template = CloudTemplateParser.parse(
            templateId = "tracking_tpl",
            categoryId = "cat_text",
            canvasData = JSONObject(TRACKING_FIXTURE),
        )

        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)
        val textLayer = layers.single { it.id == "tracking_text" }

        assertEquals(24f, textLayer.charSpacing, 0.001f)
    }

    @Test
    fun testGradientAngleTrace() {
        for (a in 350..365) {
            val grad = com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper.buildLinearGradient(
                color1Argb = 0xFF000000.toInt(),
                color2Argb = 0xFFFFFFFF.toInt(),
                angleDegrees = a.toFloat()
            )
            val mapped = com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper.linearGradientAngleDegrees(grad)
            println("Angle trace: Input=$a -> x1=${grad.coords.x1}, y1=${grad.coords.y1}, x2=${grad.coords.x2}, y2=${grad.coords.y2} -> Mapped=$mapped")
        }
    }

    @Test
    fun testGradientAngleParity() {
        for (a in 0..360) {
            val grad = com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper.buildLinearGradient(
                color1Argb = 0xFF000000.toInt(),
                color2Argb = 0xFFFFFFFF.toInt(),
                angleDegrees = a.toFloat()
            )
            val mapped = com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper.linearGradientAngleDegrees(grad)
            val diff = kotlin.math.abs(a % 360 - mapped % 360)
            org.junit.Assert.assertTrue("Angle $a mismatch: $mapped", diff < 1f || diff > 359f)
        }
    }

    companion object {
        private val PARITY_FIXTURE = """
            {
              "templateId": "parity_tpl",
              "categoryId": "cat_shapes",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920, "aspectRatio": "9:16" },
              "layers": [
                {
                  "layerId": "shape_gradient_rect",
                  "type": "DECORATION",
                  "zIndex": 0,
                  "transform": { "anchorX": 0.5, "anchorY": 0.4, "scale": 1, "rotation": 15 },
                  "payload": {
                    "shapeType": "rect",
                    "baseWidth": 320,
                    "baseHeight": 180,
                    "fillColor": "#ff0000",
                    "fillGradient": {
                      "type": "linear",
                      "colorStops": [
                        { "offset": 0, "color": "#ff0000" },
                        { "offset": 1, "color": "#0000ff" }
                      ],
                      "coords": { "x1": 0, "y1": 0, "x2": 1, "y2": 1 }
                    },
                    "shadowIntensity": 0.4,
                    "shadowAngle": 45,
                    "shadowDistance": 12,
                    "shadowBlur": 22,
                    "shadowColorArgb": -872415232,
                    "blendMode": "multiply"
                  }
                },
                {
                  "layerId": "shape_triangle",
                  "type": "DECORATION",
                  "zIndex": 1,
                  "transform": { "anchorX": 0.3, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "shapeType": "triangle",
                    "baseWidth": 200,
                    "baseHeight": 200,
                    "fillColor": "#22c55e"
                  }
                },
                {
                  "layerId": "shape_line",
                  "type": "DECORATION",
                  "zIndex": 2,
                  "transform": { "anchorX": 0.7, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "shapeType": "line",
                    "baseWidth": 240,
                    "baseHeight": 40,
                    "fillColor": "#6366f1",
                    "strokeWidth": 8
                  }
                },
                {
                  "layerId": "shape_arrow",
                  "type": "DECORATION",
                  "zIndex": 3,
                  "transform": { "anchorX": 0.5, "anchorY": 0.65, "scale": 1, "rotation": 0 },
                  "payload": {
                    "shapeType": "arrow",
                    "baseWidth": 200,
                    "baseHeight": 120,
                    "fillColor": "#f59e0b",
                    "pathData": "M -100 -20 L 20 -20 L 20 -60 L 100 0 L 20 60 L 20 20 L -100 20 Z"
                  }
                },
                {
                  "layerId": "text_gradient",
                  "type": "TEXT",
                  "zIndex": 4,
                  "transform": { "anchorX": 0.5, "anchorY": 0.8, "scale": 1, "rotation": 0 },
                  "payload": {
                    "text": "SALE",
                    "fontSize": 72,
                    "textColorArgb": -1,
                    "baseWidth": 300,
                    "baseHeight": 120,
                    "shapeType": "pill",
                    "fillColor": "#e11d48",
                    "textColorGradient": {
                      "type": "linear",
                      "colorStops": [
                        { "offset": 0, "color": "#ffffff" },
                        { "offset": 1, "color": "#fde68a" }
                      ],
                      "coords": { "x1": 0, "y1": 0, "x2": 0, "y2": 1 }
                    },
                    "shadowBlur": 18,
                    "shadowIntensity": 0.5,
                    "shadowDistance": 10,
                    "shadowAngle": 90
                  }
                },
                {
                  "layerId": "decoration_text_fallback",
                  "type": "DECORATION",
                  "zIndex": 5,
                  "transform": { "anchorX": 0.5, "anchorY": 0.9, "scale": 1, "rotation": 0 },
                  "payload": {
                    "text": "Hello",
                    "fontSize": 48,
                    "textColorArgb": -1,
                    "baseWidth": 200,
                    "baseHeight": 80
                  }
                },
                {
                  "layerId": "junk_1x1_image",
                  "type": "DECORATION",
                  "zIndex": 6,
                  "transform": { "anchorX": 0.5, "anchorY": 0.95, "scale": 1, "rotation": 0 },
                  "payload": {
                    "imageUrl": "https://example.com/1x1.png",
                    "baseWidth": 1,
                    "baseHeight": 1
                  }
                }
              ]
            }
        """.trimIndent()

        private val GROUND_SHADOW_FIXTURE = """
            {
              "templateId": "ground_shadow_tpl",
              "categoryId": "cat",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920, "aspectRatio": "9:16" },
              "layers": [
                {
                  "layerId": "layer_ground_shadow",
                  "type": "DECORATION",
                  "zIndex": 0,
                  "transform": { "anchorX": 0.5, "anchorY": 0.72, "scale": 1, "rotation": 0 },
                  "payload": {
                    "shapeType": "ellipse",
                    "baseWidth": 420,
                    "baseHeight": 90,
                    "fillColor": "rgba(0,0,0,0.28)",
                    "fillGradient": {
                      "type": "radial",
                      "colorStops": [
                        { "offset": 0, "color": "rgba(0,0,0,0.28)" },
                        { "offset": 0.55, "color": "rgba(0,0,0,0.16)" },
                        { "offset": 1, "color": "rgba(0,0,0,0)" }
                      ],
                      "coords": { "x1": 0.5, "y1": 0.5, "r1": 0, "x2": 0.5, "y2": 0.5, "r2": 0.5 }
                    },
                    "shadowIntensity": 0.94,
                    "shadowAngle": 45,
                    "shadowDistance": 12,
                    "shadowBlur": 15,
                    "alpha": 0.95
                  }
                },
                {
                  "layerId": "layer_product",
                  "type": "PLACEHOLDER_OBJECT",
                  "zIndex": 1,
                  "transform": { "anchorX": 0.5, "anchorY": 0.55, "scale": 1, "rotation": 0 },
                  "payload": {
                    "imageUrl": "https://example.com/product.png",
                    "baseWidth": 320,
                    "baseHeight": 320,
                    "shadowIntensity": 0.3,
                    "shadowAngle": 45,
                    "shadowDistance": 12
                  }
                }
              ]
            }
        """.trimIndent()

        private val REPLACEABLE_IMAGE_FIXTURE = """
            {
              "templateId": "replaceable_image_tpl",
              "categoryId": "cat",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920, "aspectRatio": "9:16" },
              "layers": [
                {
                  "layerId": "layer_replaceable_image",
                  "type": "IMAGE",
                  "zIndex": 0,
                  "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "imageUrl": "https://example.com/sample.png",
                    "defaultImageUrl": "https://example.com/sample.png",
                    "baseWidth": 400,
                    "baseHeight": 400,
                    "replaceable": true
                  }
                }
              ]
            }
        """.trimIndent()

        private val TRACKING_FIXTURE = """
            {
              "templateId": "tracking_tpl",
              "categoryId": "cat_text",
              "canvas": { "baseWidth": 1000, "baseHeight": 1000, "aspectRatio": "1:1" },
              "layers": [
                {
                  "layerId": "tracking_text",
                  "type": "TEXT",
                  "zIndex": 0,
                  "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "text": "TEST",
                    "fontSize": 120,
                    "charSpacing": 200,
                    "baseWidth": 400,
                    "baseHeight": 120
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
