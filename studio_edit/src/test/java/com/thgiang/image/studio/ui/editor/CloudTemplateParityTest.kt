package com.thgiang.image.studio.ui.editor

import com.thgiang.image.core.domain.model.template.CloudTemplateParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

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
        assertEquals(5, layers.size)

        val gradientRect = layers.first { it.id == "shape_gradient_rect" }
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
        assertNotNull(textGradient.textColorGradient)
        assertEquals(18f, textGradient.appearance.shadowBlur)
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
                }
              ]
            }
        """.trimIndent()
    }
}
