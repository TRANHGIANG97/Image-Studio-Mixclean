package com.thgiang.image.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Overlay toàn màn hình hiển thị animation Lottie khi đang xử lý xóa phông.
 *
 * @param isVisible Kiểm soát hiển thị overlay (true = đang xử lý).
 * @param animationAssetPath Đường dẫn tới file JSON trong thư mục assets.
 * @param overlayColor Màu nền overlay (mặc định: đen mờ 70%).
 * @param animationSize Kích thước animation Lottie.
 * @param label Nhãn chữ hiển thị dưới animation (null = ẩn).
 */
@Composable
fun LottieProcessingOverlay(
    isVisible: Boolean,
    animationAssetPath: String = "animation/animation.lottie",
    overlayColor: Color = Color.Transparent,
    animationSize: Dp = 180.dp,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor),
            contentAlignment = Alignment.Center
        ) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset(animationAssetPath)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(animationSize)
                )
                if (label != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
