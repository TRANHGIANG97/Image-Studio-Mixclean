package com.abizer_r.quickedit.ui.removeBgMode

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.thgiang.image.core.data.backgroundremove.GooglePlayServicesHelper
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_EXTRA_LARGE
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap

@Composable
fun RemoveBgModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: RemoveBgModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.playServicesUpdateRecommended, state.selfieFallbackUsed) {
        when {
            state.playServicesUpdateRecommended -> {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.play_services_update_message),
                    actionLabel = context.getString(R.string.play_services_update_action),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    GooglePlayServicesHelper.openUpdatePage(context)
                }
                viewModel.clearUserNotices()
            }
            state.selfieFallbackUsed -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.remove_bg_selfie_fallback_warning),
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearUserNotices()
            }
        }
    }

    // Tooltip preference
    val prefs = remember { context.getSharedPreferences("remove_bg_prefs", Context.MODE_PRIVATE) }
    var showTooltip by remember { mutableStateOf(!prefs.getBoolean("hide_tooltip", false)) }

    LaunchedEffect(immutableBitmap) {
        viewModel.setInitialBitmap(immutableBitmap.bitmap)
    }

    var toolbarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        toolbarVisible = true
    }

    val onCloseClickedLambda = remember { {
        toolbarVisible = false
        onBackPressed()
    } }

    BackHandler {
        onCloseClickedLambda()
    }

    val onDoneClickedLambda = remember { {
        state.processedBitmap?.let { onDoneClicked(it) }
        Unit
    } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ConstraintLayout(
            modifier = Modifier.fillMaxSize(),
        ) {
        val (topToolBar, mainImage, bottomToolbar, navBarZone, tooltipRef) = createRefs()

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

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolBar)
        ) {
            Row(
                modifier = Modifier
                    .height(TOOLBAR_HEIGHT_SMALL)
                    .background(MaterialTheme.colorScheme.surface),
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
                        .clickable { onDoneClickedLambda() },
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.done),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        val currentBitmap = state.processedBitmap ?: immutableBitmap.bitmap

        Box(
            modifier = Modifier
                .constrainAs(mainImage) {
                    top.linkTo(topToolBar.bottom)
                    bottom.linkTo(navBarZone.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )

            // Pink Overlay (Subject Mask)
            AnimatedVisibility(
                visible = state.showOverlay && state.processedBitmap != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                state.processedBitmap?.let { pb ->
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = pb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color(0xFFFF2D55).copy(alpha = 0.6f))
                    )
                }
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingView(
                            modifier = Modifier.size(64.dp),
                            progressBarSize = 64.dp,
                            progressBarColor = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.loading),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (showTooltip && !state.isProcessing) {
            Box(
                modifier = Modifier
                    .constrainAs(tooltipRef) {
                        top.linkTo(topToolBar.bottom, margin = 16.dp)
                        start.linkTo(parent.start, margin = 24.dp)
                        end.linkTo(parent.end, margin = 24.dp)
                        width = Dimension.fillToConstraints
                    }
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.remove_bg_tooltip_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.remove_bg_tooltip_desc),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.remove_bg_tooltip_dismiss),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable {
                                prefs.edit().putBoolean("hide_tooltip", true).apply()
                                showTooltip = false
                            }
                            .padding(8.dp)
                    )
                }
            }
        }

        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}
