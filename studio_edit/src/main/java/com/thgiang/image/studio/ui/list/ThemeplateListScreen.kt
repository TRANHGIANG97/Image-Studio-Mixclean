package com.thgiang.image.studio.ui.list

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.model.StudioThemeplates
import com.thgiang.image.studio.util.CloudFirstSubcomposeAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeplateListScreen(
    onBack: () -> Unit,
    onThemeplateSelected: (StudioThemeplate) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.studio_category_cosmetics))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.studio_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp), // Responsive: 2-3 columns
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(
                items = StudioThemeplates.all,
                key = { it.id }
            ) { themeplate ->
                ThemeplateCardV2(
                    themeplate = themeplate,
                    onClick = { onThemeplateSelected(themeplate) }
                )
            }
        }
    }
}

@Composable
internal fun ThemeplateCardV2(
    themeplate: StudioThemeplate,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 5.dp,
        animationSpec = tween(160),
        label = "cardElevation"
    )

    val baseAssetPath = themeplate.backgroundAssetPath ?: themeplate.assetPath

    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = elevation,
        shadowElevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .background(Color(0xFFF5F5F5))
        ) {
            CloudFirstSubcomposeAsyncImage(
                sourcePath = baseAssetPath,
                contentDescription = stringResource(themeplate.titleResId),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    ShimmerBox(Modifier.matchParentSize())
                },
                error = {
                    Box(
                        Modifier.matchParentSize().background(Color(0xFFEEEEEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                    }
                }
            )

            themeplate.objectSourceAssetPath?.let { objectPath ->
                CloudFirstSubcomposeAsyncImage(
                    sourcePath = objectPath,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                    loading = {},
                    error = {}
                )
            }
        }
    }
}

@Composable
internal fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE0E0E0),
                    Color(0xFFF5F5F5),
                    Color(0xFFE0E0E0)
                ),
                start = Offset(translateAnim, 0f),
                end = Offset(translateAnim + 200, 0f)
            )
        )
    )
}
