plementation Plan - Advanced On-Canvas Text Controls
The goal is to modernize the text editing workflow by replacing the current "add and forget" bitmap-based text with interactive, re-editable text layers. Each text layer will have its own bounding box on the canvas with handles for moving, resizing, deleting, and editing.

User Review Required
IMPORTANT

The text will still be rendered as a bitmap internally to maintain compatibility with the existing layer system and HighResCompositor, but the underlying configuration (text, font, color) will be preserved for the "Edit" feature.

Proposed Changes
[Component] Data Model & Persistence
[MODIFY] 
EditorLayer.kt
Add EditorLayer.Text class inheriting from EditorLayer.
Include TextEditConfig property in the Text class.
[MODIFY] 
ProjectState.kt
Update LayerSnapshot to include a textConfig field (JSON string or flattened properties).
[Component] Business Logic
[MODIFY] 
EditorProjectManager.kt
Add createTextLayer(bitmap, config, ...) method.
Add updateTextLayer(layer, config, bitmap) method for re-editing.
[MODIFY] 
ImageEditorViewModel.kt
Add updateTextLayerConfig(index, config) to handle edits.
Ensure the selected layer state is properly exposed for the UI overlay to react.
[Component] UI & Interaction
[NEW] 
TextTransformOverlay.kt
A custom View that draws a bounding box around the selected text layer.
Interactive Handles:
Top-Left: Delete (Removes the layer).
Top-Right: Edit (Opens TextEditorSheet with current config).
Bottom-Right: Resize/Scale (Scales the text layer).
Gesture Handling:
Move by dragging inside the box.
Resizing logic that updates scaleX and scaleY.
[MODIFY] 
ImageEditActivity.kt
Instantiate and add TextTransformOverlay to the layout container.
Sync overlay visibility with selectedLayerIndex.
Handle callbacks from overlay (Delete -> viewModel.removeLayerAt, Edit -> showTextDialog = true).
[Component] Rendering Utility
[MODIFY] 
TextToLayerRasterizer.kt
Update rasterizeAtCenter to support generating bitmaps for existing text configs without auto-centering if needed.
Verification Plan
Automated Tests
None planned for UI interactions, will focus on manual verification.
Manual Verification
Add Text: Verify it appears with a bounding box and handles.
Move: Drag the box and ensure it moves smoothly.
Resize: Drag the bottom-right handle and verify the text scales correctly.
Edit: Click the pen icon, change text/color, and verify it updates in-place.
Delete: Click the X icon and verify the layer is removed.
Persistence: Save the project, reload, and verify the text is still editable.