package com.thgiang.image.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/**
 * Custom contract specifically designed to prompt an App Chooser for Gallery apps
 * for selecting MULTIPLE images, without the Camera option.
 */
class MultiGalleryPickerContract : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        
        return Intent.createChooser(pickIntent, "Chọn nhiều hình ảnh")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return emptyList()
        }
        
        val uris = mutableListOf<Uri>()
        
        // Check ClipData for multiple selections
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
        } 
        // Fallback to data if only one was selected natively
        else {
            intent.data?.let { uris.add(it) }
        }
        
        return uris
    }
}
