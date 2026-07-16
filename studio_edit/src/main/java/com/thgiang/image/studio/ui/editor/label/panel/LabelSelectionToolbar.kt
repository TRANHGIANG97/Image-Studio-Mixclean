package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

/** All label tabs in one scroll row (no "More" expand control). */
internal val labelSelectionTabs: List<LabelEditTab> = listOf(
    LabelEditTab.EDIT,
    LabelEditTab.TEXT_TEMPLATE,
    LabelEditTab.FONT,
    LabelEditTab.SIZE,
    LabelEditTab.FORMAT,
    LabelEditTab.TEXT_STYLE,
    LabelEditTab.ALIGN,
    LabelEditTab.TEXT_COLOR,
    LabelEditTab.BG_COLOR,
    LabelEditTab.ELEVATION,
    LabelEditTab.TEXT_FORM,
)

@Composable
internal fun labelTabTitle(tab: LabelEditTab): String = when (tab) {
    LabelEditTab.EDIT -> stringResource(R.string.studio_label_tab_edit)
    LabelEditTab.LABEL -> stringResource(R.string.studio_tool_label)
    LabelEditTab.FONT -> stringResource(R.string.studio_label_tab_font)
    LabelEditTab.SIZE -> stringResource(R.string.studio_label_tab_size)
    LabelEditTab.TEXT_STYLE -> stringResource(R.string.studio_label_tab_text_style)
    LabelEditTab.FORMAT -> stringResource(R.string.studio_label_tab_format)
    LabelEditTab.ALIGN -> stringResource(R.string.studio_label_tab_align)
    LabelEditTab.BG_COLOR -> stringResource(R.string.studio_label_tab_bg_color)
    LabelEditTab.TEXT_COLOR -> stringResource(R.string.studio_label_tab_text_color)
    LabelEditTab.TEXT_TEMPLATE -> stringResource(R.string.studio_label_tab_text_template)
    LabelEditTab.ELEVATION -> stringResource(R.string.studio_label_tab_elevation)
    LabelEditTab.TEXT_FORM -> stringResource(R.string.studio_label_tab_text_form)
    LabelEditTab.SHAPE -> stringResource(R.string.studio_label_tab_shape)
}

@Composable
internal fun LabelSelectionToolbar(
    activeTab: LabelEditTab,
    onTabSelected: (LabelEditTab) -> Unit,
    tokens: EditorTokens,
    modifier: Modifier = Modifier,
    tabs: List<LabelEditTab> = labelSelectionTabs,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = activeTab == tab
                val color = if (isSelected) tokens.accent else Color(0xFF1F2937)

                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(tab) },
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (tab) {
                            LabelEditTab.EDIT -> {
                                Icon(
                                    imageVector = Icons.Filled.Keyboard,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            LabelEditTab.FONT -> {
                                Text(
                                    text = "Ff",
                                    color = color,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                )
                            }
                            LabelEditTab.TEXT_STYLE -> {
                                Text(
                                    text = "H",
                                    color = color,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                )
                            }
                            LabelEditTab.FORMAT -> {
                                Icon(
                                    imageVector = Icons.Filled.FormatBold,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            LabelEditTab.SIZE -> {
                                Text(
                                    text = "aA",
                                    color = color,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            LabelEditTab.ALIGN -> {
                                Icon(
                                    imageVector = Icons.Filled.FormatAlignLeft,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            LabelEditTab.TEXT_COLOR -> {
                                Icon(
                                    imageVector = Icons.Filled.FormatColorText,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            LabelEditTab.TEXT_TEMPLATE -> {
                                Text(
                                    text = "Aa",
                                    color = color,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            LabelEditTab.BG_COLOR -> {
                                Icon(
                                    imageVector = Icons.Filled.FormatColorFill,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            LabelEditTab.ELEVATION -> {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            LabelEditTab.TEXT_FORM -> {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = labelTabTitle(tab),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                    )
                }
            }
        }
    }
}
