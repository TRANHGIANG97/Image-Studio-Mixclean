package com.abizer_r.quickedit.ui.mainScreen

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.abizer_r.quickedit.utils.FileUtils
import com.abizer_r.quickedit.utils.other.bitmap.BitmapStatus
import com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onImageSelected: (Bitmap) -> Unit,
    initialImageUri: Uri? = null,
) {
    val context = LocalContext.current

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var scaledBitmapStatus by remember { mutableStateOf<BitmapStatus>(BitmapStatus.None) }

    val cameraImageUri = remember {
        val imgFile = File(context.filesDir, "camera_photo.jpg")
        FileUtils.getUriForFile(context, imgFile)
    }

    val onPhotoPicked = remember<(Uri?) -> Unit> {
        {
            imageUri = it
        }
    }

    val onPhotoCaptured = remember<(Uri?) -> Unit> {
        {
            imageUri = it
        }
    }

    LaunchedEffect(initialImageUri) {
        initialImageUri?.let { imgUri ->
            withContext(Dispatchers.Default) {
                BitmapUtils.getScaledBitmap(context, imgUri).onEach {
                    scaledBitmapStatus = it
                }.collect()
            }
        }
    }

    LaunchedEffect(key1 = imageUri) {
        imageUri?.let { imgUri ->
            withContext(Dispatchers.Default) {
                BitmapUtils.getScaledBitmap(context, imgUri).onEach {
                    scaledBitmapStatus = it
                }.collect()
            }
        }
    }

    MainScreenLayout(
        modifier = modifier,
        scaledBitmapStatus = scaledBitmapStatus,
        cameraImageUri = cameraImageUri,
        onPhotoPicked = onPhotoPicked,
        onPhotoCaptured = onPhotoCaptured,
        onImageSelected = onImageSelected
    )
}
