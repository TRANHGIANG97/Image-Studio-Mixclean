package com.thgiang.image.feature.editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.thgiang.image.R
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun RemoveBgScreen(
    bitmap: Bitmap,
    backgroundRemoverRepository: BackgroundRemoverRepository,
    onBackPressed: () -> Unit,
    onDoneClicked: (Bitmap) -> Unit
) {
    var processing by remember { mutableStateOf(true) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        processing = true
        val result = withContext(Dispatchers.Default) {
            backgroundRemoverRepository.getForegroundBitmap(bitmap)
        }
        result.fold(
            onSuccess = { fg ->
                resultBitmap = fg
                processing = false
                // Automatically done for a seamless experience
                onDoneClicked(fg)
            },
            onFailure = { e ->
                errorMessage = e.message ?: "Background removal failed"
                processing = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Show current state image
        val displayBitmap = resultBitmap ?: bitmap
        Image(
            bitmap = displayBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        if (processing) {
            val composition by rememberLottieComposition(LottieCompositionSpec.Asset("animation/animation.lottie"))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(300.dp)
                )
            }
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = onBackPressed) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }

    }
}
