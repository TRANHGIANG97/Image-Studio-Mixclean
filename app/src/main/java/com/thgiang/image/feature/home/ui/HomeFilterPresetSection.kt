package com.thgiang.image.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeDarkStyle

private data class FilterPreset(
    val name: String,
    val color: Color
)

@Composable
fun FilterPresetDock(
    selectedIndex: Int?,
    isProcessing: Boolean,
    useHomeDarkStyle: Boolean,
    onPresetClick: (Int) -> Unit
) {
    val items = listOf(
        FilterPreset("Original", Color.Gray),
        FilterPreset("Cinematic", Color(0xFF1A237E)),
        FilterPreset("Vintage", Color(0xFF795548)),
        FilterPreset("Portrait", Color(0xFFEC407A)),
        FilterPreset("B&W", Color.Black),
        FilterPreset("Vibrant", Color(0xFFFFAB00)),
        FilterPreset("Cool", Color(0xFF00B0FF)),
        FilterPreset("Warm", Color(0xFFFF6D00))
    )

    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    val selectedStroke = if (useHomeDarkStyle) HomeDarkStyle.accent else Color(0xFFB78B50)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.filter_artistic_title),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            itemsIndexed(items) { index, filter ->
                val selected = selectedIndex == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(enabled = !isProcessing) {
                            onPresetClick(index)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(filter.color.copy(alpha = 0.8f))
                            .then(
                                if (selected) Modifier.border(3.dp, selectedStroke, CircleShape)
                                else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.name.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
