package com.thgiang.image.studio.ui.editor.components.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
internal fun LabelCreateSection(
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    ShapeGallery(
        shapes = defaultCreateShapes,
        selectedShape = null,
        tokens = tokens,
        onShapeSelected = { shape ->
            onLayoutEvent(EditorEvent.AddShapeTextLayer(shape))
        },
        singleRow = true,
    )
}
