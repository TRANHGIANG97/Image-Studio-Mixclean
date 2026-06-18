package com.thgiang.image.studio.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorShapeGeometryTest {

    @Test
    fun `maps cloud shape types for vector primitives`() {
        assertEquals(ShapeType.TRIANGLE, mapShape("triangle"))
        assertEquals(ShapeType.LINE, mapShape("line"))
        assertEquals(ShapeType.DIAMOND, mapShape("diamond"))
        assertEquals(ShapeType.ARROW, mapShape("arrow"))
        assertEquals(ShapeType.PATH, mapShape("path"))
        assertEquals(ShapeType.POLYGON, mapShape("polygon"))
    }

    @Test
    fun `line is stroke-only filled check`() {
        assertTrue(EditorShapeGeometry.isLineShape(ShapeType.LINE))
        assertFalse(
            EditorShapeGeometry.isFilledShape(
                shapeType = ShapeType.LINE,
                shapeColorAlpha = 255,
                hasGradient = false,
            ),
        )
        assertTrue(
            EditorShapeGeometry.isFilledShape(
                shapeType = ShapeType.ARROW,
                shapeColorAlpha = 0,
                hasGradient = false,
            ),
        )
    }

    @Test
    fun `pill corner radius defaults to capsule`() {
        assertEquals(
            50f,
            EditorShapeGeometry.resolvePillCornerRadius(100f, null, null),
            0.01f,
        )
    }

    @Test
    fun `pill corner radius respects custom value`() {
        assertEquals(
            20f,
            EditorShapeGeometry.resolvePillCornerRadius(100f, 20f, null),
            0.01f,
        )
    }

    @Test
    fun `pill corner radius clamps to capsule max`() {
        assertEquals(
            50f,
            EditorShapeGeometry.resolvePillCornerRadius(100f, 80f, null),
            0.01f,
        )
    }

    private fun mapShape(raw: String): ShapeType {
        val template = com.thgiang.image.core.domain.model.template.CloudTemplateParser.parse(
            templateId = "tpl",
            categoryId = "cat",
            canvasData = org.json.JSONObject(
                """
                {
                  "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
                  "layers": [{
                    "layerId": "s1",
                    "type": "DECORATION",
                    "zIndex": 0,
                    "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                    "payload": { "shapeType": "$raw", "baseWidth": 200, "baseHeight": 120, "fillColor": "#ff0000" }
                  }]
                }
                """.trimIndent(),
            ),
        )
        return CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)
            .first()
            .shapeType
    }
}
