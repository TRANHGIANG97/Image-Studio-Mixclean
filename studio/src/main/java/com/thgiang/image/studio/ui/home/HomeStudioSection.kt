package com.thgiang.image.studio.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thgiang.image.studio.R

data class StudioCategory(
    val id: String,
    val titleResId: Int,
    val icon: ImageVector,
    val gradient: List<Color>,
    val assetPath: String? = null
)

private val studioCategories = listOf(
    StudioCategory(
        id = "electronics",
        titleResId = R.string.studio_category_electronics,
        icon = Icons.Default.Phone,
        gradient = listOf(Color(0xFF0D47A1), Color(0xFF00BCD4))
    ),
    StudioCategory(
        id = "fashion",
        titleResId = R.string.studio_category_fashion,
        icon = Icons.Default.Favorite,
        gradient = listOf(Color(0xFF880E4F), Color(0xFFFF4081))
    ),
    StudioCategory(
        id = "cosmetics",
        titleResId = R.string.studio_category_cosmetics,
        icon = Icons.Default.Face,
        gradient = listOf(Color(0xFF4A148C), Color(0xFFE040FB)),
        assetPath = "anh_my_pham/anh_mypham.jpeg"
    ),
    StudioCategory(
        id = "home",
        titleResId = R.string.studio_category_home,
        icon = Icons.Default.Home,
        gradient = listOf(Color(0xFF1B5E20), Color(0xFF69F0AE))
    )
)

@Composable
fun StudioSection(
    useHomeDarkStyle: Boolean = false,
    onCategoryClick: (StudioCategory) -> Unit = {}
) {
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_studio_title),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(studioCategories) { category ->
                StudioCategoryCard(
                    category = category,
                    isDark = isDark,
                    useHomeDarkStyle = useHomeDarkStyle,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

@Composable
private fun StudioCategoryCard(
    category: StudioCategory,
    isDark: Boolean,
    useHomeDarkStyle: Boolean,
    onClick: () -> Unit
) {
    val hasAsset = category.assetPath != null
    val cardBackground: Brush? = if (!hasAsset) when {
        useHomeDarkStyle -> Brush.diagonalGradient(listOf(Color(0xFF101214), Color(0xFF1A1A2E)))
        isDark -> Brush.diagonalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A)))
        else -> Brush.diagonalGradient(category.gradient)
    } else null

    Box(
        modifier = Modifier
            .width(104.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (cardBackground != null) Modifier.background(cardBackground)
                else Modifier.background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0))
            )
            .clickable(onClick = onClick)
    ) {
        if (hasAsset) {
            AsyncImage(
                model = "file:///android_asset/${category.assetPath}",
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            // Semi-transparent overlay for readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        Icon(
            imageVector = category.icon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp),
            tint = Color.White.copy(alpha = 0.9f)
        )

        Text(
            text = stringResource(category.titleResId),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp, end = 4.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF7C4DFF).copy(alpha = 0.85f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "AI",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

private fun Brush.Companion.diagonalGradient(colors: List<Color>): Brush {
    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset.Infinite
    )
}
