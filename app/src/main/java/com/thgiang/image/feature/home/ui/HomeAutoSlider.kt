package com.thgiang.image.feature.home.ui
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thgiang.image.R
import com.thgiang.image.core.design.components.TransparentBackgroundPattern
import com.thgiang.image.core.design.theme.ImageDesign

/** Ảnh chưa xoá phông (before) — dùng khi chưa có cặp ảnh từ user, có thể tái sử dụng cho màn khác */
private const val DEMO_IMG_BEFORE = "rem_home_demo/img1.jpg"
/** Ảnh đã xoá phông (after) — dùng khi chưa có cặp ảnh từ user, có thể tái sử dụng cho màn khác */
private const val DEMO_IMG_AFTER = "rem_home_demo/img2.png"

/**
 * Slider before/after tự chạy qua lại.
 * - Khi [originalUri] và [processedUri] đều null: dùng ảnh demo từ assets (img1.jpg, img2.png); nếu load lỗi thì hiển thị placeholder.
 * - Khi có [originalUri] (và optional [processedUri]): dùng cặp ảnh từ user (Uri).
 */
@Composable
fun BeforeAfterAutoSlider(
    originalUri: Uri? = null,
    processedUri: Uri? = null,
    autoAnimate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiColor = ImageDesign.semantic.aiAccent.copy(alpha = 0.5f)
    val useDemoImages = originalUri == null && processedUri == null

    val infiniteTransition = rememberInfiniteTransition(label = "beforeAfterSlider")
    val progress by if (autoAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(7000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sliderProgress"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    val beforeAlpha = ((progress - 0.25f) * 5f).coerceIn(0f, 1f)
    val afterAlpha = ((0.75f - progress) * 5f).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val clipWidthDp = maxWidth * progress
        val progressFraction = progress

        val showContent = useDemoImages || originalUri != null
        if (showContent) {
            val beforeModel = if (useDemoImages) "file:///android_asset/$DEMO_IMG_BEFORE" else originalUri
            val afterModel = if (useDemoImages) "file:///android_asset/$DEMO_IMG_AFTER" else (processedUri ?: originalUri)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f))
                            )
                        )
                    }
            ) {
                AsyncImage(
                    model = beforeModel,
                    contentDescription = "Before",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(left = 0f, top = 0f, right = size.width * progressFraction, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }
                ) {
                    if (useDemoImages || processedUri != null) {
                        TransparentBackgroundPattern(modifier = Modifier.matchParentSize())
                    }
                    AsyncImage(
                        model = afterModel,
                        contentDescription = "After",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = clipWidthDp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.compare_label_before),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White.copy(alpha = beforeAlpha),
                        modifier = Modifier
                            .offset(x = (-48).dp)
                            .background(
                                Color.Black.copy(alpha = 0.4f * beforeAlpha),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = stringResource(R.string.compare_label_after),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White.copy(alpha = afterAlpha),
                        modifier = Modifier
                            .offset(x = 8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.4f * afterAlpha),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(aiColor)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ImageDesign.surfaces.elevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tap_remove_background_to_start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}




