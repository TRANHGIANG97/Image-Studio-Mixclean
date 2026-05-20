package com.thgiang.image.studio.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
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
    forceDarkStyle: Boolean = false,
    onCategoryClick: (StudioCategory) -> Unit = {}
) {
    val isDark = forceDarkStyle || isSystemInDarkTheme()

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_studio_title),
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = if (isDark) Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(1f, 1f),
                    blurRadius = 3f
                ) else null
            ),
            color = if (isDark) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(studioCategories, key = { it.id }) { category ->
                StudioCategoryCard(
                    category = category,
                    isDark = isDark,
                    forceDarkStyle = forceDarkStyle,
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
    forceDarkStyle: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )
    
    val hasAsset = category.assetPath != null
    val cardBackground: Brush? = if (!hasAsset) when {
        forceDarkStyle -> Brush.linearGradient(
            colors = listOf(Color(0xFF101214), Color(0xFF1A1A2E)),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
        isDark -> Brush.linearGradient(
            colors = listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A)),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
        else -> Brush.linearGradient(
            colors = category.gradient,
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    } else null

    Box(
        modifier = Modifier
            .width(108.dp)
            .height(92.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (cardBackground != null) Modifier.background(cardBackground)
                else Modifier.background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0))
            )
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(category.titleResId),
                indication = null, // Custom ripple handled by parent or interaction source if needed
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        if (hasAsset) {
            SubcomposeAsyncImage(
                model = "file:///android_asset/${category.assetPath}",
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        Modifier.matchParentSize()
                            .shimmerEffect()
                    )
                },
                error = {
                    Box(
                        Modifier.matchParentSize()
                            .background(Color(0xFFE0E0E0))
                    )
                }
            )
            
            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f)
                            ),
                            startY = 0f,
                            endY = 300f
                        )
                    )
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
                .padding(start = 10.dp, bottom = 8.dp, end = 4.dp)
        )

        // AI badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF7C4DFF).copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
        ) {
            Text(
                text = "AI",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Shimmer loading effect for images
 */
@Composable
private fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    return background(
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFE0E0E0),
                Color(0xFFF5F5F5),
                Color(0xFFE0E0E0)
            ),
            start = Offset(translateAnim - 200, 0f),
            end = Offset(translateAnim + 200, 0f)
        )
    )
}
