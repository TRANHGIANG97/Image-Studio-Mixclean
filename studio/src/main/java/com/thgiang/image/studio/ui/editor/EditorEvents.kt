package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.compose.ui.geometry.Offset

sealed class EditorEvent {
    data class LoadTemplate(val assetPath: String) : EditorEvent()
    data class SetProductImage(val uri: Uri) : EditorEvent()
    data class UpdateOffset(val delta: Offset) : EditorEvent()
    data class SetOffset(val offset: Offset) : EditorEvent()
    data class UpdateScale(val factor: Float) : EditorEvent()
    data class SetScale(val scale: Float) : EditorEvent()
    data class UpdateRotation(val delta: Float) : EditorEvent()
    data class SetRotation(val degrees: Float) : EditorEvent()
    data object FlipHorizontal : EditorEvent()
    data object FlipVertical : EditorEvent()
    data class UpdateShadow(val intensity: Float) : EditorEvent()
    data class UpdateAlpha(val alpha: Float) : EditorEvent()
    data class SelectTool(val tool: EditorTool) : EditorEvent()
    data class SelectCropRatio(val ratio: CropRatio) : EditorEvent()
    data object CommitTransform : EditorEvent()
    data object Undo : EditorEvent()
    data object Redo : EditorEvent()
    data class Export(val templateAssetPath: String) : EditorEvent()
}
