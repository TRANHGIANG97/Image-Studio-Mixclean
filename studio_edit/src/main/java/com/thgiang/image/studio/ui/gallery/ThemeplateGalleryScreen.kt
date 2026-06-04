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
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.model.StudioThemeplates
import com.thgiang.image.studio.ui.list.ThemeplateCardV2
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.core.domain.model.template.CloudCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeplateGalleryScreen(
    initialTabIndex: Int,
    onBack: () -> Unit,
    onThemeplateSelected: (StudioThemeplate) -> Unit,
    viewModel: ThemeplateGalleryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    
    var selectedTabIndex by remember { mutableStateOf(initialTabIndex) }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedTabIndex >= categories.size) {
            selectedTabIndex = 0
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
                        onClick = { selectedTabIndex = index },
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
                    val currentCategoryId = categories[selectedTabIndex].id
                    when (currentCategoryId) {
                        "professional" -> ThemeplateGrid(
                            themeplates = StudioThemeplates.professional,
                            onThemeplateSelected = onThemeplateSelected
                        )
                        "cosmetics" -> ThemeplateGrid(
                            themeplates = StudioThemeplates.cosmetics,
                            onThemeplateSelected = onThemeplateSelected
                        )
                        "digital_life" -> ThemeplateGrid(
                            themeplates = StudioThemeplates.professionalSections.getOrNull(0)?.themeplates ?: emptyList(),
                            onThemeplateSelected = onThemeplateSelected
                        )
                        "selfie_food" -> ThemeplateGrid(
                            themeplates = StudioThemeplates.professionalSections.getOrNull(1)?.themeplates ?: emptyList(),
                            onThemeplateSelected = onThemeplateSelected
                        )
                        else -> ComingSoonView()
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeplateGrid(
    themeplates: List<StudioThemeplate>,
    onThemeplateSelected: (StudioThemeplate) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (themeplates.isEmpty()) {
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
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 5.dp, top = 8.dp, end = 5.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = themeplates,
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

