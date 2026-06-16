package com.thgiang.image.studio.util

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.thgiang.image.studio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object FontDownloader {
    private const val TAG = "FontDownloader"
    private val fontCache = ConcurrentHashMap<String, Typeface>()
    private val fontUrls = ConcurrentHashMap<String, String>() // fontFamily -> url
    private var isFontListLoaded = false

    suspend fun getAvailableFontFamilies(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isFontListLoaded) {
                    loadFontList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh font families", e)
            }

            listOf("sans-serif", "serif", "monospace", "cursive") +
                fontUrls.keys
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }

    suspend fun getTypeface(context: Context, fontFamily: String?): Typeface? {
        if (fontFamily.isNullOrEmpty() || fontFamily.lowercase() in listOf("sans-serif", "serif", "monospace", "cursive", "outfit")) {
            return null // Use system default
        }

        // Check in-memory cache
        fontCache[fontFamily]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                if (!isFontListLoaded) {
                    loadFontList()
                }

                val fontUrl = fontUrls[fontFamily] ?: fontUrls[fontFamily.lowercase()]
                if (fontUrl == null) {
                    Log.e(TAG, "No URL found for font: $fontFamily")
                    return@withContext null
                }

                // local file path in cacheDir
                val fontDir = File(context.cacheDir, "fonts")
                if (!fontDir.exists()) fontDir.mkdirs()
                
                val sanitizeName = fontFamily.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val fontFile = File(fontDir, "$sanitizeName.ttf")

                if (!fontFile.exists()) {
                    Log.d(TAG, "Downloading font $fontFamily from $fontUrl")
                    val connection = URL(fontUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.inputStream.use { input ->
                        fontFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Downloaded font $fontFamily successfully")
                }

                val typeface = Typeface.createFromFile(fontFile)
                if (typeface != null) {
                    fontCache[fontFamily] = typeface
                }
                typeface
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load/download font: $fontFamily", e)
                null
            }
        }
    }

    private fun loadFontList() {
        val baseUrl = BuildConfig.ADMIN_WEB_BASE_URL
        if (baseUrl.isBlank()) return
        try {
            val connection = (URL("$baseUrl/api/v1/fonts").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                if (root.optBoolean("success", false)) {
                    val fontsArray = root.optJSONArray("fonts")
                    if (fontsArray != null) {
                        for (i in 0 until fontsArray.length()) {
                            val obj = fontsArray.optJSONObject(i) ?: continue
                            val name = obj.optString("name")
                            val familySlug = obj.optString("family_slug")
                            val fontUrl = obj.optString("font_url")
                            if (name.isNotEmpty() && fontUrl.isNotEmpty()) {
                                fontUrls[name] = fontUrl
                                fontUrls[name.lowercase()] = fontUrl
                                if (familySlug.isNotEmpty()) {
                                    fontUrls[familySlug] = fontUrl
                                    fontUrls[familySlug.lowercase()] = fontUrl
                                }
                            }
                        }
                        isFontListLoaded = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load font list from server", e)
        }
    }
}
