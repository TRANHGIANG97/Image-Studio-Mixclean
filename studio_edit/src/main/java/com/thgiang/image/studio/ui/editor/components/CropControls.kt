@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.CropRatio
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
fun CropToolPanel(
    cropRatio: CropRatio,
    onSelectCropRatio: (CropRatio) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.studio_crop_label),
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
            val ratioText = if (cropRatio == CropRatio.ORIGINAL) {
                stringResource(R.string.studio_crop_ratio_original)
            } else {
                cropRatio.label
            }
            Text(
                text = ratioText,
                color = tokens.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(180))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CropRatio.entries.forEach { ratio ->
                    CropRatioButton(
                        ratio = ratio,
                        selected = cropRatio == ratio,
                        onClick = { onSelectCropRatio(ratio) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CropRatioButton(
    ratio: CropRatio,
    selected: Boolean,
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    val borderColor = if (selected) tokens.accent else Color(0xFFE5E7EB)
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val containerBg = if (selected) tokens.accentSoft else Color(0xFFF8F8F8)
    val contentColor = if (selected) tokens.accent else tokens.textSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .then(
                if (ratio == CropRatio.ORIGINAL) {
                    Modifier.widthIn(min = 42.dp)
                } else {
                    Modifier.width(42.dp)
                },
            )
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .height(16.dp)
                .width(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val boxModifier = Modifier
                .border(
                    width = 1.dp,
                    color = contentColor.copy(alpha = if (selected) 0.85f else 0.5f),
                    shape = RoundedCornerShape(2.dp),
                )
                .background(contentColor.copy(alpha = 0.05f))

            when (ratio) {
                CropRatio.ORIGINAL -> {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(1.dp, contentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.Center)
                                .border(1.dp, contentColor, RoundedCornerShape(2.dp)),
                        )
                    }
                }
                CropRatio.RATIO_1_1 -> Box(modifier = boxModifier.size(10.dp))
                CropRatio.RATIO_3_4 -> Box(modifier = boxModifier.size(9.dp, 12.dp))
                CropRatio.RATIO_4_3 -> Box(modifier = boxModifier.size(12.dp, 9.dp))
                CropRatio.RATIO_9_16 -> Box(modifier = boxModifier.size(8.dp, 14.dp))
                CropRatio.RATIO_16_9 -> Box(modifier = boxModifier.size(14.dp, 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (ratio == CropRatio.ORIGINAL) {
                stringResource(R.string.studio_crop_ratio_original)
            } else {
                ratio.label
            },
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
