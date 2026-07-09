package com.thgiang.image.core.domain.model.template

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTemplateExtensionsTest {

    @Test
    fun `hasGradientBakedShadow detects radial fade to transparent`() {
        val layer = CloudLayer(
            type = "DECORATION",
            payload = CloudPayload(
                shapeType = "ellipse",
                fillGradient = CloudGradient(
                    type = "radial",
                    colorStops = listOf(
                        CloudGradientStop(0f, "rgba(0,0,0,0.28)"),
                        CloudGradientStop(1f, "rgba(0,0,0,0)"),
                    ),
                ),
                shadowIntensity = 0.94f,
            ),
        )
        assertTrue(layer.hasGradientBakedShadow())
    }

    @Test
    fun `hasGradientBakedShadow ignores linear gradient shapes with drop shadow`() {
        val layer = CloudLayer(
            type = "DECORATION",
            payload = CloudPayload(
                shapeType = "rect",
                fillGradient = CloudGradient(
                    type = "linear",
                    colorStops = listOf(
                        CloudGradientStop(0f, "#ff0000"),
                        CloudGradientStop(1f, "#0000ff"),
                    ),
                ),
                shadowIntensity = 0.4f,
                shadowDistance = 12f,
            ),
        )
        assertFalse(layer.hasGradientBakedShadow())
    }

    @Test
    fun `hasGradientBakedShadow ignores shadow region layers`() {
        val layer = CloudLayer(
            type = "SHADOW_REGION",
            payload = CloudPayload(
                sourceKind = "shadow-region",
                shapeType = "ellipse",
            ),
        )
        assertFalse(layer.hasGradientBakedShadow())
    }

    @Test
    fun `isReplaceableLayer accepts PLACEHOLDER_OBJECT type`() {
        val layer = CloudLayer(type = "PLACEHOLDER_OBJECT", payload = CloudPayload())
        assertTrue(layer.isReplaceableLayer())
    }

    @Test
    fun `isReplaceableLayer accepts replaceable flag on IMAGE`() {
        val layer = CloudLayer(
            type = "IMAGE",
            payload = CloudPayload(replaceable = true),
        )
        assertTrue(layer.isReplaceableLayer())
    }

    @Test
    fun `isReplaceableLayer rejects plain IMAGE`() {
        val layer = CloudLayer(type = "IMAGE", payload = CloudPayload())
        assertFalse(layer.isReplaceableLayer())
    }
}
