package com.abizer_r.quickedit.ui.mainScreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.thgiang.image.studio.model.StudioThemeplates
import com.thgiang.image.studio.ui.editor.SampleObjectCacheManager
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfessionalThemeplateSection(
    modifier: Modifier = Modifier,
    onThemeplateSelected: (StudioThemeplate) -> Unit
) {
    val templates = remember { StudioThemeplates.professional }
    val sections = remember { StudioThemeplates.professionalSections }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        ProfessionalThemeplateGroup(
            title = stringResource(R.string.professional_section_title),
            themeplates = templates,
            onThemeplateSelected = onThemeplateSelected
        )

        sections.forEach { section ->
            Spacer(modifier = Modifier.height(28.dp))
            ProfessionalThemeplateGroup(
                title = stringResource(section.titleResId),
                themeplates = section.themeplates,
                onThemeplateSelected = onThemeplateSelected
            )
        }
    }
}

@Composable
private fun ProfessionalThemeplateGroup(
    title: String,
    themeplates: List<StudioThemeplate>,
    onThemeplateSelected: (StudioThemeplate) -> Unit
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
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF2D55).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFFF2D55),
                    modifier = Modifier.size(18.dp)
                )
            }
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
    val context = LocalContext.current
    val cacheManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SampleObjectEntryPoint::class.java
        ).sampleObjectCacheManager()
    }

    val bgBitmapState = produceState<Bitmap?>(initialValue = null, key1 = themeplate.assetPath) {
        value = withContext(Dispatchers.IO) {
            loadProfessionalAssetBitmap(context, themeplate.assetPath)
        }
    }

    val fgBitmapState = produceState<Bitmap?>(initialValue = null, key1 = themeplate.objectSourceAssetPath) {
        themeplate.objectSourceAssetPath?.let { objSrcPath ->
            value = withContext(Dispatchers.IO) {
                val uri = cacheManager.getOrExtract(objSrcPath)
                if (uri != null) {
                    runCatching {
                        context.contentResolver.openInputStream(uri).use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }.getOrNull()
                } else {
                    null
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(3f / 4f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            bgBitmapState.value?.let { bgBitmap ->
                Image(
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditorColorPalette.PanelElevated),
                contentAlignment = Alignment.Center
            ) {
            }

            fgBitmapState.value?.let { fgBitmap ->
                Image(
                    bitmap = fgBitmap.asImageBitmap(),
                    contentDescription = stringResource(themeplate.titleResId),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private fun loadProfessionalAssetBitmap(context: Context, assetPath: String): Bitmap? {
    return runCatching {
        context.assets.open(assetPath).use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}
