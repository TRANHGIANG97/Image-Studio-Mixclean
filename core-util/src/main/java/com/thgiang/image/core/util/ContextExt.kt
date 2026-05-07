package com.thgiang.image.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Extension functions for [Context].
 */

/**
 * Tìm Activity từ Context (nếu có).
 * @return Activity nếu context là Activity hoặc ContextWrapper chứa Activity, ngược lại null.
 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Creates a temporary URI for the Camera to store the captured image.
 */
fun Context.createTempPictureUri(): android.net.Uri {
    val imageFile = java.io.File(cacheDir, "images").apply {
        if (!exists()) mkdirs()
    }
    val tempFile = java.io.File.createTempFile("camera_image_", ".jpg", imageFile)
    return androidx.core.content.FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        tempFile
    )
}