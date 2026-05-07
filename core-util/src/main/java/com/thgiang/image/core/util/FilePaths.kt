package com.thgiang.image.core.util
import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore.Images.Media.DISPLAY_NAME
import android.provider.MediaStore.Images.Media.RELATIVE_PATH

/**
 * Returns display name for the URI, or a safe fallback so that callers never get null
 * (avoids Parcel "Reading a NULL string not supported here" when values are serialized).
 */
internal fun fileName(contentResolver: ContentResolver, uri: Uri): String {
    val projection = arrayOf(DISPLAY_NAME)
    val cursor = contentResolver.query(uri, projection, null, null, null) ?: return fallbackFileName()
    cursor.use {
        if (!cursor.moveToFirst()) return fallbackFileName()
        return cursor.getString(0)?.takeIf { it.isNotBlank() } ?: fallbackFileName()
    }
}

private fun fallbackFileName(): String = "image_${System.currentTimeMillis()}.png"

internal fun filePath(contentResolver: ContentResolver, uri: Uri): String? {
    val projection = arrayOf(RELATIVE_PATH, DISPLAY_NAME)
    val cursor = contentResolver.query(uri, projection, null, null, null) ?: return null
    cursor.use {
        val relativePathIndex = cursor.getColumnIndex(RELATIVE_PATH)
        if (relativePathIndex == -1) return null
        val displayNameIndex = cursor.getColumnIndex(DISPLAY_NAME)
        if (displayNameIndex == -1) return null
        if (!cursor.moveToFirst()) return null
        val relativePath = cursor.getString(relativePathIndex)
        val displayName = cursor.getString(displayNameIndex)
        return "$relativePath$displayName"
    }
}





