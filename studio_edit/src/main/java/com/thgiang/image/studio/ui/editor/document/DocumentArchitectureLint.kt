package com.thgiang.image.studio.ui.editor.document

/**
 * Architecture guard (Phase F).
 *
 * Rules for contributors:
 * 1. Do not set `EditorLayer.shapeWidthPx` / `shapeHeightPx` from UI panels.
 *    Go through [DocumentCommand] + [com.thgiang.image.studio.ui.editor.document.layout.LayoutEngine].
 * 2. Do not reintroduce `shapeFitFlow` / `skipShapeFit` — sizing goes through
 *    [com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent] + LayoutEngine
 *    (or synchronous fit inside a Document-disabled delegate fallback).
 * 3. Effect conflicts must be declared in
 *    [com.thgiang.image.studio.ui.editor.document.rules.EffectCompatibilityMatrix].
 * 4. Frame+label geometry sync must use [com.thgiang.image.studio.ui.editor.model.LayerGroupSync].
 *
 * Allowed writers of box size:
 * - `document/layout/LayoutEngine.kt`
 * - `document/adapter/EditorLayerBridge.kt`
 * - Gesture bake paths that then dispatch `DocumentCommand.BakeCornerScale` / `ResizeEdge`
 */
object DocumentArchitectureLint {
    const val ADR_PATH = "studio_edit/docs/LABEL_DOCUMENT_ARCHITECTURE.md"
}
