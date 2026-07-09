package com.thgiang.image.studio.ui.editor

import androidx.compose.ui.graphics.BlendMode
import com.thgiang.image.core.domain.logging.AppLogger
import com.thgiang.image.core.domain.model.template.CloudTemplateParser
import com.thgiang.image.studio.ui.editor.mapper.CloudLayerToEditorMapper
import com.thgiang.image.studio.ui.editor.mapper.EditorBlendModeMapper
import com.thgiang.image.studio.util.FontDownloader
import com.thgiang.image.studio.util.FontManifestEntry
import com.thgiang.image.studio.util.FontsManifest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 (App Mobile Defense): hostile/unknown data from the Web contract must
 * degrade gracefully — never crash, never leak raw values into the renderer.
 */
class CloudDefenseTest {

    // ── BlendMode whitelist ─────────────────────────────────────────

    @Test
    fun `unknown blendMode maps to null`() {
        assertNull(EditorBlendModeMapper.toComposeBlendModeOrNull("psychedelic-glow"))
        assertNull(EditorBlendModeMapper.toComposeBlendModeOrNull("MULTIPLY_EXTREME"))
        assertNull(EditorBlendModeMapper.toComposeBlendModeOrNull(""))
        assertNull(EditorBlendModeMapper.toComposeBlendModeOrNull(null))
        assertNull(EditorBlendModeMapper.toComposeBlendModeOrNull("normal"))
    }

    @Test
    fun `whitelisted blendModes map to compose blend modes`() {
        assertEquals(BlendMode.Multiply, EditorBlendModeMapper.toComposeBlendModeOrNull("multiply"))
        assertEquals(BlendMode.Screen, EditorBlendModeMapper.toComposeBlendModeOrNull("SCREEN"))
        assertEquals(BlendMode.Overlay, EditorBlendModeMapper.toComposeBlendModeOrNull("overlay"))
        assertEquals(BlendMode.ColorDodge, EditorBlendModeMapper.toComposeBlendModeOrNull("color-dodge"))
        assertEquals(BlendMode.Softlight, EditorBlendModeMapper.toComposeBlendModeOrNull("soft-light"))
        assertEquals(BlendMode.Luminosity, EditorBlendModeMapper.toComposeBlendModeOrNull("luminosity"))
        assertEquals(BlendMode.Plus, EditorBlendModeMapper.toComposeBlendModeOrNull("linear-dodge"))
    }

    @Test
    fun `unknown blendMode renders normal without offscreen compositing`() {
        assertFalse(EditorBlendModeMapper.needsOffscreenCompositing("psychedelic-glow"))
        assertFalse(EditorBlendModeMapper.needsOffscreenCompositing("normal"))
        assertFalse(EditorBlendModeMapper.needsOffscreenCompositing(null))
        assertTrue(EditorBlendModeMapper.needsOffscreenCompositing("multiply"))
        assertEquals(BlendMode.SrcOver, EditorBlendModeMapper.toComposeBlendMode("psychedelic-glow"))
    }

    // ── Font fallback chain ─────────────────────────────────────────

    @Test
    fun `unknown font falls back to sans-serif`() {
        val manifest = manifestWithOutfit()
        assertEquals("Outfit", FontDownloader.resolveFamilySlugOrFallback(manifest, "Outfit"))
        assertEquals(
            FontDownloader.FALLBACK_FONT_FAMILY,
            FontDownloader.resolveFamilySlugOrFallback(manifest, "UTM Mystery Font"),
        )
        assertEquals(
            FontDownloader.FALLBACK_FONT_FAMILY,
            FontDownloader.resolveFamilySlugOrFallback(manifest, null),
        )
    }

    @Test
    fun `css font stack resolves first known candidate`() {
        val manifest = manifestWithOutfit()
        assertEquals(
            "Outfit",
            FontDownloader.resolveFamilySlugOrFallback(manifest, "UTM Mystery Font, Outfit, sans-serif"),
        )
    }

    // ── Graceful skip / hostile layer data ──────────────────────────

    @Test
    fun `hostile layer data maps without crashing`() {
        val template = CloudTemplateParser.parse(
            templateId = "hostile_tpl",
            categoryId = "cat",
            canvasData = JSONObject(HOSTILE_FIXTURE),
        )

        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)

        // The valid text layer survives; junk layers are skipped, not fatal.
        assertTrue(layers.any { it.id == "valid_text" })
        assertTrue(layers.none { it.id == "junk_unknown_type" })
    }

    // ── Observability (AppLogger) ───────────────────────────────────

    @Test
    fun `mapper skip is reported to AppLogger`() {
        val logger = FakeAppLogger()
        val template = CloudTemplateParser.parse(
            templateId = "hostile_tpl",
            categoryId = "cat",
            canvasData = JSONObject(HOSTILE_FIXTURE),
        )

        CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f, logger = logger)

        // The junk layer skip must surface as logNonFatal (exception) or logWarning (null-mapped).
        val reportedLayerIds =
            logger.nonFatals.map { it.second["layerId"] } + logger.warnings.map { it.second["layerId"] }
        assertTrue(reportedLayerIds.contains("junk_unknown_type"))
    }

    @Test
    fun `template_mapped event carries mapped and skipped counts`() {
        val logger = FakeAppLogger()
        val template = CloudTemplateParser.parse(
            templateId = "hostile_tpl",
            categoryId = "cat",
            canvasData = JSONObject(HOSTILE_FIXTURE),
        )

        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f, logger = logger)

        val event = logger.events.single { it.first == "template_mapped" }
        assertEquals("hostile_tpl", event.second["templateId"])
        assertEquals(layers.size.toString(), event.second["layerCount"])
        assertEquals("1", event.second["layerCount"])
        assertEquals("1", event.second["skippedCount"])
    }

    @Test
    fun `no logger passed keeps mapping silent and safe`() {
        val template = CloudTemplateParser.parse(
            templateId = "hostile_tpl",
            categoryId = "cat",
            canvasData = JSONObject(HOSTILE_FIXTURE),
        )

        // Default parameter path (tests/legacy callers): must not crash without a logger.
        val layers = CloudLayerToEditorMapper.mapLayers(template, scaledDensity = 3f)
        assertTrue(layers.any { it.id == "valid_text" })
    }

    private class FakeAppLogger : AppLogger {
        val nonFatals = mutableListOf<Pair<Throwable, Map<String, String>>>()
        val warnings = mutableListOf<Pair<String, Map<String, String>>>()
        val events = mutableListOf<Pair<String, Map<String, String>>>()

        override fun logNonFatal(throwable: Throwable, context: Map<String, String>) {
            nonFatals += throwable to context
        }

        override fun logWarning(message: String, context: Map<String, String>) {
            warnings += message to context
        }

        override fun logEvent(name: String, params: Map<String, String>) {
            events += name to params
        }
    }

    private fun manifestWithOutfit() = FontsManifest(
        schemaVersion = 1,
        systemFonts = listOf("sans-serif", "serif"),
        fonts = listOf(
            FontManifestEntry(
                id = "builtin-outfit",
                name = "Outfit",
                familySlug = "Outfit",
                style = "Hệ thống",
                source = "cdn",
                fontUrl = "https://example.com/Outfit.ttf",
            ),
        ),
    )

    companion object {
        private val HOSTILE_FIXTURE = """
            {
              "templateId": "hostile_tpl",
              "categoryId": "cat",
              "canvas": { "baseWidth": 1080, "baseHeight": 1920 },
              "layers": [
                {
                  "layerId": "junk_unknown_type",
                  "type": "FUTURE_TYPE",
                  "zIndex": 0,
                  "transform": { "anchorX": 99, "anchorY": -42, "scale": 0, "rotation": 99999 },
                  "payload": {}
                },
                {
                  "layerId": "valid_text",
                  "type": "TEXT",
                  "zIndex": 1,
                  "transform": { "anchorX": 0.5, "anchorY": 0.5, "scale": 1, "rotation": 0 },
                  "payload": {
                    "text": "Still alive",
                    "fontSize": 48,
                    "font": "Totally Unknown Font",
                    "blendMode": "psychedelic-glow",
                    "baseWidth": 300,
                    "baseHeight": 100
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
