package com.thgiang.image.studio.ui.editor.label.panel

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.LayerViewportScale
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import kotlin.math.roundToInt

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
internal fun LabelEditingKeyboardToolbar(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    selectionStart: Int = 0,
    selectionEnd: Int = 0,
) {
    val textSize = LayerViewportScale.effectiveTextSizeSp(layer)
    val isBold = TextRunOps.selectionIsBold(layer, selectionStart, selectionEnd)
    val isItalic = TextRunOps.selectionIsItalic(layer, selectionStart, selectionEnd)
    val sliderColors = remember(tokens) { tokens.toSliderColors() }
    var showSizePicker by remember(layer.id) { mutableStateOf(false) }
    var localTextSize by remember(layer.id) { mutableFloatStateOf(textSize) }
    var lastEmittedSize by remember(layer.id) { mutableFloatStateOf(textSize) }

    LaunchedEffect(textSize, layer.id) {
        if (kotlin.math.abs(textSize - lastEmittedSize) > 0.5f) {
            localTextSize = textSize
            lastEmittedSize = textSize
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        AnimatedVisibility(
            visible = showSizePicker,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .border(0.5.dp, tokens.borderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${localTextSize.roundToInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                    modifier = Modifier.width(32.dp),
                )
                PrecisionSlider(
                    label = "",
                    value = localTextSize,
                    valueRange = 1f..ShapeLabelDefaults.MAX_TEXT_SIZE_SP,
                    onValueChange = { value ->
                        localTextSize = value
                        lastEmittedSize = value
                        onLayoutEvent(EditorEvent.UpdateTextSize(value))
                    },
                    valueFormatter = { "${it.roundToInt()}px" },
                    colors = sliderColors,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val sizeLabel = stringResource(R.string.studio_label_text_size)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showSizePicker) tokens.accentSoft else Color(0xFFF5F5F5))
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = { showSizePicker = !showSizePicker },
                    )
                    .semantics { contentDescription = sizeLabel }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${textSize.roundToInt()} px",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (showSizePicker) tokens.accent else tokens.textPrimary,
                )
            }

            LabelToolbarIconToggle(
                icon = Icons.Filled.FormatBold,
                selected = isBold,
                tokens = tokens,
                onClick = { onLayoutEvent(EditorEvent.UpdateTextBold(!isBold)) },
            )
            LabelToolbarIconToggle(
                icon = Icons.Filled.FormatItalic,
                selected = isItalic,
                tokens = tokens,
                onClick = { onLayoutEvent(EditorEvent.UpdateTextItalic(!isItalic)) },
            )

            Box(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onDismissKeyboard,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokens.accentSoft),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardHide,
                    contentDescription = stringResource(R.string.studio_hide_keyboard),
                    tint = tokens.accent,
                )
            }
        }
    }
}

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
private fun LabelToolbarIconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.accentSoft else Color(0xFFF5F5F5))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) tokens.accent else tokens.textSecondary,
        )
    }
}
