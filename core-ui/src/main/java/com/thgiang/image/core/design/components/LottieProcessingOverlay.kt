package com.thgiang.image.core.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Overlay toàn màn hình hiển thị Lottie khi đang xử lý.
 * Không dùng nền trắng/đen che khuất — UI phía sau vẫn nhìn thấy.
 */
@Composable
fun LottieProcessingOverlay(
    isVisible: Boolean,
    animationAssetPath: String = "animation/animation.lottie",
    animationSize: Dp = 120.dp,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset(animationAssetPath),
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = true,
            )
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(animationSize),
            )
            if (label != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}
