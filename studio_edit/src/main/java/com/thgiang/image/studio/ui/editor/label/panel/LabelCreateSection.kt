package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.panel.*

import androidx.compose.runtime.Composable
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.ui.Modifier
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
internal fun LabelCreateSection(
    pendingShape: ShapeType?,
    tokens: EditorTokens,
    onPendingShapeChange: (ShapeType) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShapeGallery(
        shapes = defaultCreateShapes,
        selectedShape = pendingShape,
        tokens = tokens,
        onShapeSelected = onPendingShapeChange,
        singleRow = true,
        modifier = modifier,
    )
}
