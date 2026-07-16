package com.abizer_r.quickedit.ui.mainScreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abizer_r.quickedit.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.model.StudioThemeplateSection
import com.thgiang.image.studio.ui.editor.SampleObjectCacheManager
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.thgiang.image.studio.util.CloudFirstSubcomposeAsyncImage

@Composable
fun ProfessionalThemeplateSection(
    templates: List<StudioThemeplate>,
    sections: List<StudioThemeplateSection>,
    modifier: Modifier = Modifier,
    onOpenGallery: (categoryId: String) -> Unit = {},
    onThemeplateSelected: (StudioThemeplate) -> Unit
) {
    if (templates.isEmpty() && sections.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 11.dp)) {
        ProfessionalThemeplateGroup(
            title = stringResource(com.thgiang.image.studio.R.string.themeplate_section_fashion),
            themeplates = templates,
            onThemeplateSelected = onThemeplateSelected,
            onArrowClick = { onOpenGallery("professional") }
        )

        sections.forEachIndexed { index, section ->
            Spacer(modifier = Modifier.height(28.dp))
            ProfessionalThemeplateGroup(
                title = if (section.titleResId != 0) stringResource(section.titleResId) else section.titleString.orEmpty(),
                themeplates = section.themeplates,
                onThemeplateSelected = onThemeplateSelected,
                onArrowClick = { onOpenGallery(section.id) }
            )
        }
    }
}

@Composable
private fun ProfessionalThemeplateGroup(
    title: String,
    themeplates: List<StudioThemeplate>,
    onThemeplateSelected: (StudioThemeplate) -> Unit,
    onArrowClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.home_see_all),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = Color(0xFF26C6DA),
                modifier = Modifier.clickable { onArrowClick() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(themeplates, key = { it.id }) { themeplate ->
                ProfessionalThemeplateCard(
                    themeplate = themeplate,
                    onClick = { onThemeplateSelected(themeplate) }
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SampleObjectEntryPoint {
    fun sampleObjectCacheManager(): SampleObjectCacheManager
}

@Composable
private fun ProfessionalThemeplateCard(
    themeplate: StudioThemeplate,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .width(90.dp)
            .aspectRatio(4f / 5f)
            .scale(scale)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CloudFirstSubcomposeAsyncImage(
                sourcePath = themeplate.assetPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(EditorColorPalette.PanelElevated)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(EditorColorPalette.PanelElevated)
                    )
                }
            )

            themeplate.objectSourceAssetPath?.let { objSrcPath ->
                CloudFirstSubcomposeAsyncImage(
                    sourcePath = objSrcPath,
                    contentDescription = themeplate.titleString ?: stringResource(themeplate.titleResId),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {},
                    error = {}
                )
            }

            if (themeplate.isPremium) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 4.dp)
                        .background(Color(0xFFFFB300), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "PRO",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
