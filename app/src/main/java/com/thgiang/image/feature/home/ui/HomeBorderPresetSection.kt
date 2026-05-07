package com.thgiang.image.feature.home.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.model.BorderPresetStyle

private data class BorderPresetItem(
    val label: String,
    val style: BorderPresetStyle,
    val swatch: Brush,
    val lightLabel: Boolean = true
)

@Composable
fun BorderPresetDock(
    selectedPreset: BorderPresetStyle?,
    isProcessing: Boolean,
    useHomeDarkStyle: Boolean,
    onPresetClick: (BorderPresetStyle) -> Unit
) {
    val items = listOf(
        BorderPresetItem(
            label = "Solid",
            style = BorderPresetStyle.SOLID,
            swatch = Brush.linearGradient(listOf(Color(0xFF0E1015), Color(0xFF1A1D24))),
            lightLabel = true
        ),
        BorderPresetItem(
            label = "White",
            style = BorderPresetStyle.WHITE,
            swatch = Brush.linearGradient(listOf(Color(0xFFFDFDFD), Color(0xFFF3F3F3))),
            lightLabel = false
        ),
        BorderPresetItem(
            label = "Gradient",
            style = BorderPresetStyle.GRADIENT,
            swatch = Brush.linearGradient(listOf(Color(0xFFC8A46A), Color(0xFFE9CF9A), Color(0xFFB98744))),
            lightLabel = true
        ),
        BorderPresetItem(
            label = "Neon",
            style = BorderPresetStyle.NEON,
            swatch = Brush.linearGradient(listOf(Color(0xFF08111E), Color(0xFF1D2F4D), Color(0xFF57E4FF))),
            lightLabel = true
        )
    )

    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    val selectedStroke = if (useHomeDarkStyle) HomeDarkStyle.accent else Color(0xFFB78B50)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.border_presets_title),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(items) { preset ->
                val selected = selectedPreset == preset.style
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF101214) else Color(0xFFF6F3EE))
                        .then(
                            if (selected) Modifier.border(2.dp, selectedStroke, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .clickable(enabled = !isProcessing) {
                            onPresetClick(preset.style)
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(58.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(preset.swatch)
                            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    )

                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (preset.lightLabel) Color.White else Color(0xFF161616),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(
                                if (preset.lightLabel) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.75f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Chỉ hiện khi đang xử lý border cụ thể (tránh hiện khi remove background không liên quan)
        if (isProcessing && selectedPreset != null) {
            Text(
                text = stringResource(R.string.border_applying),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}





