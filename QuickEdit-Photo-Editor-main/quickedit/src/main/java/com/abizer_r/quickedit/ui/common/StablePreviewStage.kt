package com.abizer_r.quickedit.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun StablePreviewStage(
    modifier: Modifier = Modifier,
    aspectRatio: Float,
    onStageMeasured: (IntSize) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val boundsRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else aspectRatio
        val stageModifier = if (aspectRatio > boundsRatio) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
        }

        Box(
            modifier = stageModifier.onSizeChanged(onStageMeasured),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
