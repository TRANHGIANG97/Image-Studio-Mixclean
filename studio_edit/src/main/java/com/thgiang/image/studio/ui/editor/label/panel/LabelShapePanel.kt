@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.panel.*

import androidx.compose.animation.AnimatedVisibility
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

/**
 * Panel cho Label — chỉ còn text, không còn chọn hình dạng.
 * Hình dạng đã được tách thành công cụ Shape riêng.
 */
@Composable
fun LabelShapePanel(
    selectedLayer: EditorLayer?,
    onLayoutEvent: (EditorEvent) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
    canvasFirstMode: Boolean = false,
    showTabBar: Boolean = true,
    activeTab: LabelEditTab = LabelEditTab.FONT,
    onActiveTabChange: (LabelEditTab) -> Unit = {},
) {
    if (selectedLayer != null && selectedLayer.isLabelLayer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabelEditSection(
                layer = selectedLayer,
                tokens = tokens,
                onLayoutEvent = onLayoutEvent,
                tabOrder = if (canvasFirstMode) labelSelectionTabs else labelTextFirstTabs,
                showTabBar = showTabBar,
                canvasFirstMode = canvasFirstMode,
                activeTabExternal = if (canvasFirstMode) activeTab else null,
                onActiveTabChange = onActiveTabChange,
            )
        }
    }
}
