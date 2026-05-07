package com.thgiang.image.feature.home.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberImagePicker(
    imageOnly: Boolean = true,
    onImagePicked: (Uri?) -> Unit
): () -> Unit {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onImagePicked(uri)
    }

    return remember {
        {
            pickerLauncher.launch(
                PickVisualMediaRequest(
                    mediaType = if (imageOnly) {
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    } else {
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    }
                )
            )
        }
    }
}
