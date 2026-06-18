package com.thgiang.image.studio.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Minimal JSON GET client for admin_web public API endpoints.
 */
class AdminWebJsonClient(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
) {
    fun getJson(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        require(baseUrl.isNotBlank()) { "ADMIN_WEB_BASE_URL is not configured" }

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val queryString = query.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append(normalizedPath)
            if (queryString.isNotEmpty()) {
                append('?')
                append(queryString)
            }
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (body.isBlank()) {
                throw IllegalStateException("Empty response from $normalizedPath (HTTP ${connection.responseCode})")
            }

            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) {
                throw IllegalStateException("API success=false for $normalizedPath")
            }
            return root
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
