package com.abizer_r.quickedit.ui.editorScreen.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object EditorToolButtonTemplate {
    val ToolbarBackgroundColor = Color(0xFFEDE8E5)
    val ButtonSelectedColor = Color(0xFFF3E8D9)

    val ButtonShape = RoundedCornerShape(12.dp)
    val ButtonHorizontalPadding = 10.dp
    val ButtonVerticalPadding = 8.dp
    val IconSize = 24.dp
    val LabelSpacing = 2.dp
    val LabelFontSize = 10.sp

    val ToolbarHorizontalPadding = 16.dp
    val ToolbarVerticalPadding = 6.dp
    val ToolbarItemSpacing = 16.dp
    val ToolbarTonalElevation = 3.dp
}

@Composable
fun EditorBottomToolbarTemplate(
    modifier: Modifier = Modifier,
    toolbarHeight: Dp,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        tonalElevation = EditorToolButtonTemplate.ToolbarTonalElevation,
        color = EditorToolButtonTemplate.ToolbarBackgroundColor,
        modifier = modifier.height(toolbarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(
                    horizontal = EditorToolButtonTemplate.ToolbarHorizontalPadding,
                    vertical = EditorToolButtonTemplate.ToolbarVerticalPadding
                ),
            horizontalArrangement = Arrangement.spacedBy(EditorToolButtonTemplate.ToolbarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
