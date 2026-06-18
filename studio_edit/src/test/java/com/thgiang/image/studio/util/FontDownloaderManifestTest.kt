package com.thgiang.image.studio.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontDownloaderManifestTest {

    @Test
    fun `parse manifest fonts reads schema and aliases`() {
        val json = JSONObject(
            """
            {
              "success": true,
              "schema_version": 1,
              "system_fonts": ["sans-serif", "serif"],
              "fonts": [
                {
                  "id": "builtin-outfit",
                  "name": "Outfit",
                  "family_slug": "Outfit",
                  "style": "Hệ thống",
                  "source": "cdn",
                  "font_url": "https://example.com/Outfit.ttf",
                  "aliases": ["outfit", "Outfit, sans-serif"]
                },
                {
                  "id": "system-sans",
                  "name": "Sans-Serif",
                  "family_slug": "sans-serif",
                  "style": "Hệ thống",
                  "source": "system",
                  "font_url": null
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(json.optBoolean("success"))
        assertEquals(1, json.optInt("schema_version"))
        val fonts = json.optJSONArray("fonts")
        assertEquals(2, fonts.length())
        assertEquals("Outfit", fonts.getJSONObject(0).getString("family_slug"))
    }

    @Test
    fun `manifest system fonts array parses`() {
        val array = JSONArray().put("sans-serif").put("monospace")
        assertEquals(2, array.length())
    }
}
