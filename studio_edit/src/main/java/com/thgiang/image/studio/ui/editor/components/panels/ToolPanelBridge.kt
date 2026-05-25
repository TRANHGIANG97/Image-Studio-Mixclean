package com.thgiang.image.studio.ui.editor.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Tracks which tool is currently active in the editor panel system.
 *
 * [Idle] — no tool active; property panel hidden.
 * [Active] — a tool is selected; property panel shows the tool's controls.
 * [Adjusting] — user is actively dragging/adjusting a control within the panel.
 */
sealed interface EditorToolState {
    data object Idle : EditorToolState
    data class Active(val toolId: String, val toolLabel: String) : EditorToolState
    data class Adjusting(val toolId: String, val toolLabel: String) : EditorToolState
}

/**
 * Creates and remembers an [EditorToolState] holder that bridges the tool palette
 * and the property panel.
 *
 * Usage:
 * ```
 * val (toolState, selectTool, dismissTool) = rememberToolPanelState()
 *
 * ToolPaletteStrip(
 *     selectedToolId = (toolState as? EditorToolState.Active)?.toolId,
 *     onToolSelected = { selectTool(it, label) }
 * )
 *
 * PropertyPanel(
 *     visible = toolState !is EditorToolState.Idle,
 *     toolName = (toolState as? EditorToolState.Active)?.toolLabel ?: "",
 *     onClose = { dismissTool() }
 * ) { ... }
 * ```
 */
@Composable
fun rememberToolPanelState(): ToolPanelState {
    var state by remember { mutableStateOf<EditorToolState>(EditorToolState.Idle) }

    return remember {
        object : ToolPanelState {
            override var current: EditorToolState
                get() = state
                private set(value) { state = value }

            override fun selectTool(toolId: String, toolLabel: String) {
                current = EditorToolState.Active(toolId, toolLabel)
            }

            override fun startAdjusting() {
                val s = current
                if (s is EditorToolState.Active) {
                    current = EditorToolState.Adjusting(s.toolId, s.toolLabel)
                }
            }

            override fun finishAdjusting() {
                val s = current
                if (s is EditorToolState.Adjusting) {
                    current = EditorToolState.Active(s.toolId, s.toolLabel)
                }
            }

            override fun dismiss() {
                current = EditorToolState.Idle
            }

            override val isActive: Boolean get() = current !is EditorToolState.Idle
            override val toolId: String? get() = (current as? EditorToolState.Active)?.toolId
                ?: (current as? EditorToolState.Adjusting)?.toolId
            override val toolLabel: String? get() = (current as? EditorToolState.Active)?.toolLabel
                ?: (current as? EditorToolState.Adjusting)?.toolLabel
        }
    }
}

interface ToolPanelState {
    val current: EditorToolState
    fun selectTool(toolId: String, toolLabel: String)
    fun startAdjusting()
    fun finishAdjusting()
    fun dismiss()
    val isActive: Boolean
    val toolId: String?
    val toolLabel: String?
}
