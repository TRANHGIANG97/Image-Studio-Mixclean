package com.thgiang.image.studio.ui.editor.label.panel

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.EditorTextStyleMapper
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.LayerViewportScale
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import kotlin.math.roundToInt

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
internal fun LabelEditingKeyboardToolbar(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textSize = LayerViewportScale.effectiveTextSizeSp(layer)
    val isBold = EditorTextStyleMapper.isBoldWeight(layer.fontWeight)
    val isItalic = EditorTextStyleMapper.isItalicStyle(layer.fontStyle)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${textSize.roundToInt()} px",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
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
            onClick = onConfirm,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tokens.accent),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.studio_done),
                tint = Color.White,
            )
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
