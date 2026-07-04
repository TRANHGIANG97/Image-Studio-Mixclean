package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*

import androidx.compose.foundation.background
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerPickerSheet(
    hitLayers: List<EditorLayer>,
    allLayers: List<EditorLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalEditorTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayIndices = remember(allLayers) { buildLayerDisplayIndices(allLayers) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.glassBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.studio_layer_picker_title),
                color = tokens.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.studio_layer_picker_subtitle, hitLayers.size),
                color = tokens.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(hitLayers, key = { _, layer -> layer.id }) { index, layer ->
                    val displayIndex = displayIndices[layer.id] ?: 0
                    val isSelected = layer.id == selectedLayerId
                    LayerPickerRow(
                        layer = layer,
                        displayIndex = displayIndex,
                        stackHint = if (index == 0) {
                            stringResource(R.string.studio_layer_picker_topmost)
                        } else {
                            null
                        },
                        isSelected = isSelected,
                        onClick = { onSelectLayer(layer.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerPickerRow(
    layer: EditorLayer,
    displayIndex: Int,
    stackHint: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalEditorTokens.current
    val label = layerPickerLabel(layer, displayIndex)
    val thumbnailModel = remember(layer.product) {
        layer.product.foregroundUriString ?: layer.product.originalUriString
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) tokens.accentSoft else Color.Transparent,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            when {
                layer.isVectorContentLayer -> {
                    val shapeColor = Color(layer.shapeColorArgb)
                    val textColor = Color(layer.textColorArgb)
                    val backgroundBrush = if (layer.fillGradient != null) {
                        EditorGradientMapper.toComposeBrush(
                            gradient = layer.fillGradient,
                            width = 44f,
                            height = 44f,
                            fallbackColor = shapeColor,
                        )
                    } else {
                        Brush.linearGradient(listOf(shapeColor.copy(alpha = 0.18f), shapeColor.copy(alpha = 0.18f)))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundBrush)
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ShapePreviewIcon(
                            shape = layer.shapeType,
                            color = shapeColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = EditorTextStyleMapper
                                .applyTextTransform(layer.text, layer.textTransform)
                                .ifBlank { "…" }
                                .take(6),
                            color = textColor,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                thumbnailModel != null -> {
                    AsyncImage(
                        model = thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                else -> {
                    Text("…", fontSize = 12.sp, color = tokens.textSecondary)
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = tokens.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stackHint != null) {
                Text(
                    text = stackHint,
                    color = tokens.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun layerPickerLabel(layer: EditorLayer, displayIndex: Int): String {
    return when {
        layer.isVectorContentLayer -> {
            val preview = EditorTextStyleMapper.applyTextTransform(
                layer.text.trim(),
                layer.textTransform,
            ).ifBlank { stringResource(R.string.studio_layer_text_label) }
            stringResource(R.string.studio_layer_text_with_content, preview.take(20))
        }

        layer.type == LayerType.SHADOW_REGION -> stringResource(R.string.studio_layer_shadow_label)

        displayIndex == -1 -> stringResource(R.string.studio_badge_sample)

        else -> stringResource(R.string.studio_layer_image_label, displayIndex)
    }
}

private fun buildLayerDisplayIndices(layers: List<EditorLayer>): Map<String, Int> {
    var nonSampleIndex = 0
    return buildMap {
        layers.forEach { layer ->
            val index = if (!layer.product.isSample) {
                nonSampleIndex++
                nonSampleIndex
            } else {
                -1
            }
            put(layer.id, index)
        }
    }
}
