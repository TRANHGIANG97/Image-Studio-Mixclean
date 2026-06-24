package com.thgiang.image.studio.ui.editor.panel
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*

import androidx.compose.animation.AnimatedVisibility
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

/**
 * Layer Strip (formerly "minimap") — cải tiến toàn diện:
 * - Nền checkerboard cho ảnh PNG trong suốt
 * - Glow effect khi được chọn
 * - Label tên layer ở dưới thumbnail
 * - Animation mượt hơn
 * - Card size 80dp để thumbnail rõ hơn
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
    var expanded by rememberSaveable { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    // Đánh số thứ tự chỉ cho các layer không phải mẫu
    var nonSampleIndex = 0

    AnimatedVisibility(
        visible = layers.isNotEmpty(),
        enter = slideInVertically(animationSpec = tween(250)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.glassBackground)
                .drawBehind {
                    // Top divider
                    drawRect(
                        color = Color(0xFFE0E0E0),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 0.8.dp.toPx())
                    )
                }
        ) {
            // Header: "Đối tượng" + đếm + nút ẩn/hiện
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.studio_objects_label),
                        color = tokens.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    // Đếm số layer
                    Text(
                        text = "${layers.size}",
                        color = tokens.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(180))
            ) {
                // Layer strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    val thumbnailModel = remember(layer.product) {
        layer.product.foregroundUriString ?: layer.product.originalUriString
    }

    // Tên hiển thị
    val labelText = when {
        layer.type == LayerType.SHAPE_TEXT -> {
            val preview = EditorTextStyleMapper.applyTextTransform(
                layer.text.trim(),
                layer.textTransform,
            ).ifBlank { stringResource(R.string.studio_layer_text_label) }
            stringResource(R.string.studio_layer_text_with_content, preview.take(14))
        }
        layer.type == LayerType.SHADOW_REGION -> "Bóng"
        displayIndex == -1 -> stringResource(R.string.studio_badge_sample)
        else -> stringResource(R.string.studio_layer_image_label, displayIndex)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(50.dp)
    ) {
        // Card
        Box(
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (isSelected) Modifier.shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = accentColor.copy(alpha = 0.4f),
                        spotColor = accentColor.copy(alpha = 0.6f)
                    ) else Modifier
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) accentSoftColor else surfaceElevatedColor
                )
                .then(
                    if (isSelected) Modifier.border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.6f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) else Modifier.border(
                        width = 1.dp,
                        color = Color(0xFFE5E7EB),
                        shape = RoundedCornerShape(8.dp)
                    )
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
                            .size(30.dp)
                            .align(Alignment.Center)
                    )
                }

                // SHAPE_TEXT — mini shape + text preview
                layer.type == LayerType.SHAPE_TEXT -> {
                    val shapeColor = Color(layer.shapeColorArgb)
                    val textColor = Color(layer.textColorArgb)
                    val backgroundBrush = if (layer.fillGradient != null) {
                        EditorGradientMapper.toComposeBrush(
                            gradient = layer.fillGradient,
                            width = 80f,
                            height = 50f,
                            fallbackColor = shapeColor,
                        )
                    } else {
                        Brush.linearGradient(listOf(shapeColor.copy(alpha = 0.18f), shapeColor.copy(alpha = 0.18f)))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundBrush)
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val isShapeInvisible = layer.shapeType == ShapeType.TEXT_ONLY ||
                            layer.shapeColorArgb == 0 ||
                            (layer.shapeColorArgb and 0xFFFFFF) == 0xFFFFFF
                        if (!isShapeInvisible) {
                            ShapePreviewIcon(
                                shape = layer.shapeType,
                                color = shapeColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        val isTextInvisible = layer.textColorArgb == 0 || (layer.textColorArgb and 0xFFFFFF) == 0xFFFFFF
                        Text(
                            text = EditorTextStyleMapper
                                .applyTextTransform(layer.text, layer.textTransform)
                                .ifBlank { "…" }
                                .take(10),
                            color = if (isTextInvisible) Color(0xFF374151) else textColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Có ảnh để hiển thị
                thumbnailModel != null -> {
                    // Nền checkerboard để ảnh PNG trong suốt không bị đen
                    val checker = rememberCheckerboardBrush()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(checker)
                    ) {
                        AsyncImage(
                            model = thumbnailModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Không có ảnh — placeholder
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFE8ECF0), Color(0xFFD1D5DB))
                                )
                            )
                    )
                }
            }

            // Badge góc trên bên trái: layer type icon/text
            if (layer.type == LayerType.SHAPE_TEXT || layer.type == LayerType.SHADOW_REGION) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(12.dp)
                        .background(
                            color = if (isSelected) accentColor else Color(0xCC000000),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (layer.type == LayerType.SHADOW_REGION) "S" else "T",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Checkmark / selected indicator góc dưới phải
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(12.dp)
                        .background(accentColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    EditorCheckIcon(
                        modifier = Modifier.size(7.dp),
                        tint = Color.White
                    )
                }
            }

            // Processing overlay
            if (layer.isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(10.dp)
                        .background(Color(0xCC666666), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 6.sp)
                }
            }
        }

        // Label dưới thumbnail
        Spacer(Modifier.height(1.dp))
        Text(
            text = labelText,
            color = if (isSelected) accentColor else Color(0xFF6B7280),
            fontSize = 8.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
