@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.supportsFrameElevationUi
import com.thgiang.image.studio.ui.editor.mapper.supportsFrameShadowUi
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

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
    var isExpanded by rememberSaveable { mutableStateOf(true) }
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

            AnimatedVisibility(visible = isExpanded) {
                // Tab Content (scrollable)
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
                            fadeIn(tween(160)) togetherWith fadeOut(tween(100))
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
            }

            // Row holding Icon Tab Bar + Expand/Collapse Button
            if (!isSubTabActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeIconTabBar(
                        tabs = shapeTabs,
                        selected = activeTab,
                        tokens = tokens,
                        onTabSelected = { 
                            activeTab = it 
                            isExpanded = true
                            isSubTabActive = false
                        },
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(
                                if (isExpanded) R.string.studio_shape_collapse else R.string.studio_shape_expand,
                            ),
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
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
