package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorLayer
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

/**
 * Thanh danh sách đối tượng nằm ngang, hỗ trợ vuốt trái/phải.
 * Hiển thị thumbnail + trạng thái chọn rõ ràng bằng viền xanh.
 * Chỉ hiển thị khi có ít nhất 1 layer (kể cả layer đang xử lý).
 */
@Composable
fun EditorObjectList(
    layers: List<EditorLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current
    val scrollState = rememberScrollState()

    // Đánh số thứ tự chỉ cho các layer không phải mẫu
    var nonSampleIndex = 0

    AnimatedVisibility(
        visible = layers.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.glassBackground)
                .drawBehind {
                    // Đường viền trên
                    drawRect(
                        color = Color(0xFFE0E0E0),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 1.dp.toPx())
                    )
                }
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                layers.forEachIndexed { _, layer ->
                    val isSample = layer.product.isSample
                    val displayIndex = if (!isSample) {
                        nonSampleIndex++
                        nonSampleIndex
                    } else {
                        -1 // mẫu
                    }

                    ObjectLayerCard(
                        layer = layer,
                        isSelected = layer.id == selectedLayerId,
                        displayIndex = displayIndex,
                        accentColor = tokens.accent,
                        accentSoftColor = tokens.accentSoft,
                        surfaceElevatedColor = tokens.surfaceElevated,
                        onSelect = { onSelectLayer(layer.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectLayerCard(
    layer: EditorLayer,
    isSelected: Boolean,
    displayIndex: Int,         // -1 = mẫu, 1..n = sản phẩm
    accentColor: Color,
    accentSoftColor: Color,
    surfaceElevatedColor: Color,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    val thumbnailModel = remember(layer.product) {
        layer.product.foregroundUriString ?: layer.product.originalUriString
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) accentSoftColor else surfaceElevatedColor
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = accentColor,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Đang xử lý tách nền
            layer.product.processing -> {
                StudioLottieLoader(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }

            // Có ảnh để hiển thị
            thumbnailModel != null -> {
                AsyncImage(
                    model = thumbnailModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            // Không có ảnh — placeholder mờ
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(0xFFDEE2E8),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            }
        }

        // Badge góc trên bên trái: "Mẫu" hoặc số thứ tự
        val badgeText = if (displayIndex == -1) stringResource(R.string.studio_badge_sample) else "$displayIndex"
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(
                    color = if (isSelected) accentColor else Color(0x99000000),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = badgeText,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
            )
        }

        // Checkmark badge góc trên bên phải khi được chọn
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(accentColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                EditorCheckIcon(
                    modifier = Modifier.size(10.dp),
                    tint = Color.White
                )
            }
        }
    }
}
