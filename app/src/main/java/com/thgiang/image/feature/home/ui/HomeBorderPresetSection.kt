package com.thgiang.image.feature.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abizer_r.quickedit.utils.BorderGradientDirection
import com.abizer_r.quickedit.utils.BorderGradientPreset
import com.abizer_r.quickedit.utils.BorderGradientPresets
import com.abizer_r.quickedit.utils.BorderPreset
import com.abizer_r.quickedit.utils.BorderUtils
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeUiTokens
import com.thgiang.image.core.design.theme.HomeDarkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BorderPresetDock(
    selectedPresetId: String?,
    isProcessing: Boolean,
    useHomeDarkStyle: Boolean,
    onPresetClick: (BorderGradientPreset) -> Unit
) {
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    val selectedStroke = if (useHomeDarkStyle) HomeDarkStyle.accent else Color(0xFFB78B50)
    val assetMap = remember { buildBorderSampleAssets() }
    val borderMotion = rememberInfiniteTransition(label = "homeBorderPresetMotion")
    val gradientPhase by borderMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "homeBorderGradientPhase"
    )
    val selectedPulse by borderMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homeBorderSelectedPulse"
    )
    val revealMotion = rememberInfiniteTransition(label = "homeBorderRevealMotion")
    val revealPhase by revealMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "homeBorderRevealPhase"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.border_presets_title),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) {
                Color.White.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = HomeUiTokens.outerPadding, vertical = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = HomeUiTokens.outerPadding)
        ) {
            items(BorderGradientPresets.modernPresets) { preset ->
                val selected = selectedPresetId == preset.id
                val assetPath = assetMap[preset.id] ?: assetMap.values.first()
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val idleScale = if (selected) 1f + selectedPulse * 0.018f else 1f
                val scale by animateFloatAsState(
                    targetValue = if (pressed && !isProcessing) 0.96f else idleScale,
                    animationSpec = tween(120),
                    label = "borderPresetScale"
                )
                val animatedBorderBrush = buildHomeBorderGradientBrush(
                    preset = preset,
                    phase = gradientPhase,
                    alpha = if (selected) 1f else 0.78f
                )
                val selectedGlowBrush = buildHomeBorderGradientBrush(
                    preset = preset,
                    phase = (gradientPhase + 0.18f) % 1f,
                    alpha = 0.45f + selectedPulse * 0.35f
                )

                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(104.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(HomeUiTokens.cardRadius))
                        .background(if (isDark) Color(0xFF101214) else Color(0xFFF6F3EE))
                        .then(
                            if (selected) {
                                Modifier
                                    .border(4.dp, selectedGlowBrush, RoundedCornerShape(HomeUiTokens.cardRadius))
                                    .border(2.dp, selectedStroke, RoundedCornerShape(HomeUiTokens.cardRadius))
                            } else {
                                Modifier.border(
                                    1.5.dp,
                                    animatedBorderBrush,
                                    RoundedCornerShape(HomeUiTokens.cardRadius)
                                )
                            }
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = !isProcessing
                        ) {
                            onPresetClick(preset)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDark) Color(0xFF16191E) else Color(0xFFF7F3EC))
                    )

                    BorderSampleSticker(
                        assetPath = assetPath,
                        preset = preset,
                        revealPhase = revealPhase,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                            .padding(7.dp)
                    )

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

        if (isProcessing && selectedPresetId != null) {
            Text(
                text = stringResource(R.string.border_applying),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) {
                    Color.White.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun buildBorderSampleAssets(): Map<String, String> = mapOf(
    "aurora_mist" to "border/have-a-good-day_6122874.png",
    "neon_bloom" to "border/movie-ticket_6426920.png",
    "sunset_pulse" to "border/nothing_7590216.png",
    "velvet_sky" to "border/planner_8512483.png",
    "ocean_drive" to "border/planner_8512484.png",
    "peach_cloud" to "border/positive-vibes_8336001.png",
    "midnight_fade" to "border/stay-positive_7590154.png",
    "ember_glass" to "border/today_12337908.png"
)

@Composable
private fun BorderSampleSticker(
    assetPath: String,
    preset: BorderGradientPreset,
    revealPhase: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewBitmaps by produceState<PreviewBitmaps?>(initialValue = null, assetPath, preset.id) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    val source = BitmapFactory.decodeStream(input)
                        ?.copy(Bitmap.Config.ARGB_8888, true)
                        ?: return@runCatching null
                    try {
                        val previewWidth = 360
                        val previewHeight = 360
                        val plainPreview = createScaledPreviewBitmap(
                            source = source,
                            targetWidth = previewWidth,
                            targetHeight = previewHeight
                        )
                        val borderedPreview = BorderUtils.applyBorderToBitmap(
                            bitmap = plainPreview.copy(Bitmap.Config.ARGB_8888, true),
                            borderColorArgb = AndroidColor.BLACK,
                            borderGradientPreset = preset,
                            borderWidthPx = maxOf(plainPreview.width, plainPreview.height) / 12,
                            borderPreset = BorderPreset.SOLID,
                            previewMaxDimension = 360
                        ).getOrNull()
                        PreviewBitmaps(
                            plain = plainPreview,
                            bordered = borderedPreview
                        )
                    } finally {
                        source.recycle()
                    }
                }
            }.getOrNull()
        }
    }

    val plain = previewBitmaps?.plain
    val bordered = previewBitmaps?.bordered

    if (plain != null) {
        val borderReveal = borderRevealAlpha(revealPhase)
        val borderScale = borderRevealScale(revealPhase)

        Box(modifier = modifier) {
            Image(
                bitmap = plain.asImageBitmap(),
                contentDescription = preset.title,
                modifier = Modifier.fillMaxSize()
            )

            if (bordered != null) {
                Image(
                    bitmap = bordered.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(borderScale)
                        .graphicsLayer(alpha = borderReveal)
                )
            }
        }
    }
}

private data class PreviewBitmaps(
    val plain: Bitmap,
    val bordered: Bitmap?
)

private fun createScaledPreviewBitmap(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int
): Bitmap {
    val scale = minOf(
        targetWidth.toFloat() / source.width.toFloat(),
        targetHeight.toFloat() / source.height.toFloat()
    )
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
}

private fun borderRevealAlpha(revealPhase: Float): Float {
    return when {
        revealPhase < 0.25f -> 0f
        revealPhase < 0.40f -> ((revealPhase - 0.25f) / 0.15f).coerceIn(0f, 1f)
        revealPhase < 0.82f -> 1f
        revealPhase < 0.92f -> (1f - ((revealPhase - 0.82f) / 0.10f)).coerceIn(0f, 1f)
        else -> 0f
    }
}

private fun borderRevealScale(revealPhase: Float): Float {
    return when {
        revealPhase < 0.25f -> 0.96f
        revealPhase < 0.40f -> 0.96f + ((revealPhase - 0.25f) / 0.15f) * 0.04f
        revealPhase < 0.82f -> 1f
        revealPhase < 0.92f -> 1f
        else -> 0.96f
    }
}

private fun buildHomeBorderGradientBrush(
    preset: BorderGradientPreset,
    phase: Float = 0f,
    alpha: Float = 1f
): Brush {
    val colors = preset.colors.map { Color(it).copy(alpha = alpha.coerceIn(0f, 1f)) }
    val shift = 700f * phase
    return when (preset.direction) {
        BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> Brush.linearGradient(
            colors = colors,
            start = Offset(-shift, 700f + shift),
            end = Offset(700f - shift, shift)
        )
        BorderGradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> Brush.linearGradient(
            colors = colors,
            start = Offset(-shift, -shift),
            end = Offset(700f - shift, 700f - shift)
        )
    }
}
