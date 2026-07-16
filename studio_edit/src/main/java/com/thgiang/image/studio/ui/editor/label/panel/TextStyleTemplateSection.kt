package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
internal fun TextStyleTemplateSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = layer.matchedTextStyleTemplate()
    val listState = rememberLazyListState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(textStyleTemplates, key = { it.id }) { template ->
                TextStyleTemplateChip(
                    template = template,
                    isSelected = selected?.id == template.id,
                    tokens = tokens,
                    onClick = { onLayoutEvent(EditorEvent.ApplyTextStyleTemplate(template.id)) },
                )
            }
        }
    }
}

@Composable
private fun TextStyleTemplateChip(
    template: TextStyleTemplate,
    isSelected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (isSelected) tokens.accent else Color(0xFFE0E0E0)
    val fillColor = Color(template.previewFillArgb)
    val textColor = Color(template.previewTextArgb)

    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(shape)
            .border(2.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(fillColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                color = textColor,
                fontSize = 22.sp,
                fontWeight = if (template.fontWeight == "bold") FontWeight.Bold else FontWeight.SemiBold,
                fontStyle = if (template.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(template.nameRes),
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) tokens.accent else tokens.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
