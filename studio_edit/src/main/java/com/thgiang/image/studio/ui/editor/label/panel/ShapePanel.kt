@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

/**
 * Panel cho công cụ Shape — thêm hình dạng trang trí độc lập với Label.
 *
 * Copy đầy đủ chức năng "hình dạng" từ Label cũ:
 * - Creation: shape gallery + confirm (không text, không auto-fit)
 * - Editing: LabelEditSection đầy đủ để chỉnh sửa thuộc tính shape
 */
@Composable
fun ShapePanel(
    selectedLayer: EditorLayer? = null,
    onLayoutEvent: (EditorEvent) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var pendingCreateShape by rememberSaveable { mutableStateOf<ShapeType?>(null) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "shapeChevronRotation"
    )

    val isShapeLayer = selectedLayer?.type == LayerType.SHAPE_TEXT

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.studio_tool_shape),
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }

            LabelConfirmButton(
                onClick = {
                    if (isShapeLayer) {
                        onLayoutEvent(EditorEvent.DismissShapeTool)
                    } else {
                        onLayoutEvent(
                            EditorEvent.ConfirmAddShape(
                                pendingCreateShape ?: ShapeType.PILL,
                            ),
                        )
                        pendingCreateShape = null
                    }
                },
                tokens = tokens,
                enabled = isShapeLayer || pendingCreateShape != null,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(180)),
        ) {
            if (isShapeLayer) {
                // Editing mode — full editing panel (same as Label tool's edit section)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabelEditSection(
                        layer = selectedLayer!!,
                        tokens = tokens,
                        onLayoutEvent = onLayoutEvent,
                        tabOrder = labelShapeFirstTabs,
                    )
                }
            } else {
                // Creation mode — shape gallery
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    LabelCreateSection(
                        pendingShape = pendingCreateShape,
                        tokens = tokens,
                        onPendingShapeChange = { pendingCreateShape = it },
                    )
                }
            }
        }
    }
}
