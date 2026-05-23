package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = Color(0xF012171A),
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
                val isSelected = selectedTool == tool
                ToolButton(
                    tool = tool,
                    isSelected = isSelected,
                    onClick = {
                        if (tool == EditorTool.Replace) onReplaceImage()
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
    val labelRes = when (tool) {
        EditorTool.Replace -> R.string.studio_tool_replace
        EditorTool.Layout -> R.string.studio_tool_layout
        EditorTool.Rotate -> R.string.studio_tool_rotateflip
        EditorTool.Shadow -> R.string.studio_tool_shadow
        EditorTool.Transparency -> R.string.studio_tool_transparency
        EditorTool.Crop -> R.string.studio_tool_crop
        else -> R.string.studio_tool_layout
    }

    val accent = Color(0xFF3794FF)
    val iconColor = if (isSelected) accent else Color.White
    val labelColor = if (isSelected) accent else Color(0xFFD7DCE3)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) Color(25, 29, 32) else Color.Transparent,
        animationSpec = tween(200),
        label = "toolContainer"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        val iconRes = when (tool) {
            EditorTool.Replace -> R.drawable.ic_tool_replace_image
            EditorTool.Layout -> R.drawable.ic_tool_layout
            EditorTool.Rotate -> R.drawable.ic_tool_rotate_flip
            EditorTool.Shadow -> R.drawable.ic_tool_shadow
            EditorTool.Transparency -> R.drawable.ic_tool_opacity
            EditorTool.Crop -> R.drawable.ic_tool_crop
            else -> R.drawable.ic_tool_layout
        }

        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = labelColor,
            maxLines = 1
        )
    }
}
