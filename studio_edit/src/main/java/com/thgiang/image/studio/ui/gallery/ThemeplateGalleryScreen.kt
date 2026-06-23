package com.thgiang.image.studio.ui.gallery

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.RemoteTemplateRow
import com.thgiang.image.studio.ui.components.ShimmerBox
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.core.domain.model.template.CloudCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeplateGalleryScreen(
    initialTabIndex: Int,
    onBack: () -> Unit,
    onThemeplateSelected: (String, Boolean) -> Unit,
    viewModel: ThemeplateGalleryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val remoteTemplates by viewModel.remoteTemplates.collectAsState()
    val loadingRemoteTemplates by viewModel.loadingRemoteTemplates.collectAsState()
    
    var selectedTabIndex by remember { mutableStateOf(initialTabIndex) }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedTabIndex >= categories.size) {
            selectedTabIndex = 0
        }
    }

    LaunchedEffect(categories, selectedTabIndex) {
        if (categories.isNotEmpty() && selectedTabIndex < categories.size) {
            viewModel.loadTemplatesForCategory(categories[selectedTabIndex].id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.themeplate_gallery_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.studio_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {},
                indicator = {}
            ) {
                categories.forEachIndexed { index, category ->
                    val selected = selectedTabIndex == index
                    Tab(
                        selected = selected,
                        onClick = {
                            selectedTabIndex = index
                            viewModel.loadTemplatesForCategory(category.id)
                        },
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) Color(0xFFFF2D55) else Color(0xFFFF2D55).copy(alpha = 0.08f)
                            ),
                        text = {
                            val categoryName = when (category.id) {
                                "professional" -> stringResource(R.string.studio_category_professional)
                                "cosmetics" -> stringResource(R.string.studio_category_cosmetics)
                                "digital_life" -> stringResource(R.string.themeplate_professional_digital_life)
                                "selfie_food" -> stringResource(R.string.themeplate_professional_food_selfie)
                                else -> category.name
                            }
                            Text(
                                text = categoryName,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (categories.isNotEmpty() && selectedTabIndex < categories.size) {
                    RemoteThemeplateGrid(
                        templates = remoteTemplates,
                        isLoading = loadingRemoteTemplates,
                        onTemplateSelected = onThemeplateSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteThemeplateGrid(
    templates: List<RemoteTemplateRow>,
    isLoading: Boolean,
    onTemplateSelected: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading && templates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF2D55))
                }
            } else if (templates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.studio_gallery_no_templates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = templates,
                        key = { it.id }
                    ) { template ->
                        RemoteThemeplateCard(
                            template = template,
                            onClick = { onTemplateSelected(template.id, template.isPremium) }
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.studio_gallery_design_note),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            maxLines = 3
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun RemoteThemeplateCard(
    template: RemoteTemplateRow,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 5.dp,
        shadowElevation = 5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .background(Color(0xFFF5F5F5))
        ) {
            com.thgiang.image.studio.util.CloudFirstSubcomposeAsyncImage(
                sourcePath = template.thumbnailUrl,
                contentDescription = template.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = template.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }

            if (template.status != "published") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = template.status,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (template.isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFB300))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PRO",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingSoonView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.studio_gallery_coming_soon),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF2D55)
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.studio_gallery_coming_soon_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

