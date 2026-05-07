package com.abizer_r.quickedit.ui.textMode.bottomToolbarExtension.textFormatOptions.sizeOptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
// ToolBarBackgroundColor removed from imports
import com.abizer_r.quickedit.ui.common.toolbar.SelectableToolbarItem

@Composable
fun FontSizeOptions(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onDecreaseClicked: () -> Unit,
    onIncreaseClicked: () -> Unit
) {
    Row(
        modifier = modifier
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectableToolbarItem(
            imageVector = Icons.Default.Remove,
            isSelected = false,
            onClick = onDecreaseClicked
        )

        SelectableToolbarItem(
            imageVector = Icons.Default.Add,
            isSelected = false,
            onClick = onIncreaseClicked
        )
    }
}
