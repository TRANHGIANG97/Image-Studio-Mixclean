package com.thgiang.image.core.design.components
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.theme.ImageDesign

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val radii = ImageDesign.radii
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val aiAccent = ImageDesign.semantic.aiAccent
    val shape = RoundedCornerShape(radii.large)
    val borderColor = if (isDarkTheme) {
        aiAccent.copy(alpha = 0.25f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val elevation = if (isDarkTheme) 2.dp else 8.dp

    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, clip = false)
            .drawBehind {
                if (isDarkTheme) {
                    // Subtle neon glow around the container for dark mode.
                    drawRoundRect(
                        color = aiAccent.copy(alpha = 0.12f),
                        cornerRadius = CornerRadius(radii.large.toPx(), radii.large.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            .clip(shape)
            .background(ImageDesign.surfaces.glass)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val radii = ImageDesign.radii
    val gradient = ImageDesign.gradients.cta
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(radii.medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (enabled) gradient else Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ShimmerGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val radii = ImageDesign.radii
    val gradient = ImageDesign.gradients.cta
    val contentColor = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 8.dp, 
                shape = RoundedCornerShape(radii.large), 
                spotColor = ImageDesign.semantic.aiAccent.copy(alpha = 0.3f)
            ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(radii.large),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) gradient else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)))
                .drawBehind {
                    if (enabled) {
                        val brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0f),
                            ),
                            start = Offset(shimmerOffset, 0f),
                            end = Offset(shimmerOffset + 200f, 200f)
                        )
                        drawRect(brush = brush)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = Color.White // CTA text always white on gradient
            )
        }
    }
}

@Composable
fun PremiumPlanCard(
    title: String,
    price: String,
    subtext: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    isBestValue: Boolean = false,
    discountBadge: String? = null
) {
    val isDark = isSystemInDarkTheme()
    val aiAccent = ImageDesign.semantic.aiAccent
    val radii = ImageDesign.radii
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = textColor.copy(alpha = 0.6f)
    val cardBg = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.large))
            .clickable { onSelect() }
            .then(
                if (isSelected && isBestValue) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(listOf(aiAccent, Color.Cyan, aiAccent)),
                        shape = RoundedCornerShape(radii.large)
                    )
                } else if (isSelected) {
                    Modifier.border(2.dp, aiAccent, RoundedCornerShape(radii.large))
                } else {
                    Modifier.border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(radii.large))
                }
            )
            .background(
                if (isSelected) aiAccent.copy(alpha = 0.12f)
                else cardBg
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    maxLines = 1
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor,
                    maxLines = 1
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) aiAccent else textColor
                    ),
                    maxLines = 1
                )
                if (discountBadge != null) {
                    StatusChip(
                        text = discountBadge, 
                        tone = if (isDark) Color(0xFFFFD700) else Color(0xFFC6A700)
                    )
                }
            }
        }
        
        if (isBestValue) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-20).dp)
                    .rotate(12f)
            ) {
                Text(
                    text = "BEST VALUE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFFF9800), Color(0xFFFF5722))),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun PremiumFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = textColor.copy(alpha = 0.6f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(ImageDesign.semantic.aiAccent.copy(alpha = 0.15f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ImageDesign.semantic.aiAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = subTextColor,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = ImageDesign.semantic.aiAccent
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.16f))
            .border(
                width = 1.dp,
                color = tone.copy(alpha = 0.35f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tone
        )
    }
}






@Composable
fun ModernRewardedAdDialog(
    count: Int,
    isLoading: Boolean,
    onWatchAd: () -> Unit,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    val aiAccent = ImageDesign.semantic.aiAccent
    val isDark = isSystemInDarkTheme()
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(aiAccent.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, aiAccent.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = aiAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Unlock Batch Mode",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color.White else Color.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Watch a short ad to process multiple images at once instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Indicator (Steps)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(1) { index ->
                        val isCompleted = index < count
                        Box(
                            modifier = Modifier
                                .size(if (isCompleted) 20.dp else 16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCompleted) aiAccent 
                                    else (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f)
                                )
                                .then(
                                    if (isCompleted) Modifier.shadow(8.dp, CircleShape, spotColor = aiAccent)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = aiAccent,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Ad...",
                        style = MaterialTheme.typography.labelMedium,
                        color = aiAccent
                    )
                } else {
                    ShimmerGradientButton(
                        text = "WATCH AD TO UNLOCK",
                        onClick = onWatchAd
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onUpgrade) {
                    Text(
                        text = "GO PRO - NO ADS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = aiAccent
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Maybe Later",
                        style = MaterialTheme.typography.bodySmall,
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
