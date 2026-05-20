package com.thgiang.image.feature.home.ui
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeDarkStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundGradientPreset
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundGradientPresets
import com.abizer_r.quickedit.ui.backgroundMode.GradientDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PresetDock(
    isPremium: Boolean,
    onLockedClick: () -> Unit,
    onPresetClick: (BackgroundGradientPreset) -> Unit,
    useHomeDarkStyle: Boolean = false,
    isProcessing: Boolean = false,
    selectedPresetId: String? = null,
) {
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    val previewAssets = remember { buildPresetPreviewAssets() }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_background_presets),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            itemsIndexed(BackgroundGradientPresets.modernPresets) { index, preset ->
                val selected = selectedPresetId == preset.id
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed && !isProcessing) 0.96f else 1f,
                    animationSpec = tween(120),
                    label = "presetScale"
                )
                val context = LocalContext.current
                val previewAsset = previewAssets.getOrNull(index)
                val previewBitmapState = produceState<Bitmap?>(initialValue = null, key1 = previewAsset) {
                    value = previewAsset?.let { assetPath ->
                        withContext(Dispatchers.IO) {
                            loadAssetBitmap(context, assetPath)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(90.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF101214) else Color(0xFFF6F3EE))
                        .then(
                            if (selected) Modifier.border(
                                2.dp,
                                if (useHomeDarkStyle) HomeDarkStyle.accent else Color(0xFFB78B50),
                                RoundedCornerShape(12.dp)
                            ) else Modifier.border(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = !isProcessing
                        ) {
                            onPresetClick(preset)
                        }
                ) {
                    if (previewBitmapState.value != null) {
                        Image(
                            bitmap = previewBitmapState.value!!.asImageBitmap(),
                            contentDescription = preset.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(buildHomeGradientBrush(preset))
                        )
                    }

                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (isProcessing) {
        Text(
            text = stringResource(R.string.home_applying_preset),
            style = MaterialTheme.typography.labelMedium,
            color = if (isDark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun buildHomeGradientBrush(preset: BackgroundGradientPreset): Brush {
    val colors = preset.colors.map { Color(it) }
    return when (preset.direction) {
        GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> Brush.linearGradient(
            colors = colors,
            start = Offset(0f, 700f),
            end = Offset(700f, 0f)
        )
        GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> Brush.linearGradient(
            colors = colors,
            start = Offset.Zero,
            end = Offset(700f, 700f)
        )
    }
}

private fun buildPresetPreviewAssets(): List<String> {
    return (1..12).map { index -> "background_editor/$index.jpg" }
}

private fun loadAssetBitmap(context: Context, assetPath: String): Bitmap? {
    return runCatching {
        context.assets.open(assetPath).use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}




