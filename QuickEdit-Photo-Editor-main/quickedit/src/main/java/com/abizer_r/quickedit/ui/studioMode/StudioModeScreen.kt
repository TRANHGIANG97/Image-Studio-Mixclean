package com.abizer_r.quickedit.ui.studioMode
import com.abizer_r.quickedit.ui.studioMode.StudioModeViewModel.StudioEffect
import com.abizer_r.quickedit.ui.studioMode.StudioModeViewModel.StudioOption
import com.abizer_r.quickedit.ui.studioMode.StudioModeViewModel.StudioModeState

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
// ToolBarBackgroundColor removed from imports
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.ui.editorScreen.components.EditorToolButtonTemplate
import com.abizer_r.quickedit.utils.toast
import com.thgiang.image.core.util.processors.ProcessorUtils
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudioModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit,
    onCheckClicked: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: StudioModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(immutableBitmap) {
        viewModel.setInitialBitmap(immutableBitmap.bitmap)
    }

    val topToolbarHeight = TOOLBAR_HEIGHT_SMALL
    val bottomToolbarHeight = 100.dp

    var toolbarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        toolbarVisible = true
    }

    val onCloseClickedLambda = remember { {
        toolbarVisible = false
        onBackPressed()
    } }

    val onCheckClickedLambda = remember { {
        if (state.currentEffect == StudioEffect.PORTRAIT || state.currentEffect == StudioEffect.BLUR_SUBJECT) {
            scope.launch {
                val finalBmp = viewModel.renderFinalForExport()
                if (finalBmp != null) onCheckClicked?.invoke(finalBmp)
            }
        } else {
            state.processedBitmap?.let { onCheckClicked?.invoke(it) }
        }
        Unit
    } }

    BackHandler {
        onCloseClickedLambda()
    }

    val onDoneClickedLambda = remember { {
        if (state.currentEffect == StudioEffect.PORTRAIT || state.currentEffect == StudioEffect.BLUR_SUBJECT) {
            scope.launch {
                val finalBmp = viewModel.renderFinalForExport()
                if (finalBmp != null) onDoneClicked(finalBmp)
            }
        } else {
            state.processedBitmap?.let { onDoneClicked(it) }
        }
        Unit
    } }

    val sliderValue = remember(state.intensity) { mutableStateOf(state.intensity) }

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(EditorToolButtonTemplate.ToolbarBackgroundColor)
            .statusBarsPadding()
    ) {
        val (topToolBar, mainImage, sliderRef, bottomToolbar, navBarZone) = createRefs()

        // Inviolable Zone for System Navigation Keys
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .constrainAs(navBarZone) {
                    bottom.linkTo(parent.bottom)
                    width = Dimension.matchParent
                    height = Dimension.wrapContent
                }
                .navigationBarsPadding()
        )

        val showSlider = state.currentEffect != StudioModeViewModel.StudioEffect.NONE

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolBar)
        ) {
            Row(
                modifier = Modifier
                    .height(topToolbarHeight)
                    .background(EditorToolButtonTemplate.ToolbarBackgroundColor),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .size(32.dp)
                        .clickable { onCloseClickedLambda() },
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .size(32.dp)
                        .clickable {
                            if (onCheckClicked != null) {
                                onCheckClickedLambda()
                            } else {
                                onDoneClickedLambda()
                            }
                        },
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.done),
                    tint = if (onCheckClicked != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onBackground
                )
            }
        }

    val currentBitmap = state.processedBitmap ?: immutableBitmap.bitmap
    val hasForegroundBackground = !ProcessorUtils.hasMeaningfulTransparency(currentBitmap)
    val aspectRatio = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()

        Box(
            modifier = Modifier
                .constrainAs(mainImage) {
                    top.linkTo(topToolBar.bottom)
                    bottom.linkTo(if (showSlider) sliderRef.top else bottomToolbar.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageRatio = aspectRatio
                val stageRatio = if (maxHeight.value > 0f) maxWidth / maxHeight else imageRatio
                val displaySize = if (imageRatio > stageRatio) {
                    val width = maxWidth
                    width to (width / imageRatio)
                } else {
                    val height = maxHeight
                    (height * imageRatio) to height
                }

                Box(
                    modifier = Modifier.size(displaySize.first, displaySize.second),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingView(
                        modifier = Modifier.fillMaxSize(),
                        progressBarSize = 96.dp,
                        progressBarColor = Color.White
                    )
                }
            }
        }

        // Intensity slider - refined UI container
        if (showSlider) {
            Surface(
                modifier = Modifier
                    .constrainAs(sliderRef) {
                        bottom.linkTo(bottomToolbar.top, margin = 8.dp)
                        start.linkTo(parent.start, margin = 24.dp)
                        end.linkTo(parent.end, margin = 24.dp)
                        width = Dimension.fillToConstraints
                    },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.studio_intensity),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(sliderValue.value * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    Slider(
                        value = sliderValue.value,
                        onValueChange = { 
                            sliderValue.value = it
                            viewModel.updateIntensity(it)
                        },
                        onValueChangeFinished = {
                            // Already handled in real-time
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = Modifier.constrainAs(bottomToolbar) {
                bottom.linkTo(navBarZone.top)
                width = Dimension.matchParent
                height = Dimension.wrapContent
            }
        ) {
            StudioOptionsList(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomToolbarHeight)
                    .background(EditorToolButtonTemplate.ToolbarBackgroundColor),
                selectedEffect = state.currentEffect,
                canUseForegroundEffects = hasForegroundBackground,
                onBlockedEffectAttempt = {
                    context.toast(R.string.studio_effect_requires_background)
                },
                onEffectSelected = { viewModel.applyEffect(it) }
            )
        }
    }
}

@Composable
fun StudioOptionsList(
    modifier: Modifier = Modifier,
    selectedEffect: StudioEffect,
    canUseForegroundEffects: Boolean,
    onBlockedEffectAttempt: () -> Unit,
    onEffectSelected: (StudioEffect) -> Unit
) {
    val options = listOf(
        StudioOption(StudioEffect.NONE, stringResource(R.string.original), Icons.Outlined.Image),
        StudioOption(StudioEffect.BLUR, stringResource(R.string.blur), Icons.Outlined.BlurOn),
        StudioOption(StudioEffect.PORTRAIT, stringResource(R.string.portrait), Icons.Outlined.FaceRetouchingNatural),
        StudioOption(StudioEffect.BLUR_SUBJECT, stringResource(R.string.blur_subject), Icons.Outlined.Face),
        StudioOption(StudioEffect.CLEAN, stringResource(R.string.clean), Icons.Outlined.AutoFixHigh),
        StudioOption(StudioEffect.DARKEN, stringResource(R.string.darken), Icons.Outlined.DarkMode)
    )

    LazyRow(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(options) { option ->
            val blocked = !canUseForegroundEffects && (
                option.effect == StudioEffect.PORTRAIT ||
                option.effect == StudioEffect.CLEAN ||
                option.effect == StudioEffect.DARKEN
            )
            StudioOptionItem(
                option = option,
                isSelected = selectedEffect == option.effect,
                onClick = {
                    if (blocked) {
                        onBlockedEffectAttempt()
                    } else {
                        onEffectSelected(option.effect)
                    }
                }
            )
        }
    }
}

@Composable
fun StudioOptionItem(
    option: StudioOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.1f)
                    }
                )
                .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.title,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = option.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
