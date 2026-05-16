package com.abizer_r.quickedit.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun LoadingView(
    modifier: Modifier,
    progressBarSize: Dp = 48.dp,
    progressBarColor: Color = MaterialTheme.colorScheme.onBackground,
    progressBarStrokeWidth: Dp = 4.dp
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("animation/animation.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    Box(modifier = modifier) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .size(progressBarSize * 2.5f)
                .align(Alignment.Center)
        )
    }
}
