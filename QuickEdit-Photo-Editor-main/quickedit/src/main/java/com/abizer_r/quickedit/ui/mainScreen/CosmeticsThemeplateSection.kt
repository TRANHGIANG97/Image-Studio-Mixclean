package com.abizer_r.quickedit.ui.mainScreen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.abizer_r.quickedit.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.model.StudioThemeplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CosmeticsThemeplateSection(
    modifier: Modifier = Modifier,
    onThemeplateSelected: (StudioThemeplate) -> Unit
) {
    val templates = remember { StudioThemeplates.all }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.cosmetics_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            items(templates, key = { it.id }) { themeplate ->
                CosmeticsThemeplateCard(
                    themeplate = themeplate,
                    onClick = { onThemeplateSelected(themeplate) }
                )
            }
        }
    }
}

@Composable
private fun CosmeticsThemeplateCard(
    themeplate: StudioThemeplate,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = themeplate.assetPath) {
        value = withContext(Dispatchers.IO) {
            loadAssetBitmap(context, themeplate.assetPath)
        }
    }

    Card(
        modifier = Modifier
            .width(72.dp)
            .aspectRatio(72f / 90f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            bitmapState.value?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(themeplate.titleResId),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE8E3DD)),
                contentAlignment = Alignment.Center
            ) {
            }
        }
    }
}

private fun loadAssetBitmap(context: Context, assetPath: String): Bitmap? {
    return runCatching {
        context.assets.open(assetPath).use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}
