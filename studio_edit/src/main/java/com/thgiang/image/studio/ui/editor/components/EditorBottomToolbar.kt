package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.thgiang.image.studio.ui.editor.EditorTool
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
fun EditorBottomToolbar(
    selectedTool: EditorTool?,
    onToolSelected: (EditorTool?) -> Unit,
    onReplaceImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current
    val tools = remember { EditorTool.ALL }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
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
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { tool ->
                val isSelected = when (tool) {
                    is EditorTool.Replace -> selectedTool is EditorTool.Replace
                    is EditorTool.Rotate -> selectedTool is EditorTool.Rotate
                    is EditorTool.Shadow -> selectedTool is EditorTool.Shadow
                    is EditorTool.Transparency -> selectedTool is EditorTool.Transparency
                    is EditorTool.Crop -> selectedTool is EditorTool.Crop
                    is EditorTool.Duplicate, is EditorTool.Delete -> false // These are instant actions
                    else -> false
                }
                ToolButton(
                    tool = tool,
                    isSelected = isSelected,
                    accentColor = tokens.accent,
                    accentSoftColor = tokens.accentSoft,
                    primaryColor = tokens.textPrimary,
                    secondaryColor = tokens.textSecondary,
                    onClick = {
                        if (tool is EditorTool.Replace) {
                            onReplaceImage()
                        } else {
                            onToolSelected(tool)
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
    accentColor: Color,
    accentSoftColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    val labelRes = when (tool) {
        is EditorTool.Replace      -> R.string.studio_tool_replace
        is EditorTool.Layout       -> R.string.studio_tool_layout
        is EditorTool.Rotate       -> R.string.studio_tool_rotateflip
        is EditorTool.Shadow       -> R.string.studio_tool_shadow
        is EditorTool.Transparency -> R.string.studio_tool_transparency
        is EditorTool.Crop         -> R.string.studio_tool_crop
        is EditorTool.Duplicate    -> R.string.studio_tool_duplicate
        is EditorTool.Delete       -> R.string.studio_tool_delete
        else                       -> R.string.studio_tool_layout
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    val iconRes = when (tool) {
        is EditorTool.Replace      -> R.drawable.ic_tool_replace_image
        is EditorTool.Layout       -> R.drawable.ic_tool_layout
        is EditorTool.Rotate       -> R.drawable.ic_tool_rotate_flip
        is EditorTool.Shadow       -> R.drawable.ic_tool_shadow
        is EditorTool.Transparency -> R.drawable.ic_tool_opacity
        is EditorTool.Crop         -> R.drawable.ic_tool_crop
        is EditorTool.Duplicate    -> R.drawable.ic_tool_duplicate
        is EditorTool.Delete       -> R.drawable.ic_tool_delete
        else                       -> R.drawable.ic_tool_layout
    }

    // Subtle spring scale animation on select
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "toolScale"
    )

    val iconTint  = if (isSelected) accentColor  else secondaryColor
    val labelColor = if (isSelected) accentColor  else secondaryColor

    // Pill-shaped container for selected state
    Box(
        modifier = Modifier
            .widthIn(min = 64.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) accentSoftColor else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
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
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = labelColor,
                maxLines = 1
            )
        }
    }
}
