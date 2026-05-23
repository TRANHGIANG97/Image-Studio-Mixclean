package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        listOf(
            EditorTool.Replace to Icons.Default.Photo,
            EditorTool.Layout to Icons.Default.DragIndicator,
            EditorTool.Rotate to Icons.Default.Refresh,
            EditorTool.Shadow to Icons.Default.WbSunny,
            EditorTool.Transparency to Icons.Default.Opacity,
            EditorTool.Crop to Icons.Default.CropSquare
        )
    }

    Surface(
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        modifier = modifier
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { (tool, icon) ->
                val isSelected = selectedTool == tool
                ToolButton(
                    tool = tool,
                    icon = icon,
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
    icon: ImageVector,
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
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(200),
        label = "toolContainer"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
