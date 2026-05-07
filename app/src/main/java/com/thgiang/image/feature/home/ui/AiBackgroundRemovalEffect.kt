package com.thgiang.image.feature.home.ui
import android.app.ActivityManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import coil.compose.AsyncImage
import com.thgiang.image.core.design.components.TransparentBackgroundPattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class LightParticle(
    val angle: Float,
    val distanceFactor: Float,
    val radius: Float
)

private const val LOW_RAM_THRESHOLD_MB = 96
private const val TAG = "AiBackgroundRemovalEffect"

@Composable
fun AiBackgroundRemovalEffect(
    originalModel: Any?,
    processedImageUri: android.net.Uri?,
    isProcessing: Boolean,
    progress: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLowRam = remember {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.memoryClass?.let { it < LOW_RAM_THRESHOLD_MB } ?: false
    }
    val useHeavyAnimations = !isLowRam

    val cinematicEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val progressClamped = progress.coerceIn(0, 100)
    val currentUriIdentity = processedImageUri?.toString()?.hashCode() ?: 0

    var lastRevealedIdentity by remember { mutableIntStateOf(0) }
    var showProcessed by remember { mutableStateOf(false) }

    val pulseScale = remember { Animatable(1f) }
    val burstScale = remember { Animatable(0f) }
    val burstAlpha = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val particleProgress = remember { Animatable(0f) }
    val vignetteAlpha = remember { Animatable(0f) }
    val shakeOffsetX = remember { Animatable(0f) }

    val particles = remember(currentUriIdentity) {
        if (!useHeavyAnimations) emptyList()
        else List(16) {
            LightParticle(
                angle = Random.nextFloat() * (2f * PI.toFloat()),
                distanceFactor = Random.nextFloat() * 0.5f + 0.5f,
                radius = Random.nextFloat() * 6f + 3f
            )
        }
    }

    LaunchedEffect(originalModel) {
        if (processedImageUri == null) {
            showProcessed = false
            lastRevealedIdentity = 0
        }
    }

    LaunchedEffect(isProcessing, progressClamped, currentUriIdentity) {
        if (currentUriIdentity == 0 || isProcessing || progressClamped < 100) return@LaunchedEffect
        if (currentUriIdentity == lastRevealedIdentity) return@LaunchedEffect

        if (useHeavyAnimations) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(1.14f, tween(110, easing = cinematicEasing))
            pulseScale.animateTo(1f, tween(95, easing = cinematicEasing))

            burstScale.snapTo(0f)
            burstAlpha.snapTo(1f)
            flashAlpha.snapTo(1f)
            particleProgress.snapTo(0f)
            vignetteAlpha.snapTo(0.36f)
            shakeOffsetX.snapTo(0f)

            coroutineScope {
                launch { burstScale.animateTo(3f, tween(520, easing = cinematicEasing)) }
                launch { burstAlpha.animateTo(0f, tween(520, easing = cinematicEasing)) }
                launch { flashAlpha.animateTo(0f, tween(130, easing = cinematicEasing)) }
                launch { particleProgress.animateTo(1f, tween(620, easing = cinematicEasing)) }
                launch { vignetteAlpha.animateTo(0f, tween(620, easing = cinematicEasing)) }
                launch {
                    shakeOffsetX.animateTo(5f, tween(45, easing = cinematicEasing))
                    shakeOffsetX.animateTo(-4f, tween(55, easing = cinematicEasing))
                    shakeOffsetX.animateTo(3f, tween(50, easing = cinematicEasing))
                    shakeOffsetX.animateTo(0f, tween(70, easing = cinematicEasing))
                }
                launch {
                    delay(120)
                    showProcessed = true
                }
            }
        } else {
            showProcessed = true
        }
        lastRevealedIdentity = currentUriIdentity
    }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = showProcessed && processedImageUri != null,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(
                        animationSpec = tween(280, easing = cinematicEasing)
                    ) + scaleIn(
                        animationSpec = tween(360, easing = cinematicEasing),
                        initialScale = 0.96f
                    ),
                    initialContentExit = fadeOut(
                        animationSpec = tween(140, easing = cinematicEasing)
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { revealProcessed ->
            val model = if (revealProcessed) processedImageUri else originalModel
            if (model != null) {
                if (revealProcessed && processedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        TransparentBackgroundPattern(
                            modifier = Modifier.matchParentSize()
                        )
                        AsyncImage(
                            model = processedImageUri,
                            contentDescription = stringResource(R.string.cd_ai_removal_preview),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    AsyncImage(
                        model = model,
                        contentDescription = "AI background removal preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.home_preview_placeholder),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ✅ FIX: Tách Canvas vào layer riêng với kiểm tra size
        EffectOverlayLayer(
            burstAlpha = burstAlpha,
            burstScale = burstScale,
            pulseScale = pulseScale,
            flashAlpha = flashAlpha,
            particleProgress = particleProgress,
            vignetteAlpha = vignetteAlpha,
            particles = particles,
            useHeavyAnimations = useHeavyAnimations
        )

        // ✅ FIX: Shake + Blur effect riêng layer
        if (useHeavyAnimations) {
            ShakeAndBlurLayer(shakeOffsetX = shakeOffsetX)
        }

        FuturisticProgressBar(
            progress = progressClamped,
            visible = isProcessing || (progressClamped in 1..99),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        )
    }
}

/**
 * ✅ FIX: Riêng layer cho các effects (burst, particles, vignette, flash)
 * - Chỉ vẽ khi size > 0
 * - Không mix với animated translationX
 * - Không áp dụng RenderEffect ở layer này
 */
@Composable
private fun EffectOverlayLayer(
    burstAlpha: Animatable<Float, *>,
    burstScale: Animatable<Float, *>,
    pulseScale: Animatable<Float, *>,
    flashAlpha: Animatable<Float, *>,
    particleProgress: Animatable<Float, *>,
    vignetteAlpha: Animatable<Float, *>,
    particles: List<LightParticle>,
    useHeavyAnimations: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = size.minDimension

        // ✅ CRITICAL: Kiểm tra size trước mọi vẽ
        if (minDim <= 0f) {
            return@Canvas
        }

        // Vignette
        if (vignetteAlpha.value > 0f) {
            val safeVignetteRadius = max(minDim * 0.85f, 1f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = vignetteAlpha.value)
                    ),
                    center = center,
                    radius = safeVignetteRadius
                )
            )
        }

        // Burst
        if (burstAlpha.value > 0f) {
            val burstRadius = minDim * 0.2f * burstScale.value * pulseScale.value
            val safeBurstRadius = max(burstRadius, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFBAF3FF).copy(alpha = burstAlpha.value),
                        Color(0xFF4FD8FF).copy(alpha = burstAlpha.value * 0.75f),
                        Color(0x33248BFF),
                        Color.Transparent
                    ),
                    center = center,
                    radius = safeBurstRadius
                ),
                center = center,
                radius = safeBurstRadius,
                blendMode = BlendMode.Screen
            )
            drawCircle(
                color = Color(0xFF7EE8FF).copy(alpha = burstAlpha.value * 0.32f),
                center = center,
                radius = safeBurstRadius * 1.1f,
                blendMode = BlendMode.Plus
            )
        }

        // Flash
        if (flashAlpha.value > 0f) {
            drawRect(
                color = Color.White.copy(alpha = flashAlpha.value * 0.9f),
                blendMode = BlendMode.Screen
            )
        }

        // Particles
        if (particleProgress.value > 0f && particleProgress.value < 1f) {
            val maxDistance = minDim * 0.55f
            val particleAlpha = 1f - particleProgress.value
            particles.forEach { particle ->
                val distance = maxDistance * particle.distanceFactor * particleProgress.value
                val x = center.x + cos(particle.angle) * distance
                val y = center.y + sin(particle.angle) * distance
                drawCircle(
                    color = Color(0xFFB9F3FF).copy(alpha = particleAlpha),
                    radius = particle.radius,
                    center = Offset(x, y),
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

/**
 * ✅ FIX: Riêng layer cho shake + blur effect
 * - Chỉ áp dụng translationX animation ở layer này
 * - Kiểm tra Build version trước blur
 * - Wrap RenderEffect trong try-catch
 */
@Composable
private fun ShakeAndBlurLayer(shakeOffsetX: Animatable<Float, *>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = shakeOffsetX.value

                // ✅ FIX: Safe RenderEffect application
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        renderEffect = RenderEffect
                            .createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    } catch (e: Exception) {
                        Log.w(TAG, "RenderEffect blur failed, continuing without blur", e)
                        // Fallback: continue without blur effect
                    }
                }
            }
    )
}

@Composable
private fun FuturisticProgressBar(
    progress: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val progressFloat = (progress.coerceIn(0, 100) / 100f)
    val infinite = rememberInfiniteTransition(label = "progressShine")
    val shineShift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineShift"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
        ) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                color = Color(0x66202C40),
                cornerRadius = corner
            )

            val fillWidth = size.width * progressFloat
            if (fillWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF67F7FF),
                            Color(0xFF1E9DFF),
                            Color(0xFF94FFFF)
                        )
                    ),
                    size = Size(fillWidth, size.height),
                    cornerRadius = corner
                )

                val shineWidth = size.width * 0.18f
                val shineLeft = (fillWidth * shineShift) - (shineWidth / 2f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.65f),
                            Color.Transparent
                        ),
                        startX = shineLeft,
                        endX = shineLeft + shineWidth
                    ),
                    size = Size(fillWidth, size.height),
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
            }
        }

        Text(
            text = stringResource(R.string.progress_percent, progress),
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB9F3FF)
        )
    }
}




