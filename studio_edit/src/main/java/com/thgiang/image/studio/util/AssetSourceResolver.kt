package com.thgiang.image.studio.util

import com.thgiang.image.studio.BuildConfig
import android.content.Context
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val ANDROID_ASSET_PREFIX = "file:///android_asset/"
private const val BG_DEBUG_TAG = "TPL_BG_DEBUG"

fun String.replaceLocalhostWithConfiguredHost(baseUrl: String = BuildConfig.ADMIN_WEB_BASE_URL): String {
    if (!contains("localhost", ignoreCase = true) && !contains("127.0.0.1")) return this
    val host = try {
        val uri = java.net.URI(baseUrl)
        val h = uri.host
        if (h == null || h == "localhost" || h == "127.0.0.1") "10.0.2.2" else h
    } catch (e: Exception) {
        "10.0.2.2"
    }
    return this
        .replace("localhost", host, ignoreCase = true)
        .replace("127.0.0.1", host)
}

fun String.isRemoteAssetSource(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

fun String.isAndroidAssetUri(): Boolean =
    startsWith(ANDROID_ASSET_PREFIX)

fun String.toAssetModel(): Any = when {
    isRemoteAssetSource() -> this.replaceLocalhostWithConfiguredHost()
    startsWith("content://") || startsWith("file://") -> this
    else -> toCloudResolvedAssetUrl()?.replaceLocalhostWithConfiguredHost() ?: this.replaceLocalhostWithConfiguredHost()
}

fun String.toCloudResolvedAssetUrl(baseUrl: String = BuildConfig.ADMIN_WEB_BASE_URL): String? {
    if (isRemoteAssetSource() || startsWith("content://") || startsWith("file://")) return this.replaceLocalhostWithConfiguredHost(baseUrl)
    if (baseUrl.isBlank()) return null

    // Nếu cấu hình CDN trực tiếp, ghép chuỗi trả về luôn (Bypass Vercel)
    val cdnBaseUrl = BuildConfig.CDN_BASE_URL
    if (cdnBaseUrl.isNotBlank()) {
        val cleanPath = this.removePrefix("/")
        val encodedPath = cleanPath.split("/").joinToString("/") { 
            URLEncoder.encode(it, StandardCharsets.UTF_8.name()) 
        }
        return "$cdnBaseUrl$encodedPath"
    }

    val encoded = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    return "$baseUrl/api/v1/assets/resolve?sourcePath=$encoded".replaceLocalhostWithConfiguredHost(baseUrl)
}

fun String.toAssetInputUri(): String = when {
    isAndroidAssetUri() -> this
    startsWith("content://") || startsWith("file://") -> this
    isRemoteAssetSource() -> this.replaceLocalhostWithConfiguredHost()
    else -> toCloudResolvedAssetUrl()?.replaceLocalhostWithConfiguredHost() ?: this.replaceLocalhostWithConfiguredHost()
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
