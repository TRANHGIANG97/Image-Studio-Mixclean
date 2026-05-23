package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorTool

@Composable
fun EditorBottomToolbar(
    selectedTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    onReplaceImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = remember {
        EditorTool.ALL
    }

    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = Color(0xFF131418),
        modifier = modifier
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { tool ->
                val isSelected = selectedTool.javaClass == tool.javaClass
                ToolButton(
                    tool = tool,
                    isSelected = isSelected,
                    onClick = {
                        if (tool is EditorTool.Replace) onReplaceImage()
                        else onToolSelected(tool)
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
    onClick: () -> Unit
) {
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    val labelRes = when (tool) {
        is EditorTool.Replace -> R.string.studio_tool_replace
        is EditorTool.Layout -> R.string.studio_tool_layout
        is EditorTool.Rotate -> R.string.studio_tool_rotateflip
        is EditorTool.Shadow -> R.string.studio_tool_shadow
        is EditorTool.Transparency -> R.string.studio_tool_transparency
        is EditorTool.Crop -> R.string.studio_tool_crop
        else -> R.string.studio_tool_layout
    }

    val accent = Color(0xFF387BFF)
    val iconColor = if (isSelected) accent else Color(0xFF7B8187)
    val labelColor = if (isSelected) accent else Color(0xFF7B8187)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        val iconRes = when (tool) {
            is EditorTool.Replace -> R.drawable.ic_tool_replace_image
            is EditorTool.Layout -> R.drawable.ic_tool_layout
            is EditorTool.Rotate -> R.drawable.ic_tool_rotate_flip
            is EditorTool.Shadow -> R.drawable.ic_tool_shadow
            is EditorTool.Transparency -> R.drawable.ic_tool_opacity
            is EditorTool.Crop -> R.drawable.ic_tool_crop
            else -> R.drawable.ic_tool_layout
        }

        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = labelColor,
            maxLines = 1
        )
    }
}
