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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Overlay toàn màn hình hiển thị spinner tiến trình khi đang xử lý xóa phông.
 *
 * @param isVisible Kiểm soát hiển thị overlay (true = đang xử lý).
 * @param overlayColor Màu nền overlay.
 * @param animationSize Kích thước của vòng xoay tiến trình.
 * @param label Nhãn chữ hiển thị dưới (null = ẩn).
 */
@Composable
fun LottieProcessingOverlay(
    isVisible: Boolean,
    animationAssetPath: String = "animation/animation.lottie",
    overlayColor: Color = Color.Transparent,
    animationSize: Dp = 56.dp,
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = Color(0xFF6366F1),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(animationSize)
                )
                if (label != null) {
                    Spacer(modifier = Modifier.height(16.dp))
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
