@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.supportsFrameElevationUi
import com.thgiang.image.studio.ui.editor.mapper.supportsFrameShadowUi
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.studio.ui.editor.theme.MotionTokens

/**
 * Canva-style Shape Panel.
 *
 * Layout structure:
 * ┌─────────────────────────────┐
 * │       ── Handle ──          │ ← 20dp drag handle visual
 * │ [🎨][✏️][💫][↔️][↩️][⬜][⋮] │ ← Icon Tab Bar (64dp)
 * │─────────────────────────────│
 * │  ○○○○○○○○ [scroll →]       │ ← Preset strip (56dp, only for FILL)
 * │  [Tab Content]              │ ← Active section (scrollable)
 * └─────────────────────────────┘
 */
@Composable
fun ShapePanel(
    selectedLayer: EditorLayer? = null,
    onLayoutEvent: (EditorEvent) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    var activeTab by rememberSaveable { mutableStateOf(ShapeEditTab.FILL) }
    var isSubTabActive by remember { mutableStateOf(false) }

    val isFrameLayer = selectedLayer?.isFrameLayer == true
    val shapeTabs = remember(selectedLayer?.supportsFrameElevationUi) {
        shapeTabsForLayer(selectedLayer?.supportsFrameElevationUi == true)
    }

    LaunchedEffect(selectedLayer?.id, selectedLayer?.supportsFrameElevationUi, shapeTabs) {
        if (activeTab !in shapeTabs) {
            activeTab = ShapeEditTab.FILL
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        if (isFrameLayer && selectedLayer != null) {
            // ── EDITING MODE ─────────────────────────────────────────────

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            ) {
                AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn(MotionTokens.fadeDefault) togetherWith fadeOut(MotionTokens.fadeQuick)
                        },
                        label = "shapeTabContent",
                    ) { tab ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Spacer(Modifier.height(2.dp))
                            when (tab) {
                                ShapeEditTab.FILL -> {
                                    // Fill color / gradient (BG_COLOR tab from LabelEditSection)
                                    LabelGradientSection(
                                        layer = selectedLayer,
                                        tokens = tokens,
                                        onLayoutEvent = onLayoutEvent,
                                        showMode = LabelColorTab.Background,
                                        onSubTabActiveChanged = { isSubTabActive = it },
                                    )
                                }
                                ShapeEditTab.STROKE -> {
                                    LabelStrokeSection(
                                        layer = selectedLayer,
                                        tokens = tokens,
                                        onLayoutEvent = onLayoutEvent,
                                    )
                                }
                                ShapeEditTab.SHADOW -> {
                                    if (selectedLayer.supportsFrameShadowUi) {
                                        ShapeShadowSection(
                                            appearance = selectedLayer.appearance,
                                            tokens = tokens,
                                            onLayoutEvent = onLayoutEvent,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.studio_label_shadow_requires_border),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                        )
                                    }
                                }
                                ShapeEditTab.ELEVATION -> {
                                    if (selectedLayer.supportsFrameElevationUi) {
                                        ShapeElevationSection(
                                            appearance = selectedLayer.appearance,
                                            fillColorArgb = selectedLayer.shapeColorArgb,
                                            tokens = tokens,
                                            onLayoutEvent = onLayoutEvent,
                                            elevationTarget = ElevationTarget.SHAPE,
                                            onSubTabActiveChanged = { isSubTabActive = it },
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.studio_label_elevation_requires_border),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                        )
                                    }
                                }
                                ShapeEditTab.SHAPE -> {
                                    LabelShapeSection(
                                        layer = selectedLayer,
                                        editableShapes = defaultShapeTabShapes,
                                        tokens = tokens,
                                        onLayoutEvent = onLayoutEvent,
                                    )
                                }
                            }
                        }
                    }
            }

            if (!isSubTabActive) {
                ShapeIconTabBar(
                    tabs = shapeTabs,
                    selected = activeTab,
                    tokens = tokens,
                    onTabSelected = {
                        activeTab = it
                        isSubTabActive = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // ── CREATION MODE ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                LabelCreateSection(
                    pendingShape = null,
                    tokens = tokens,
                    onPendingShapeChange = { shapeType ->
                        onLayoutEvent(EditorEvent.ConfirmAddShape(shapeType))
                    },
                )
            }
        }
    }
}
