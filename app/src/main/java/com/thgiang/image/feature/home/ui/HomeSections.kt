package com.thgiang.image.feature.home.ui
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thgiang.image.R
import com.thgiang.image.core.design.components.TransparentBackgroundPattern
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.design.theme.ImageDesign

@Composable
fun RemovalQualitySelector(
    preferredQuality: String,
    isPremium: Boolean,
    useHomeDarkStyle: Boolean,
    onSelectStandard: () -> Unit,
    onSelectPro: () -> Unit,
    onProLockedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    val chipShape = RoundedCornerShape(12.dp)
    val selectedBg = if (isDark) HomeDarkStyle.accent.copy(alpha = 0.25f) else Color(0xFF2D251C).copy(alpha = 0.08f)
    val unselectedBg = if (isDark) HomeDarkStyle.surfaceButton else Color.White.copy(alpha = 0.9f)
    val selectedText = if (isDark) HomeDarkStyle.accent else Color(0xFF2D251C)
    val unselectedText = if (isDark) HomeDarkStyle.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.quality_label),
            style = MaterialTheme.typography.labelLarge,
            color = if (useHomeDarkStyle) HomeDarkStyle.textPrimary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Text(
            text = stringResource(R.string.quality_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = if (useHomeDarkStyle) HomeDarkStyle.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(chipShape)
                    .background(if (preferredQuality == "standard") selectedBg else unselectedBg)
                    .border(
                        1.dp,
                        if (preferredQuality == "standard") selectedText.copy(alpha = 0.5f) else Color.Transparent,
                        chipShape
                    )
                    .clickable(onClick = onSelectStandard)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.quality_standard),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (preferredQuality == "standard") selectedText else unselectedText
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(chipShape)
                    .background(if (preferredQuality == "pro") selectedBg else unselectedBg)
                    .border(
                        1.dp,
                        if (preferredQuality == "pro") selectedText.copy(alpha = 0.5f) else Color.Transparent,
                        chipShape
                    )
                    .clickable(onClick = if (isPremium) onSelectPro else onProLockedClick)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPremium) stringResource(R.string.quality_pro) else stringResource(R.string.quality_pro_locked),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (preferredQuality == "pro") selectedText else unselectedText
                )
            }
        }
    }
}

/**
 * Công tắc Basic / Pro đặt trên bên phải, ngoài khung preview.
 * Trạng thái lưu qua [onQualityChange] -> DataStore (standard/pro).
 */
@Composable
fun BasicProToggle(
    preferredRemovalQuality: String,
    onQualityChange: (String) -> Unit,
    useHomeDarkStyle: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    val segmentShape = RoundedCornerShape(20.dp)
    val isBasic = preferredRemovalQuality != "pro"
    val selectedBg = if (isDark) HomeDarkStyle.accent.copy(alpha = 0.3f) else Color(0xFF2D251C).copy(alpha = 0.12f)
    val unselectedBg = if (isDark) HomeDarkStyle.surfaceButton else Color.White.copy(alpha = 0.85f)
    val selectedText = if (isDark) HomeDarkStyle.accent else Color(0xFF2D251C)
    val unselectedText = if (isDark) HomeDarkStyle.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(segmentShape)
            .background(unselectedBg)
            .border(1.dp, if (isDark) HomeDarkStyle.borderStrong.copy(alpha = 0.5f) else Color(0xFFD8CCB8).copy(alpha = 0.6f), segmentShape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (isBasic) selectedBg else Color.Transparent)
                .clickable(onClick = { onQualityChange("standard") })
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.quality_basic),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isBasic) selectedText else unselectedText
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (!isBasic) selectedBg else Color.Transparent)
                .clickable(onClick = { onQualityChange("pro") })
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.quality_pro),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (!isBasic) selectedText else unselectedText
            )
        }
    }
}

@Composable
fun HomeRemoveBackgroundSection(
    selectedImageUri: Uri?,
    processedImageUri: Uri?,
    processedHasAlpha: Boolean,
    isProcessing: Boolean,
    progress: Int,
    previewHeight: Dp,
    useHomeDarkStyle: Boolean,
    isAutoSliderEnabled: Boolean = false,
    lastSliderBeforeUri: Uri? = null,
    lastSliderAfterUri: Uri? = null,
    onPickImage: () -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)
    val cardBackground = when {
        useHomeDarkStyle -> HomeDarkStyle.surfaceElevated
        isSystemInDarkTheme() -> ImageDesign.surfaces.elevated
        else -> Color.White.copy(alpha = 0.92f)
    }
    val cardBorder = when {
        useHomeDarkStyle -> HomeDarkStyle.accent.copy(alpha = 0.22f)
        isSystemInDarkTheme() -> Color.White.copy(alpha = 0.08f)
        else -> Color(0xFFE2D9CA)
    }

    val containerModifier = Modifier
        .fillMaxWidth()
        .height(previewHeight)
        .shadow(
            elevation = 14.dp,
            shape = cardShape,
            clip = false
        )
        .clip(cardShape)
        .background(cardBackground)
        .border(1.dp, cardBorder, cardShape)

    Box(modifier = containerModifier) {
        when {
            isProcessing || (processedImageUri == null && selectedImageUri != null && progress > 0) -> {
                AiBackgroundRemovalEffect(
                    originalModel = selectedImageUri,
                    processedImageUri = processedImageUri,
                    isProcessing = isProcessing,
                    progress = progress,
                    modifier = Modifier.fillMaxSize()
                )
            }
            processedImageUri != null && !isProcessing -> {
                var compareSliderEnabled by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (compareSliderEnabled) {
                        BeforeAfterAutoSlider(
                            originalUri = selectedImageUri ?: processedImageUri,
                            processedUri = processedImageUri,
                            autoAnimate = isAutoSliderEnabled,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (processedHasAlpha) {
                                TransparentBackgroundPattern(modifier = Modifier.matchParentSize())
                            }
                            AsyncImage(
                                model = processedImageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    IconButton(
                        onClick = { compareSliderEnabled = !compareSliderEnabled },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = stringResource(R.string.compare_before_after),
                            tint = if (compareSliderEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (selectedImageUri == null) Modifier.clickable(onClick = onPickImage)
                            else Modifier
                        )
                ) {
                    if (selectedImageUri == null) {
                        BeforeAfterAutoSlider(
                            originalUri = lastSliderBeforeUri,
                            processedUri = lastSliderAfterUri,
                            autoAnimate = isAutoSliderEnabled,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        BeforeAfterAutoSliderDisplay(
                            originalUri = selectedImageUri,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreProcessActionRow(
    hasImage: Boolean,
    isProcessing: Boolean,
    useHomeDarkStyle: Boolean,
    onPickImage: () -> Unit,
    onRemoveBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillButton(
            label = stringResource(R.string.choose_image),
            icon = Icons.Default.Collections,
            secondary = !hasImage,
            isDark = useHomeDarkStyle || isSystemInDarkTheme(),
            useHomeDarkStyle = useHomeDarkStyle,
            modifier = Modifier.weight(1f),
            onClick = { if (!isProcessing) onPickImage() }
        )
        if (hasImage) {
            PillButton(
                label = if (isProcessing) {
                    stringResource(R.string.status_processing)
                } else {
                    stringResource(R.string.remove_background)
                },
                icon = Icons.Default.AutoFixHigh,
                secondary = false,
                isDark = useHomeDarkStyle || isSystemInDarkTheme(),
                useHomeDarkStyle = useHomeDarkStyle,
                modifier = Modifier.weight(1f),
                onClick = { if (!isProcessing) onRemoveBackground() }
            )
        }
    }
}

@Composable
fun PostProcessActionRow(
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    isSaving: Boolean,
    useHomeDarkStyle: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillButton(
            label = stringResource(R.string.action_edit),
            icon = Icons.Default.Edit,
            secondary = true,
            isDark = isDark,
            useHomeDarkStyle = useHomeDarkStyle,
            modifier = Modifier.weight(1f),
            onClick = onEdit
        )
        PillButton(
            label = stringResource(R.string.action_reset),
            icon = Icons.Default.Refresh,
            secondary = true,
            isDark = isDark,
            useHomeDarkStyle = useHomeDarkStyle,
            modifier = Modifier.weight(1f),
            onClick = onReset
        )
        PillButton(
            label = if (isSaving) stringResource(R.string.status_processing) else stringResource(R.string.multi_save),
            icon = Icons.Default.Check,
            secondary = false,
            isDark = isDark,
            useHomeDarkStyle = useHomeDarkStyle,
            modifier = Modifier.weight(1f),
            onClick = { if (!isSaving) onSave() }
        )
    }
}

// ✅ FIX: Renamed to avoid overload ambiguity
@Composable
fun BeforeAfterAutoSliderDisplay(
    originalUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    if (originalUri == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.status_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = originalUri,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun PillButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    compact: Boolean = false,
    isDark: Boolean = false,
    useHomeDarkStyle: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val primaryGradient = if (isDark || useHomeDarkStyle) {
        Brush.horizontalGradient(listOf(Color(0xFF2B2A2A), Color(0xFF5E4A2F)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF2D251C), Color(0xFF9A7641)))
    }
    val bg = when {
        !secondary -> Color.Transparent
        useHomeDarkStyle -> HomeDarkStyle.surfaceButton
        isDark -> Color(0xFF2C2C2C)
        else -> Color.White
    }
    val borderColor = when {
        !secondary -> Color.Transparent
        useHomeDarkStyle -> HomeDarkStyle.borderStrong
        isDark -> Color.White.copy(alpha = 0.12f)
        else -> Color(0xFFD8CCB8)
    }
    val contentColor = when {
        !secondary -> Color.White
        useHomeDarkStyle -> HomeDarkStyle.textPrimary
        isDark -> Color.White.copy(alpha = 0.92f)
        secondary -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconTint = when {
        !secondary -> Color.White
        useHomeDarkStyle -> HomeDarkStyle.textPrimary
        isDark -> Color.White.copy(alpha = 0.92f)
        secondary -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color(0xFF9A7641)
    }

    // ✅ FIX: Use proper clickable signature
    Box(
        modifier = modifier
            .height(if (compact) 40.dp else 50.dp)
            .clip(RoundedCornerShape(24.dp))

            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                tint = iconTint
            )
            if (!compact) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun BeforeAfterCompareSlider(
    originalUri: Uri?,
    processedUri: Uri?,
    processedHasAlpha: Boolean,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    val animatedSlider by animateFloatAsState(
        targetValue = sliderPosition,
        animationSpec = tween(durationMillis = 100),
        label = "sliderAnimation"
    )

    if (originalUri == null || processedUri == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.status_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val delta = dragAmount / size.width
                    sliderPosition = (sliderPosition + delta).coerceIn(0.05f, 0.95f)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val clipX = size.width * animatedSlider
                    clipRect(right = clipX) { this@drawWithContent.drawContent() }
                }
        ) {
            AsyncImage(
                model = originalUri,
                contentDescription = stringResource(R.string.status_idle),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val clipX = size.width * animatedSlider
                    clipRect(left = clipX) { this@drawWithContent.drawContent() }
                }
        ) {
            if (processedHasAlpha) {
                TransparentBackgroundPattern(modifier = Modifier.matchParentSize())
            }
            AsyncImage(
                model = processedUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val clipXDp = maxWidth * animatedSlider
            val isDark = isSystemInDarkTheme()
            val labelBg = Color.Black.copy(alpha = if (isDark) 0.42f else 0.36f)
            val dividerColor = if (isDark) HomeDarkStyle.accent else Color(0xFFD2A86A)

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = clipXDp - 2.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(dividerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = clipXDp - 14.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .border(1.dp, dividerColor.copy(alpha = 0.9f), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(2.dp)
                        .background(dividerColor, RoundedCornerShape(2.dp))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.compare_label_before),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .background(labelBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.compare_label_after),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .background(labelBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}




