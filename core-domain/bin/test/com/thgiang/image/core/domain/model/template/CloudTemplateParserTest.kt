package com.thgiang.image.core.domain.model.template

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTemplateParserTest {

    @Test
    fun `parse text layer with extended payload fields`() {
        val canvasData = JSONObject(
            """
            {
              "templateId": "tpl_1",
              "categoryId": "cat_1",
              "metadata": { "title": "Demo", "schemaVersion": 1 },
              "canvas": { "baseWidth": 1080, "baseHeight": 1920, "aspectRatio": "9:16" },
              "layers": [
                {
                  "layerId": "text_1",
                  "type": "TEXT",
                  "zIndex": 2,
                  "transform": { "anchorX": 0.5, "anchorY": 0.4, "scale": 1, "rotation": 0 },
                  "payload": {
                    "text": "Hello",
                    "font": "Outfit",
                    "fill": "#6366f1",
                    "fontSize": 72,
                    "visible": true,
                    "locked": false,
                    "underline": true,
                    "lineHeight": 1.2,
                    "charSpacing": 10,
                    "textTransform": "uppercase",
                    "blendMode": "multiply"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val template = CloudTemplateParser.parse(
            templateId = "tpl_fallback",
            categoryId = "cat_fallback",
            canvasData = canvasData,
        )

        assertEquals("tpl_1", template.templateId)
        assertEquals(1, template.layers.size)
        val layer = template.layers.first()
        assertEquals("TEXT", layer.type)
        assertEquals("Hello", layer.payload.text)
        assertEquals("Outfit", layer.payload.font)
        assertEquals("#6366f1", layer.payload.fill)
        assertTrue(layer.payload.underline == true)
        assertEquals(1.2f, layer.payload.lineHeight)
        assertEquals("multiply", layer.payload.blendMode)
        assertNotNull(layer.payload.resolvedTextColorArgb())
    }

    @Test
    fun `parse stroke fields`() {
        val canvasData = JSONObject(
            """
            {
              "templateId": "tpl_1",
              "categoryId": "cat_1",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
              "layers": [
                {
                  "layerId": "shape_1",
                  "type": "DECORATION",
                  "zIndex": 1,
                  "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "shapeType": "rect",
                    "fillColor": "#ff0000",
                    "stroke": "#000000",
                    "strokeWidth": 4,
                    "strokeDashArray": [8, 4],
                    "blendMode": "multiply"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val payload = CloudTemplateParser.parse("tpl_1", "cat_1", canvasData).layers.first().payload
        assertEquals("#000000", payload.stroke)
        assertEquals(4f, payload.strokeWidth)
        assertEquals(listOf(8f, 4f), payload.strokeDashArray)
        assertEquals("multiply", payload.blendMode)
    }

    @Test
    fun `parse numeric fontWeight`() {
        val canvasData = JSONObject(
            """
            {
              "templateId": "tpl_1",
              "categoryId": "cat_1",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
              "layers": [
                {
                  "layerId": "text_1",
                  "type": "TEXT",
                  "zIndex": 1,
                  "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": { "text": "Bold", "fontWeight": 700 }
                }
              ]
            }
            """.trimIndent()
        )

        val template = CloudTemplateParser.parse("tpl_1", "cat_1", canvasData)
        assertEquals("700", template.layers.first().payload.fontWeight)
    }

    @Test
    fun `parseFromApiItem reads canvas_data wrapper`() {
        val item = JSONObject(
            """
            {
              "id": "row_1",
              "template_id": "tpl_api",
              "category_id": "beauty",
              "title": "API Template",
              "thumbnail_url": "https://cdn.example/thumb.png",
              "status": "published",
              "canvas_data": {
                "templateId": "tpl_api",
                "categoryId": "beauty",
                "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
                "layers": []
              }
            }
            """.trimIndent()
        )

        val template = CloudTemplateParser.parseFromApiItem(item)
        assertEquals("tpl_api", template.templateId)
        assertEquals("API Template", template.metadata.title)
        assertEquals("https://cdn.example/thumb.png", template.metadata.thumbnailUrl)
    }

    @Test
    fun `parseLayers ignores null string placeholders`() {
        val layers = JSONArray(
            """
            [
              {
                "layerId": "img_1",
                "type": "IMAGE",
                "zIndex": 0,
                "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                "payload": { "text": "null", "font": "", "imageUrl": "null" }
              }
            ]
            """.trimIndent()
        )

        val parsed = CloudTemplateParser.parseLayers(layers).first().payload
        assertNull(parsed.text)
        assertNull(parsed.font)
        assertNull(parsed.imageUrl)
    }

    @Test
    fun `parse gradient fields`() {
        val canvasData = JSONObject(
            """
            {
              "templateId": "tpl_1",
              "categoryId": "cat_1",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
              "layers": [
                {
                  "layerId": "shape_1",
                  "type": "DECORATION",
                  "zIndex": 1,
                  "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "shapeType": "rect",
                    "fillColor": "#ff0000",
                    "fillGradient": {
                      "type": "linear",
                      "colorStops": [
                        { "offset": 0, "color": "#ff0000" },
                        { "offset": 1, "color": "#0000ff" }
                      ],
                      "coords": { "x1": 0, "y1": 0.5, "x2": 1, "y2": 0.5 }
                    },
                    "textColorGradient": {
                      "type": "radial",
                      "colorStops": [
                        { "offset": 0, "color": "#ffffff" },
                        { "offset": 1, "color": "#cccccc" }
                      ],
                      "coords": { "x1": 0.5, "y1": 0.5, "r1": 0, "x2": 0.5, "y2": 0.5, "r2": 0.5 }
                    },
                    "shadowBlur": 22
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val payload = CloudTemplateParser.parse("tpl_1", "cat_1", canvasData).layers.first().payload
        assertNotNull(payload.fillGradient)
        assertEquals("linear", payload.fillGradient?.type)
        assertEquals(2, payload.fillGradient?.colorStops?.size)
        assertEquals(0f, payload.fillGradient?.colorStops?.first()?.offset)
        assertEquals("#ff0000", payload.fillGradient?.colorStops?.first()?.color)
        assertEquals(1f, payload.fillGradient?.coords?.x2)
        assertNotNull(payload.textColorGradient)
        assertEquals("radial", payload.textColorGradient?.type)
        assertEquals(0.5f, payload.textColorGradient?.coords?.r2)
        assertEquals(22f, payload.shadowBlur)
    }

    @Test
    fun `parse pathData and polygonPoints`() {
        val canvasData = JSONObject(
            """
            {
              "templateId": "tpl_1",
              "categoryId": "cat_1",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
              "layers": [{
                "layerId": "shape_1",
                "type": "DECORATION",
                "zIndex": 0,
                "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                "payload": {
                  "shapeType": "arrow",
                  "pathData": "M -100 -20 L 100 0 Z",
                  "polygonPoints": [0, -100, 100, 0, 0, 100, -100, 0]
                }
              }]
            }
            """.trimIndent()
        )

        val payload = CloudTemplateParser.parse("tpl_1", "cat_1", canvasData).layers.first().payload
        assertEquals("M -100 -20 L 100 0 Z", payload.pathData)
        assertEquals(listOf(0f, -100f, 100f, 0f, 0f, 100f, -100f, 0f), payload.polygonPoints)
    }

    @Test
    fun colorArgbParser_parsesHexAndRgba() {
        assertEquals(0xFF6366F1.toInt(), ColorArgbParser.parseOrNull("#6366f1"))
        val rgba = ColorArgbParser.parseOrNull("rgba(99, 102, 241, 0.5)")
        assertNotNull(rgba)
        val alpha = (rgba!! ushr 24) and 0xFF
        assertTrue(alpha in 120..130)
    }
}
