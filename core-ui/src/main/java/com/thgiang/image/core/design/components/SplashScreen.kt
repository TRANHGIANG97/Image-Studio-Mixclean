package com.thgiang.image.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class Particle(
    val id: Int,
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    var alpha: Float = 1f
)

@Composable
fun AppSplashScreen(onAnimationFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubbleBehavior")
    
    // 1. Logo Floating Animation
    val bobbingOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = SineHoverEasing()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoBobbing"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = SineHoverEasing()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoBreathing"
    )

    // 2. Text Shimmer Animation
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    // 3. Spiral Loading Bar Animation
    val loadingOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingOffset"
    )

    // Explosion & Global states
    val contentAlpha = remember { Animatable(0f) }
    var isExploding by remember { mutableStateOf(false) }
    val particles = remember { mutableStateListOf<Particle>() }
    val explosionScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(durationMillis = 800, easing = LinearOutSlowInEasing))
        
        delay(3200) // Duration reduced slightly to keep it snappy
        
        isExploding = true
        
        val colors = listOf(Color(0xFF22D3EE), Color(0xFF7C3AED), Color(0xFFF472B6), Color.White)
        for (i in 0 until 35) {
            val angle = Random.nextFloat() * 2 * Math.PI
            val speed = Random.nextFloat() * 20f + 8f
            particles.add(
                Particle(
                    id = i,
                    x = 0f,
                    y = 0f,
                    vx = (Math.cos(angle) * speed).toFloat(),
                    vy = (Math.sin(angle) * speed).toFloat(),
                    color = colors.random()
                )
            )
        }
        
        launch {
            explosionScale.animateTo(3f, tween(durationMillis = 600, easing = LinearOutSlowInEasing))
        }
        
        launch {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 800) {
                particles.forEach { p ->
                    p.x += p.vx
                    p.y += p.vy
                    p.alpha *= 0.92f
                }
                delay(12)
            }
            onAnimationFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040915)),
        contentAlignment = Alignment.Center
    ) {
        if (!isExploding) {
            // MAIN CONTENT
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(contentAlpha.value)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_round),
                        contentDescription = null,
                        modifier = Modifier
                            .size(95.dp)
                            .graphicsLayer {
                                translationY = bobbingOffset.dp.toPx()
                                scaleX = breathingScale
                                scaleY = breathingScale
                            }
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Text(
                    text = "MixClean",
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF22D3EE), Color(0xFF7C3AED))
                        ),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    ),
                    modifier = Modifier.alpha(shimmerAlpha)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Remove Background",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.5.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                )
            }

            // SPIRAL LOADING BAR (Bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
                    .width(200.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF22D3EE), Color(0xFF7C3AED),
                            Color(0xFFF472B6), Color(0xFF22D3EE)
                        ),
                        start = Offset(loadingOffset, 0f),
                        end = Offset(loadingOffset + 200.dp.toPx(), 0f),
                        tileMode = TileMode.Repeated
                    )
                    drawRect(brush = brush)
                }
            }
        } else {
            // EXPLOSION CANVAS
            Canvas(modifier = Modifier.fillMaxSize()) {
                val alphaFading = (1f - explosionScale.value / 3f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = alphaFading * 0.8f),
                    radius = explosionScale.value * 160.dp.toPx(),
                    center = center
                )
                
                particles.forEach { p ->
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = 4.dp.toPx(),
                        center = Offset(center.x + p.x.dp.toPx(), center.y + p.y.dp.toPx())
                    )
                }
            }
        }
    }
}

class SineHoverEasing : Easing {
    override fun transform(fraction: Float): Float {
        return (Math.sin(fraction * Math.PI - Math.PI / 2).toFloat() + 1f) / 2f
    }
}
