package com.abizer_r.quickedit.ui.mainScreen

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.theme.QuickEditTheme
import com.abizer_r.quickedit.ui.common.AppIconWithName
import com.abizer_r.quickedit.ui.common.ErrorView
import com.abizer_r.quickedit.utils.other.bitmap.BitmapStatus

@Composable
fun MainScreenLayout(
    modifier: Modifier = Modifier,
    scaledBitmapStatus: BitmapStatus,
    cameraImageUri: Uri? = null,
    onPhotoPicked: (Uri?) -> Unit = {},
    onPhotoCaptured: (Uri?) -> Unit = {},
    onImageSelected: (Bitmap) -> Unit = {}
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconWithName(Modifier.padding(vertical = 64.dp))

        when (scaledBitmapStatus) {
            BitmapStatus.None -> MainScreenButtonsLayout(
                cameraImageUri = cameraImageUri,
                onPhotoPicked = onPhotoPicked,
                onPhotoCaptured = onPhotoCaptured
            )

            BitmapStatus.Processing -> MainScreenButtonsLayout(
                cameraImageUri = cameraImageUri,
                onPhotoPicked = onPhotoPicked,
                onPhotoCaptured = onPhotoCaptured,
                showLoading = true
            )

            is BitmapStatus.Failed -> MainScreenErrorView(
                cameraImageUri = cameraImageUri,
                errorText = scaledBitmapStatus.errorMsg ?: scaledBitmapStatus.exception?.message,
                onPhotoPicked = onPhotoPicked,
                onPhotoCaptured = onPhotoCaptured
            )


            is BitmapStatus.Success -> {
                onImageSelected(scaledBitmapStatus.scaledBitmap)
                // show layout to avoid showing blank screen while transition animation is played
                MainScreenButtonsLayout(
                    cameraImageUri = cameraImageUri,
                    onPhotoPicked = onPhotoPicked,
                    onPhotoCaptured = onPhotoCaptured,
                )
            }
        }
    }

}

@Composable
private fun MainScreenErrorView(
    cameraImageUri: Uri?,
    errorText: String?,
    onPhotoPicked: (Uri?) -> Unit,
    onPhotoCaptured: (Uri?) -> Unit
) {
    MainScreenButtonsLayout(
        modifier = Modifier.fillMaxWidth(),
        cameraImageUri = cameraImageUri,
        onPhotoPicked = onPhotoPicked,
        onPhotoCaptured = onPhotoCaptured,
    )
    ErrorView(
        modifier = Modifier.fillMaxWidth(),
        errorText = errorText ?: stringResource(R.string.something_went_wrong)
    )
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDefault() {
    QuickEditTheme {
        MainScreenLayout(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            scaledBitmapStatus = BitmapStatus.None
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewProcessing() {
    QuickEditTheme {
        MainScreenLayout(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            scaledBitmapStatus = BitmapStatus.Processing
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFailed() {
    QuickEditTheme {
        MainScreenLayout(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            scaledBitmapStatus = BitmapStatus.Failed()
        )
    }
}
