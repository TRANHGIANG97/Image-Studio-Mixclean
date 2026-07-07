@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.panel
import com.thgiang.image.studio.ui.editor.tool.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.label.panel.*

import androidx.compose.animation.AnimatedContent
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.thgiang.image.studio.ui.editor.tool.StickerGallerySheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditorControlsV2(
    tool: EditorTool?,
    appearance: EditorAppearance,
    cropRatio: CropRatio,
    selectedLayer: EditorLayer? = null,
    onUpdateShadow: (Float) -> Unit,
    onUpdateShadowAngle: (Float) -> Unit,
    onUpdateShadowDistance: (Float) -> Unit,
    onUpdateShadowColor: (Int) -> Unit,
    onUpdateShadowBlur: (Float?) -> Unit,
    onUpdateAlpha: (Float) -> Unit,
    onSelectCropRatio: (CropRatio) -> Unit,
    onLayoutEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalEditorTokens.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.White),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = tool,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "ControlsAnimation",
                ) { targetTool ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (targetTool is EditorTool.Label) {
                                    Modifier
                                } else {
                                    Modifier.padding(horizontal = 16.dp)
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (targetTool) {
                            is EditorTool.Rotate -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EditorLayoutControls(onEvent = onLayoutEvent)
                                }
                            }

                            is EditorTool.Sticker -> {
                                var showGallery by rememberSaveable { mutableStateOf(false) }

                                StickerPicker(
                                    onStickerSelected = { url ->
                                        onLayoutEvent(EditorEvent.AddSticker(url))
                                    },
                                    onShowGallery = { showGallery = true },
                                )

                                if (showGallery) {
                                    StickerGallerySheet(
                                        onStickerSelected = { url ->
                                            onLayoutEvent(EditorEvent.AddSticker(url))
                                            showGallery = false
                                        },
                                        onDismiss = { showGallery = false },
                                    )
                                }
                            }

                            is EditorTool.Label -> {
                                LabelShapePanel(
                                    selectedLayer = selectedLayer,
                                    onLayoutEvent = onLayoutEvent,
                                    canvasFirstMode = false,
                                )
                            }

                            is EditorTool.Shape -> {
                                ShapePanel(
                                    selectedLayer = selectedLayer,
                                    onLayoutEvent = onLayoutEvent,
                                )
                            }

                            is EditorTool.Shadow -> {
                                ShadowToolPanel(
                                    appearance = appearance,
                                    onUpdateShadow = onUpdateShadow,
                                    onUpdateShadowAngle = onUpdateShadowAngle,
                                    onUpdateShadowDistance = onUpdateShadowDistance,
                                    onUpdateShadowColor = onUpdateShadowColor,
                                    onUpdateShadowBlur = onUpdateShadowBlur,
                                )
                            }

                            is EditorTool.Transparency -> {
                                var expanded by rememberSaveable { mutableStateOf(true) }
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (expanded) 180f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "chevronRotation"
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { expanded = !expanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.studio_tool_transparency),
                                                color = tokens.textPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                                                contentDescription = null,
                                                tint = tokens.textSecondary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .graphicsLayer { rotationZ = chevronRotation }
                                            )
                                        }
                                        Text(
                                            text = "${(appearance.alpha * 100).roundToInt()}%",
                                            color = tokens.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = expandVertically(animationSpec = tween(200)),
                                        exit = shrinkVertically(animationSpec = tween(180))
                                    ) {
                                        // Slider Row - Chia đôi chiều ngang (nhỏ lại gấp đôi)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(0.5f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "10%",
                                                    color = tokens.textSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                androidx.compose.material3.Slider(
                                                    value = appearance.alpha,
                                                    onValueChange = onUpdateAlpha,
                                                    valueRange = 0.1f..1f,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(24.dp),
                                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                                        thumbColor = Color.White,
                                                        activeTrackColor = tokens.textPrimary.copy(alpha = 0.9f),
                                                        inactiveTrackColor = Color(0xFFE5E7EB)
                                                    )
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "100%",
                                                    color = tokens.textSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Spacer(Modifier.weight(0.5f))
                                        }
                                    }
                                }
                            }

                            is EditorTool.Crop -> {
                                CropToolPanel(
                                    cropRatio = cropRatio,
                                    onSelectCropRatio = onSelectCropRatio,
                                )
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
