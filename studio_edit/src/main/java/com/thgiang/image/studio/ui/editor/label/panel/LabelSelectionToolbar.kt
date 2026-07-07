package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropFree
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
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

internal val labelSelectionTabs: List<LabelEditTab> = listOf(
    LabelEditTab.EDIT,
    LabelEditTab.FONT,
    LabelEditTab.SIZE,
    LabelEditTab.TEXT_STYLE,
    LabelEditTab.FORMAT,
    LabelEditTab.ALIGN,
    LabelEditTab.TEXT_COLOR,
    LabelEditTab.BG_COLOR,
    LabelEditTab.ELEVATION,
    LabelEditTab.TEXT_FORM,
    LabelEditTab.SHAPE,
)

@Composable
internal fun labelTabTitle(tab: LabelEditTab): String = when (tab) {
    LabelEditTab.EDIT -> "Sửa"
    LabelEditTab.LABEL -> "Nhãn"
    LabelEditTab.FONT -> "Phông chữ"
    LabelEditTab.SIZE -> "Cỡ chữ"
    LabelEditTab.TEXT_STYLE -> "Kiểu văn bản"
    LabelEditTab.FORMAT -> "Định dạng"
    LabelEditTab.ALIGN -> "Căn lề"
    LabelEditTab.BG_COLOR -> "Màu nền"
    LabelEditTab.TEXT_COLOR -> "Màu sắc"
    LabelEditTab.ELEVATION -> "Hiệu ứng"
    LabelEditTab.TEXT_FORM -> "Nghệ thuật"
    LabelEditTab.SHAPE -> "Khung nhãn"
}

@Composable
internal fun LabelSelectionToolbar(
    activeTab: LabelEditTab,
    onTabSelected: (LabelEditTab) -> Unit,
    onConfirm: () -> Unit,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
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
                            LabelEditTab.SHAPE -> {
                                Icon(
                                    imageVector = Icons.Filled.CropFree,
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

        IconButton(
            onClick = onConfirm,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFF3F4F6)),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.studio_done),
                tint = tokens.textPrimary,
            )
        }
    }
}
