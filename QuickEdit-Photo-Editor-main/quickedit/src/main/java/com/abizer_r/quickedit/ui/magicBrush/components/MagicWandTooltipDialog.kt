package com.abizer_r.quickedit.ui.magicBrush.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abizer_r.quickedit.R

@Composable
fun MagicWandTooltipDialog(
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.magic_wand_tooltip_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Auto Slider
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "slider")
                    val progress by infiniteTransition.animateFloat(
                        initialValue = 0.1f,
                        targetValue = 0.9f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "progress"
                    )

                    // Before image (full)
                    Image(
                        painter = painterResource(id = R.drawable.before_bird),
                        contentDescription = "Before",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // After image (clipped)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(left = 0f, top = 0f, right = size.width * progress, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.after_bird),
                            contentDescription = "After",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Divider line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .background(Color.White)
                            .offset(x = maxWidth * progress)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.magic_wand_tooltip_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.magic_wand_tooltip_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dont_show_again),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onDismiss(dontShowAgain) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.got_it))
                }
            }
        }
    }
}
