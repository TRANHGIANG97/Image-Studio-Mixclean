package com.thgiang.image.studio.ui.editor.panel
import com.thgiang.image.studio.ui.editor.panel.*

import androidx.compose.animation.core.animateFloatAsState
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.studio.ui.editor.theme.MotionTokens

@Composable
fun EditorBottomToolbar(
    selectedTool: EditorTool?,
    onToolSelected: (EditorTool?) -> Unit,
    onReplaceImage: () -> Unit,
    onAddImage: () -> Unit = {},
    onRemoveBg: () -> Unit = {},
    canReplaceImage: Boolean = true,
    canRemoveBg: Boolean = false,
    toolsLocked: Boolean = false,
    labelLayerActive: Boolean = false,
    shapeShadowInPanel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current
    val tools = remember(selectedTool, shapeShadowInPanel) {
        EditorTool.ALL.filter { tool ->
            !(tool is EditorTool.Shadow && (selectedTool is EditorTool.Shape || shapeShadowInPanel))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // White background — premium clean
            .background(Color.White)
            .drawBehind {
                // Top divider border
                drawRect(
                    color = Color(0xFFE0E0E0),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 1.dp.toPx())
                )
            }
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { tool ->
                val isSelected = when (tool) {
                    is EditorTool.Replace -> selectedTool is EditorTool.Replace
                    is EditorTool.Sticker -> selectedTool is EditorTool.Sticker
                    is EditorTool.Background -> selectedTool is EditorTool.Background
                    is EditorTool.Label -> selectedTool is EditorTool.Label
                    is EditorTool.Shape -> selectedTool is EditorTool.Shape
                    is EditorTool.Rotate -> selectedTool is EditorTool.Rotate
                    is EditorTool.Shadow -> selectedTool is EditorTool.Shadow
                    is EditorTool.Transparency -> selectedTool is EditorTool.Transparency
                    is EditorTool.Crop -> selectedTool is EditorTool.Crop
                    is EditorTool.Duplicate, is EditorTool.Delete, is EditorTool.AddImage, is EditorTool.RemoveBg -> false // These are instant actions
                    else -> false
                }
                val isEnabled = when {
                    tool is EditorTool.Replace -> canReplaceImage
                    tool is EditorTool.AddImage -> true
                    tool is EditorTool.RemoveBg -> canRemoveBg
                    tool is EditorTool.Sticker ||
                        tool is EditorTool.Background ||
                        tool is EditorTool.Label ||
                        tool is EditorTool.Shape -> true
                    tool is EditorTool.Crop -> !toolsLocked && !labelLayerActive
                    labelLayerActive -> true
                    else -> !toolsLocked
                }
                ToolButton(
                    tool = tool,
                    isSelected = isSelected,
                    enabled = isEnabled,
                    accentColor = tokens.accent,
                    accentSoftColor = tokens.accentSoft,
                    primaryColor = tokens.textPrimary,
                    secondaryColor = tokens.textSecondary,
                    onClick = {
                        when (tool) {
                            is EditorTool.Replace -> onReplaceImage()
                            is EditorTool.AddImage -> onAddImage()
                            is EditorTool.RemoveBg -> onRemoveBg()
                            else -> onToolSelected(tool)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: EditorTool,
    isSelected: Boolean,
    enabled: Boolean,
    accentColor: Color,
    accentSoftColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    val labelRes = when (tool) {
        is EditorTool.Replace      -> R.string.studio_tool_replace
        is EditorTool.Sticker      -> R.string.studio_tool_sticker
        is EditorTool.Background   -> R.string.studio_tool_background
        is EditorTool.Label        -> R.string.studio_tool_label
        is EditorTool.Shape        -> R.string.studio_tool_shape
        is EditorTool.Layout       -> R.string.studio_tool_layout
        is EditorTool.Rotate       -> R.string.studio_tool_rotateflip
        is EditorTool.Shadow       -> R.string.studio_tool_shadow
        is EditorTool.Transparency -> R.string.studio_tool_transparency
        is EditorTool.Crop         -> R.string.studio_tool_crop
        is EditorTool.Duplicate    -> R.string.studio_tool_duplicate
        is EditorTool.Delete       -> R.string.studio_tool_delete
        is EditorTool.AddImage     -> R.string.studio_tool_add_image
        is EditorTool.RemoveBg     -> R.string.studio_tool_remove_bg
        else                       -> R.string.studio_tool_layout
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    val iconRes = when (tool) {
        is EditorTool.Replace      -> R.drawable.ic_tool_replace_image
        is EditorTool.Sticker      -> R.drawable.ic_tool_sticker
        is EditorTool.Background   -> R.drawable.ic_tool_background
        is EditorTool.Label        -> R.drawable.ic_tool_label
        is EditorTool.Shape        -> R.drawable.ic_tool_sticker
        is EditorTool.Layout       -> R.drawable.ic_tool_layout
        is EditorTool.Rotate       -> R.drawable.ic_tool_rotate_flip
        is EditorTool.Shadow       -> R.drawable.ic_tool_shadow
        is EditorTool.Transparency -> R.drawable.ic_tool_opacity
        is EditorTool.Crop         -> R.drawable.ic_tool_crop
        is EditorTool.Duplicate    -> R.drawable.ic_tool_duplicate
        is EditorTool.Delete       -> R.drawable.ic_tool_delete
        is EditorTool.AddImage     -> R.drawable.ic_tool_add_image
        is EditorTool.RemoveBg     -> R.drawable.ic_tool_remove_bg
        else                       -> R.drawable.ic_tool_layout
    }

    val baseScale = 1.0f
    val scale by animateFloatAsState(
        targetValue = if (isSelected) baseScale * 1.08f else baseScale,
        animationSpec = MotionTokens.springEmphasized(),
        label = "toolScale"
    )

    // Press feedback — subtle scale-down while pressed, springs back on release
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MotionTokens.springEmphasized(),
        label = "toolPressScale"
    )

    val iconTint = when {
        !enabled -> secondaryColor
        isSelected -> accentColor
        else -> primaryColor
    }
    val labelColor = when {
        !enabled -> secondaryColor
        isSelected -> accentColor
        else -> primaryColor
    }
    val containerAlpha = if (enabled) 1f else 0.38f

    // Pill-shaped container for selected state
    Box(
        modifier = Modifier
            .widthIn(min = 60.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) accentSoftColor else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer {
                    scaleX = scale * pressScale
                    scaleY = scale * pressScale
                    alpha = containerAlpha
                }
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = labelColor,
                maxLines = 1
            )
        }
    }
}
