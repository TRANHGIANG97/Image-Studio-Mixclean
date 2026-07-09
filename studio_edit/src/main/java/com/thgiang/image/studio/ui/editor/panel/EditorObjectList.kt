package com.thgiang.image.studio.ui.editor.panel
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.roundToInt

import androidx.compose.animation.AnimatedVisibility
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.thgiang.image.studio.ui.editor.theme.MotionTokens

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
        enter = slideInVertically(animationSpec = MotionTokens.springPanel()) { it } + fadeIn(MotionTokens.fadeDefault),
        exit = slideOutVertically(animationSpec = MotionTokens.springPanel()) { it } + fadeOut(MotionTokens.fadeQuick),
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
                enter = expandVertically(animationSpec = MotionTokens.springPanel()),
                exit = shrinkVertically(animationSpec = MotionTokens.springPanel())
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
                    layers.filter { it.shouldShowInObjectList() }.forEachIndexed { _, layer ->
                        val isSample = layer.product.isSample
                        val displayIndex = if (!isSample) {
                            nonSampleIndex++
                            nonSampleIndex
                        } else {
                            -1 // mẫu
                        }

                        ObjectLayerCard(
                            layer = layer,
                            isSelected = layers.isSelectedAsGroup(selectedLayerId, layer.id),
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
        animationSpec = MotionTokens.springEmphasized(),
        label = "cardScale"
    )

    val thumbnailModel = remember(layer.product) {
        layer.product.foregroundUriString ?: layer.product.originalUriString
    }

    // Tên hiển thị
    val labelText = when {
        layer.isVectorContentLayer -> {
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
                layer.isVectorContentLayer -> {
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
            if (layer.isVectorContentLayer || layer.type == LayerType.SHADOW_REGION) {
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

@Composable
fun LayersIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF374151)
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val sizeDp = size.width
        val cardWidth = sizeDp * 0.65f
        val cardHeight = sizeDp * 0.65f
        
        // Draw bottom card
        drawRoundRect(
            color = tint.copy(alpha = 0.4f),
            topLeft = Offset(sizeDp * 0.35f, sizeDp * 0.35f),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
        
        // Draw top card
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Fill top card content slightly
        drawRoundRect(
            color = tint.copy(alpha = 0.12f),
            topLeft = Offset(0f, 0f),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }
}



@Composable
fun EditorObjectListVertical(
    layers: List<EditorLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    layersOffset: Offset,
    onLayersOffsetChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current
    val density = LocalDensity.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val currentLayersOffset by rememberUpdatedState(layersOffset)
    val currentOnLayersOffsetChange by rememberUpdatedState(onLayersOffsetChange)

    var nonSampleIndex = 0

    Column(
        modifier = modifier
            .wrapContentSize()
            .offset { IntOffset(layersOffset.x.roundToInt(), layersOffset.y.roundToInt()) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Vertical stack of layers (shown when expanded)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(MotionTokens.fadeDefault) + expandVertically(MotionTokens.springPanel()),
            exit = fadeOut(MotionTokens.fadeQuick) + shrinkVertically(MotionTokens.springPanel())
        ) {
            Box(
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    layers.filter { it.shouldShowInObjectList() }.forEach { layer ->
                        val isSample = layer.product.isSample
                        val displayIndex = if (!isSample) {
                            nonSampleIndex++
                            nonSampleIndex
                        } else {
                            -1
                        }

                        ObjectLayerCardCompact(
                            layer = layer,
                            isSelected = layers.isSelectedAsGroup(selectedLayerId, layer.id),
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

        // Toggle Button at the bottom (Handles both Tap to toggle and Drag to move entire widget)
        val toggleAlpha = if (expanded) 1f else 0.5f
        var totalDrag by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer { alpha = toggleAlpha }
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.15f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .background(Color.White, CircleShape)
                .border(
                    width = 1.dp,
                    color = if (expanded) tokens.accent.copy(alpha = 0.5f) else Color(0xFFE5E7EB),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { totalDrag = Offset.Zero },
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            currentOnLayersOffsetChange(
                                Offset(
                                    x = currentLayersOffset.x + dragAmount.x,
                                    y = currentLayersOffset.y + dragAmount.y
                                )
                            )
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    expanded = !expanded
                },
            contentAlignment = Alignment.Center
        ) {
            LayersIcon(
                modifier = Modifier.size(20.dp),
                tint = if (expanded) tokens.accent else tokens.textSecondary
            )
        }
    }
}

@Composable
private fun ObjectLayerCardCompact(
    layer: EditorLayer,
    isSelected: Boolean,
    displayIndex: Int,
    accentColor: Color,
    accentSoftColor: Color,
    surfaceElevatedColor: Color,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = MotionTokens.springEmphasized(),
        label = "cardScale"
    )

    val thumbnailModel = remember(layer.product) {
        layer.product.foregroundUriString ?: layer.product.originalUriString
    }

    val labelText = when {
        layer.isVectorContentLayer -> {
            val preview = EditorTextStyleMapper.applyTextTransform(
                layer.text.trim(),
                layer.textTransform,
            ).ifBlank { stringResource(R.string.studio_layer_text_label) }
            stringResource(R.string.studio_layer_text_with_content, preview.take(8))
        }
        layer.type == LayerType.SHADOW_REGION -> "Bóng"
        displayIndex == -1 -> stringResource(R.string.studio_badge_sample)
        else -> stringResource(R.string.studio_layer_image_label, displayIndex)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label Text on the left
        Box(
            modifier = Modifier
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                .border(width = 0.5.dp, color = Color(0xFFE0E0E0), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = labelText,
                color = if (isSelected) accentColor else Color(0xFF4B5563),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

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
                        colors = listOf(accentColor, accentColor.copy(alpha = 0.6f))
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
            layer.product.processing -> {
                StudioLottieLoader(
                    modifier = Modifier
                        .size(30.dp)
                        .align(Alignment.Center)
                )
            }
            layer.isVectorContentLayer -> {
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
            thumbnailModel != null -> {
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

        // Badge góc trên bên trái
        if (layer.isVectorContentLayer || layer.type == LayerType.SHADOW_REGION) {
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

        // Checkmark indicator góc dưới phải
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

        // Locked badge
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
}
}
