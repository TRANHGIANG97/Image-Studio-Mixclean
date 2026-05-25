package com.thgiang.image.studio.ui.editor.components.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

data class ToolItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val isSelected: Boolean = false,
    val badge: String? = null,
    val tint: Color = EditorColorPalette.AccentCyan
)

/**
 * Adaptive tool palette that renders horizontally in compact mode
 * and vertically in tablet/desktop mode.
 *
 * Compact (phone): horizontal scrollable strip — replaces [BottomToolBarStatic].
 * Medium/Expanded (tablet): vertical column pinned to the left edge.
 */
@Composable
fun ToolPaletteStrip(
    tools: List<ToolItem>,
    selectedToolId: String? = null,
    onToolSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    orientation: PanelOrientation = PanelOrientation.HORIZONTAL,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    when (orientation) {
        PanelOrientation.HORIZONTAL -> {
            HorizontalToolStrip(
                tools = tools,
                selectedToolId = selectedToolId,
                onToolSelected = onToolSelected,
                modifier = modifier
                    .fillMaxWidth()
                    .height(TOOL_STRIP_HEIGHT)
                    .background(tokens.surfaceBase),
                tokens = tokens
            )
        }
        PanelOrientation.VERTICAL -> {
            VerticalToolStrip(
                tools = tools,
                selectedToolId = selectedToolId,
                onToolSelected = onToolSelected,
                modifier = modifier
                    .fillMaxHeight()
                    .width(TOOL_STRIP_VERTICAL_WIDTH)
                    .background(tokens.surfaceBase),
                tokens = tokens
            )
        }
    }
}

@Composable
private fun HorizontalToolStrip(
    tools: List<ToolItem>,
    selectedToolId: String?,
    onToolSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(tools, key = { it.id }) { tool ->
            ToolStripButton(
                tool = tool.copy(isSelected = tool.id == selectedToolId),
                onClick = { onToolSelected(tool.id) },
                showLabel = true,
                tokens = tokens
            )
        }
    }
}

@Composable
private fun VerticalToolStrip(
    tools: List<ToolItem>,
    selectedToolId: String?,
    onToolSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens
) {
    LazyColumn(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(tools, key = { it.id }) { tool ->
            ToolStripButton(
                tool = tool.copy(isSelected = tool.id == selectedToolId),
                onClick = { onToolSelected(tool.id) },
                showLabel = true,
                tokens = tokens
            )
        }
    }
}

@Composable
private fun ToolStripButton(
    tool: ToolItem,
    onClick: () -> Unit,
    showLabel: Boolean,
    tokens: EditorTokens
) {
    val bg = if (tool.isSelected) {
        tokens.accent.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.cornerMedium))
            .background(bg)
            .then(
                if (showLabel) {
                    Modifier.width(TOOL_BUTTON_SIZE).padding(vertical = 6.dp)
                } else {
                    Modifier.size(TOOL_BUTTON_SIZE)
                }
            )
            .then(Modifier.clickablePainless(onClick)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = tool.label,
            modifier = Modifier.size(22.dp),
            tint = if (tool.isSelected) tool.tint else tokens.textSecondary
        )
        if (showLabel) {
            Text(
                text = tool.label,
                fontSize = 10.sp,
                color = if (tool.isSelected) tool.tint else tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class PanelOrientation { HORIZONTAL, VERTICAL }

private val TOOL_STRIP_HEIGHT: Dp = 64.dp
private val TOOL_STRIP_VERTICAL_WIDTH: Dp = 64.dp
private val TOOL_BUTTON_SIZE: Dp = 56.dp

private fun Modifier.clickablePainless(onClick: () -> Unit): Modifier {
    return this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick
    )
}
