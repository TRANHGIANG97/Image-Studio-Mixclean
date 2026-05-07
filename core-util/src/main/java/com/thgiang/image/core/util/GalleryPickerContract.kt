package com.thgiang.image.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/**
 * Custom contract specifically designed to prompt an App Chooser for Gallery apps.
 * Unlike GetContent() which often defaults to the System File Manager (DocumentProvider),
 * ACTION_PICK directly targets applications that manage media (like Gallery, Photos, Camera).
 */
class GalleryPickerContract : ActivityResultContract<Uri, Uri?>() {
    private var cameraUri: Uri? = null

    override fun createIntent(context: Context, input: Uri): Intent {
        cameraUri = input

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            // Add flags to grant read/write URI permissions to the camera app
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }

        return Intent.createChooser(pickIntent, "Chọn hình ảnh").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data ?: cameraUri
    }
}
