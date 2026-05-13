package com.thgiang.image.feature.home.ui
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.design.theme.ImageDesign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import com.thgiang.image.feature.home.model.PresetItem
import com.thgiang.image.core.model.PresetStyle
import com.thgiang.image.feature.home.ui.preset.PresetCardArtwork

@Composable
fun PresetDock(
    isPremium: Boolean,
    onLockedClick: () -> Unit,
    onPresetClick: (PresetItem) -> Unit,
    useHomeDarkStyle: Boolean = false,
    isProcessing: Boolean = false,
    selectedPreset: PresetStyle? = null,
) {
    val ai = if (useHomeDarkStyle) HomeDarkStyle.accent else ImageDesign.semantic.aiAccent

    val presetItems = listOf(
        PresetItem("Noir", PresetStyle.NOIR, locked = false, lightLabel = true),
        PresetItem("Clean", PresetStyle.CLEAN, locked = false, lightLabel = false),
        PresetItem("Aurora", PresetStyle.AURORA, locked = false, lightLabel = true),
        PresetItem("Duotone", PresetStyle.DUOTONE, locked = false, lightLabel = true),
        PresetItem("Neon Grid", PresetStyle.NEON_GRID, locked = false, lightLabel = true),
        PresetItem("Liquid", PresetStyle.LIQUID_GLASS, locked = false, lightLabel = false),
        PresetItem("Sunset", PresetStyle.SUNSET_FILM, locked = false, lightLabel = true),
        PresetItem("Carbon X", PresetStyle.CARBON_X, locked = false, lightLabel = true),
    )

    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_background_presets),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(presetItems) { preset ->

                val locked = preset.locked
                val selected = selectedPreset == preset.style
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed && !isProcessing) 0.96f else 1f,
                    animationSpec = tween(120),
                    label = "presetScale"
                )

                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(68.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF101214) else Color(0xFFF6F3EE))
                        .then(
                            if (selected) Modifier.border(
                                2.dp,
                                if (useHomeDarkStyle) HomeDarkStyle.accent else Color(0xFFB78B50),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = !isProcessing
                        ) {
                            if (locked) onLockedClick()
                            else onPresetClick(preset)
                        }
                ) {
                    PresetCardArtwork(
                        style = preset.style,
                        ai = ai,
                        modifier = Modifier.fillMaxSize()
                    )

                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (preset.lightLabel) Color.White else Color(0xFF171717),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )

                    if (locked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(16.dp),
                            tint = if (preset.lightLabel) Color.White else Color(0xFF161616)
                        )
                    }
                }
            }
        }
    }

    if (isProcessing) {
        Text(
            text = stringResource(R.string.home_applying_preset),
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}




