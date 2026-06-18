package com.thgiang.image.studio.util

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.thgiang.image.studio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class FontManifestEntry(
    val id: String,
    val name: String,
    val familySlug: String,
    val style: String,
    val source: String,
    val fontUrl: String?,
    val aliases: List<String> = emptyList(),
)

data class FontsManifest(
    val schemaVersion: Int,
    val systemFonts: List<String>,
    val fonts: List<FontManifestEntry>,
)

object FontDownloader {
    private const val TAG = "FontDownloader"

    private val fontCache = ConcurrentHashMap<String, Typeface>()
    private val aliasToSlug = ConcurrentHashMap<String, String>()
    private var cachedManifest: FontsManifest? = null
    private var isManifestLoaded = false

    val fontCategories: List<String> = listOf(
        "Hệ thống",
        "Quảng cáo",
        "In ấn & Sách",
        "Thư pháp & Nghệ thuật",
        "Hiện đại",
        "Cổ điển",
        "Trang trí",
        "Chưa phân loại",
    )

    suspend fun getManifest(forceRefresh: Boolean = false): FontsManifest {
        if (!forceRefresh && cachedManifest != null) {
            return cachedManifest!!
        }
        return withContext(Dispatchers.IO) {
            loadManifest(forceRefresh)
            cachedManifest ?: FontsManifest(
                schemaVersion = 1,
                systemFonts = listOf("sans-serif", "serif", "monospace", "cursive"),
                fonts = emptyList(),
            )
        }
    }

    suspend fun getAvailableFonts(): List<FontManifestEntry> = getManifest().fonts

    /** @deprecated Use [getAvailableFonts] for manifest entries. */
    suspend fun getAvailableFontFamilies(): List<String> =
        getAvailableFonts().map { it.familySlug }

    suspend fun getTypeface(context: Context, fontFamily: String?): Typeface? {
        if (fontFamily.isNullOrBlank()) return null

        val manifest = getManifest()
        val entry = resolveEntry(manifest, fontFamily) ?: return null

        if (entry.source == "system") {
            return systemTypeface(entry.familySlug)
        }

        fontCache[entry.familySlug]?.let { return it }
        fontCache[fontFamily]?.let { return it }

        val fontUrl = entry.fontUrl ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val fontDir = File(context.cacheDir, "fonts").apply { mkdirs() }
                val sanitizeName = entry.familySlug.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val fontFile = File(fontDir, "$sanitizeName.ttf")

                if (!fontFile.exists() || fontFile.length() == 0L) {
                    Log.d(TAG, "Downloading font ${entry.name} from $fontUrl")
                    val connection = URL(fontUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.inputStream.use { input ->
                        fontFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                val typeface = Typeface.createFromFile(fontFile)
                fontCache[entry.familySlug] = typeface
                cacheAliases(entry, typeface)
                typeface
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load/download font: ${entry.familySlug}", e)
                null
            }
        }
    }

    suspend fun preloadTemplateFonts(context: Context, families: List<String?>) {
        families.filterNot { it.isNullOrBlank() }.distinct().forEach { family ->
            getTypeface(context, family)
        }
    }

    private fun loadManifest(forceRefresh: Boolean) {
        if (!forceRefresh && isManifestLoaded && cachedManifest != null) return

        val baseUrl = BuildConfig.ADMIN_WEB_BASE_URL
        if (baseUrl.isBlank()) return

        try {
            val connection = (URL("$baseUrl/api/v1/fonts").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) return

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) return

            val fonts = parseManifestFonts(root.optJSONArray("fonts"))
            val systemFonts = parseStringArray(root.optJSONArray("system_fonts"))
                .ifEmpty { listOf("sans-serif", "serif", "monospace", "cursive") }

            cachedManifest = FontsManifest(
                schemaVersion = root.optInt("schema_version", 1),
                systemFonts = systemFonts,
                fonts = fonts,
            )
            rebuildAliasIndex(fonts)
            isManifestLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load fonts manifest", e)
        }
    }

    private fun parseManifestFonts(array: JSONArray?): List<FontManifestEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val familySlug = obj.optString("family_slug")
                if (familySlug.isBlank()) continue
                val fontUrl = obj.optString("font_url").takeIf { it.isNotBlank() && it != "null" }
                val aliases = parseStringArray(obj.optJSONArray("aliases"))
                add(
                    FontManifestEntry(
                        id = obj.optString("id", familySlug),
                        name = obj.optString("name", familySlug),
                        familySlug = familySlug,
                        style = obj.optString("style", "Chưa phân loại"),
                        source = obj.optString("source", if (fontUrl == null) "system" else "upload"),
                        fontUrl = fontUrl,
                        aliases = aliases,
                    ),
                )
            }
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun rebuildAliasIndex(fonts: List<FontManifestEntry>) {
        aliasToSlug.clear()
        for (entry in fonts) {
            registerAlias(entry.familySlug, entry.familySlug)
            registerAlias(entry.name, entry.familySlug)
            registerAlias(entry.name.replace("\\s+".toRegex(), ""), entry.familySlug)
            entry.aliases.forEach { alias ->
                val base = alias.split(",").firstOrNull()?.trim() ?: alias
                registerAlias(base, entry.familySlug)
                registerAlias(base.replace("\\s+".toRegex(), ""), entry.familySlug)
            }
        }
    }

    private fun registerAlias(alias: String, slug: String) {
        if (alias.isBlank()) return
        aliasToSlug[alias.lowercase()] = slug
    }

    private fun resolveEntry(manifest: FontsManifest, fontFamily: String): FontManifestEntry? {
        val raw = fontFamily.split(",").firstOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        val normalized = raw.lowercase()
        val compact = normalized.replace("\\s+".toRegex(), "")

        manifest.fonts.firstOrNull { it.familySlug.equals(raw, ignoreCase = true) }?.let { return it }
        manifest.fonts.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }

        aliasToSlug[normalized]?.let { slug ->
            return manifest.fonts.firstOrNull { it.familySlug == slug }
        }
        aliasToSlug[compact]?.let { slug ->
            return manifest.fonts.firstOrNull { it.familySlug == slug }
        }

        return null
    }

    private fun cacheAliases(entry: FontManifestEntry, typeface: Typeface) {
        fontCache[entry.familySlug] = typeface
        fontCache[entry.name] = typeface
        entry.aliases.forEach { alias ->
            val base = alias.split(",").firstOrNull()?.trim() ?: return@forEach
            fontCache[base] = typeface
        }
    }

    private fun systemTypeface(slug: String): Typeface? = when (slug.lowercase()) {
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        "sans-serif", "cursive" -> Typeface.SANS_SERIF
        else -> Typeface.SANS_SERIF
    }
}
