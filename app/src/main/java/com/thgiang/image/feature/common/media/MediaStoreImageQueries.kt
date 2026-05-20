package com.thgiang.image.feature.common.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns

internal val supportedImageExtensions: Set<String> = setOf(
    "jpg",
    "jpeg",
    "jpe",
    "jfif",
    "pjpeg",
    "pjp",
    "png",
    "webp",
    "heic",
    "heif",
    "gif",
    "bmp",
    "dib",
    "avif",
    "tif",
    "tiff",
    "dng",
    "cr2",
    "nef",
    "nrw",
    "arw",
    "rw2",
    "raf",
    "sr2",
    "pef",
    "orf",
    "x3f",
    "mos",
    "kdc",
    "srw",
    "3fr",
    "iiq",
    "rwl",
    "raw",
    "jp2",
    "j2k",
    "jpf",
    "jpx",
    "jpg"
)

internal val appSupportedImageExtensions: Set<String> = setOf(
    "jpg",
    "jpeg",
    "jpe",
    "jfif",
    "pjpeg",
    "pjp",
    "png",
    "webp",
    "heic",
    "heif",
    "gif",
    "bmp",
    "dib",
    "avif",
    "tif",
    "tiff"
)

internal fun looksLikeSupportedImageFile(displayName: String?): Boolean {
    val name = displayName?.lowercase() ?: return false
    return supportedImageExtensions.any { ext -> name.endsWith(".$ext") }
}

internal suspend fun loadPickerImageUris(context: Context): List<Uri> {
    val list = mutableListOf<Uri>()
    val seen = mutableSetOf<String>()

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext()) {
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cursor.getLong(idCol)
            )
            if (seen.add(uri.toString())) list.add(uri)
        }
    }

    loadDownloadImageUris(context).forEach { uri ->
        if (seen.add(uri.toString())) list.add(uri)
    }

    return list
}

internal suspend fun loadDownloadImageUris(context: Context): List<Uri> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

    val list = mutableListOf<Uri>()
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Downloads._ID,
        MediaStore.Downloads.MIME_TYPE,
        MediaStore.Downloads.DISPLAY_NAME,
        MediaStore.Downloads.DATE_ADDED
    )
    val selection = buildString {
        append("(")
        append("${MediaStore.Downloads.MIME_TYPE} LIKE ?")
        for (i in supportedImageExtensions.indices) {
            append(" OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?")
        }
        append(")")
    }
    val selectionArgs = ArrayList<String>(1 + supportedImageExtensions.size).apply {
        add("image/%")
        supportedImageExtensions.forEach { ext -> add("%.${ext}") }
    }.toTypedArray()

    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Downloads.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                if (looksLikeSupportedImageFile(cursor.getStringOrNullSafe(MediaStore.Downloads.DISPLAY_NAME))) {
                    list.add(uri)
                } else {
                    list.add(uri)
                }
            }
        }
    }

    return list
}

private fun android.database.Cursor.getStringOrNullSafe(columnName: String): String? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
}

internal fun isSupportedByApp(displayName: String?, mimeType: String?): Boolean {
    return true
}

internal fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getString(index)
            } else {
                null
            }
        }
    }.getOrNull()
}

internal fun resolveMimeType(context: Context, uri: Uri): String? {
    return runCatching { context.contentResolver.getType(uri) }.getOrNull()
}
