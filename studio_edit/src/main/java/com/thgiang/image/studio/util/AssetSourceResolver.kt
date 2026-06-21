package com.thgiang.image.studio.util

import com.thgiang.image.studio.BuildConfig
import android.content.Context
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val ANDROID_ASSET_PREFIX = "file:///android_asset/"
private const val BG_DEBUG_TAG = "TPL_BG_DEBUG"

/** Unwrap admin_web `/api/proxy?url=` wrappers to the direct CDN URL. */
fun String.unwrapCdnProxyUrl(): String {
    val match = Regex("^(?:https?://[^/]+)?/api/proxy\\?url=(.+)$", RegexOption.IGNORE_CASE).find(trim())
        ?: return this
    return try {
        Uri.decode(match.groupValues[1])
    } catch (_: Exception) {
        this
    }
}

fun String.resolveRelativePath(baseUrl: String = BuildConfig.ADMIN_WEB_BASE_URL): String {
    val clean = this.trim()
    if (clean.startsWith("/") && !clean.startsWith("//") && baseUrl.isNotBlank()) {
        return baseUrl.trimEnd('/') + "/" + clean.removePrefix("/")
    }
    return clean
}

fun String.replaceLocalhostWithConfiguredHost(baseUrl: String = BuildConfig.ADMIN_WEB_BASE_URL): String {
    val resolvedRelative = this.resolveRelativePath(baseUrl)
    val unwrapped = resolvedRelative.unwrapCdnProxyUrl()
    
    // Nếu chứa R2 dev domain và cấu hình web host không phải localhost, proxy qua web server để tránh bị nhà mạng tại VN chặn
    if (unwrapped.contains(".r2.dev", ignoreCase = true)) {
        if (baseUrl.isNotBlank() && !baseUrl.contains("localhost", ignoreCase = true) && !baseUrl.contains("127.0.0.1")) {
            return try {
                val encodedUrl = URLEncoder.encode(unwrapped, StandardCharsets.UTF_8.name())
                "${baseUrl.trimEnd('/')}/api/proxy?url=$encodedUrl"
            } catch (e: Exception) {
                unwrapped
            }
        }
    }

    if (!unwrapped.contains("localhost", ignoreCase = true) && !unwrapped.contains("127.0.0.1")) return unwrapped
    val targetBase = if (baseUrl.isBlank() || baseUrl.contains("localhost", ignoreCase = true) || baseUrl.contains("127.0.0.1")) {
        "http://10.0.2.2:3000"
    } else {
        baseUrl.trimEnd('/')
    }
    val regex = "https?://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?".toRegex(RegexOption.IGNORE_CASE)
    return unwrapped.replace(regex, targetBase)
}

fun String.isRemoteAssetSource(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

fun String.isAndroidAssetUri(): Boolean =
    startsWith(ANDROID_ASSET_PREFIX)

fun String.toAssetModel(): Any {
    val resolved = this.resolveRelativePath()
    return when {
        resolved.isRemoteAssetSource() -> resolved.replaceLocalhostWithConfiguredHost()
        resolved.startsWith("content://") || resolved.startsWith("file://") -> resolved
        else -> resolved.toCloudResolvedAssetUrl()?.replaceLocalhostWithConfiguredHost() ?: resolved.replaceLocalhostWithConfiguredHost()
    }
}

fun String.toCloudResolvedAssetUrl(baseUrl: String = BuildConfig.ADMIN_WEB_BASE_URL): String? {
    val resolved = this.resolveRelativePath(baseUrl)
    if (resolved.isRemoteAssetSource() || resolved.startsWith("content://") || resolved.startsWith("file://")) return resolved.replaceLocalhostWithConfiguredHost(baseUrl)
    if (baseUrl.isBlank()) return null

    // Nếu cấu hình CDN trực tiếp, ghép chuỗi trả về luôn (Bypass Vercel)
    val cdnBaseUrl = BuildConfig.CDN_BASE_URL
    if (cdnBaseUrl.isNotBlank()) {
        val cleanPath = resolved.removePrefix("/")
        val encodedPath = cleanPath.split("/").joinToString("/") { 
            URLEncoder.encode(it, StandardCharsets.UTF_8.name()) 
        }
        return "$cdnBaseUrl$encodedPath"
    }

    val encoded = URLEncoder.encode(resolved, StandardCharsets.UTF_8.name())
    return "$baseUrl/api/v1/assets/resolve?sourcePath=$encoded".replaceLocalhostWithConfiguredHost(baseUrl)
}

fun String.toAssetInputUri(): String {
    val resolved = this.resolveRelativePath()
    return when {
        resolved.isAndroidAssetUri() -> resolved
        resolved.startsWith("content://") || resolved.startsWith("file://") -> resolved
        resolved.isRemoteAssetSource() -> resolved.replaceLocalhostWithConfiguredHost()
        else -> resolved.toCloudResolvedAssetUrl()?.replaceLocalhostWithConfiguredHost() ?: resolved.replaceLocalhostWithConfiguredHost()
    }
}

fun Context.openAssetSourceInputStream(path: String): java.io.InputStream? {
    return try {
        val resolvedPath = path.replaceLocalhostWithConfiguredHost()
        when {
            resolvedPath.isAndroidAssetUri() -> assets.open(resolvedPath.removePrefix(ANDROID_ASSET_PREFIX))
            resolvedPath.startsWith("content://") || resolvedPath.startsWith("file://") -> {
                if (resolvedPath.isAndroidAssetUri()) {
                    assets.open(resolvedPath.removePrefix(ANDROID_ASSET_PREFIX))
                } else {
                    contentResolver.openInputStream(Uri.parse(resolvedPath))
                }
            }
            resolvedPath.isRemoteAssetSource() -> {
                android.util.Log.d(BG_DEBUG_TAG, "open remote asset url=${resolvedPath.take(160)}")
                val conn = (java.net.URL(resolvedPath).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                }
                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    android.util.Log.e(BG_DEBUG_TAG, "remote asset failed status=$responseCode url=${resolvedPath.take(160)}")
                    android.util.Log.e("AssetSource", "Remote asset loading failed with HTTP status $responseCode for: $resolvedPath")
                    null
                } else {
                    android.util.Log.d(BG_DEBUG_TAG, "remote asset ok status=$responseCode url=${resolvedPath.take(160)}")
                    conn.inputStream
                }
            }
            BuildConfig.ADMIN_WEB_BASE_URL.isNotBlank() -> {
                val remoteUrl = resolvedPath.toCloudResolvedAssetUrl() ?: return null
                android.util.Log.d(BG_DEBUG_TAG, "resolve local/cloud asset source=${resolvedPath.take(120)} resolved=${remoteUrl.take(160)}")
                val conn = (java.net.URL(remoteUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                }
                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    android.util.Log.e(BG_DEBUG_TAG, "resolved asset failed status=$responseCode url=${remoteUrl.take(160)}")
                    android.util.Log.e("AssetSource", "Resolved remote asset loading failed with HTTP status $responseCode for: $remoteUrl")
                    null
                } else {
                    android.util.Log.d(BG_DEBUG_TAG, "resolved asset ok status=$responseCode url=${remoteUrl.take(160)}")
                    conn.inputStream
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        android.util.Log.e("AssetSource", "Exception opening stream for path: $path", e)
        null
    }
}

fun assetCacheKey(path: String): String {
    val safe = path.replace(Regex("[^A-Za-z0-9._-]+"), "_")
    return safe.take(160)
}
