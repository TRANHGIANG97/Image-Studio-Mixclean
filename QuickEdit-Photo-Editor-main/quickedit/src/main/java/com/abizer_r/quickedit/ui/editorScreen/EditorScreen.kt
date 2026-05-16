package com.abizer_r.quickedit.ui.editorScreen

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.abizer_r.quickedit.theme.EditorAccent
import com.abizer_r.quickedit.theme.EditorAccentVariant
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.theme.QuickEditTheme
import com.abizer_r.quickedit.utils.ImmutableList
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.BottomToolBarStatic
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_MEDIUM
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarEvent
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import com.abizer_r.quickedit.ui.editorScreen.components.EditorToolButton
import com.abizer_r.quickedit.ui.editorScreen.components.EditorToolButtonTemplate
import com.abizer_r.quickedit.ui.editorScreen.topToolbar.EditorTopToolBar
import com.abizer_r.quickedit.utils.AppUtils
import com.abizer_r.quickedit.utils.FileUtils
import com.abizer_r.quickedit.utils.editorScreen.EditorScreenUtils
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils
import com.abizer_r.quickedit.utils.toast
import com.thgiang.image.core.ad.BannerAdView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    initialEditorScreenState: EditorScreenState,
    goToCropModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToDrawModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToTextModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToEffectsModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToBorderModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToStudioModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToBackgroundModeScreen: (finalEditorState: EditorScreenState) -> Unit = { },
    goToAddImageScreen: (finalEditorState: EditorScreenState) -> Unit = { },
    goToRemoveBgScreen: (finalEditorState: EditorScreenState) -> Unit = { },

    goToMagicBrushScreen: (finalEditorState: EditorScreenState) -> Unit = { },
    goToRotateModeScreen: (finalEditorState: EditorScreenState) -> Unit = { },
    goToMainScreen: () -> Unit,
    isPremium: Boolean = false,
    onSaveDraftClicked: (Bitmap) -> Unit = {}
) {
    if (initialEditorScreenState.bitmapStack.isEmpty()) {
        throw Exception("EmptyStackException: The bitmapStack of initial state should contain at least one bitmap")
    }

    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current

    val viewModel: EditorScreenViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner
    )

    LaunchedEffect(
        initialEditorScreenState.recompositionTrigger,
        initialEditorScreenState.bitmapStack.size,
        initialEditorScreenState.bitmapRedoStack.size,
        initialEditorScreenState.showOverlay
    ) {
        viewModel.updateInitialState(initialEditorScreenState)
    }

    if (state.bitmapStack.isNotEmpty()) {
        // Adding this check because the default state in viewModel will have empty stack
        // After updating the initialEditorScreenState, we will have non-empty stack
        val currentBitmap = viewModel.getCurrentBitmap()

        val onBottomToolbarEvent = remember { { toolbarEvent: BottomToolbarEvent ->
            if (toolbarEvent is BottomToolbarEvent.OnItemClicked) {
                when (toolbarEvent.toolbarItem) {
                    BottomToolbarItem.CropMode -> {
                        goToCropModeScreen(state)
                    }
                    BottomToolbarItem.DrawMode -> {
                        goToDrawModeScreen(state)
                    }
                    BottomToolbarItem.TextMode -> {
                        goToTextModeScreen(state)
                    }
                    BottomToolbarItem.EffectsMode -> {
                        goToEffectsModeScreen(state)
                    }
                    BottomToolbarItem.BorderMode -> {
                        goToBorderModeScreen(state)
                    }
                    BottomToolbarItem.StudioMode -> {
                        goToStudioModeScreen(state)
                    }
                    BottomToolbarItem.RemoveBg -> {
                        goToRemoveBgScreen(state)
                    }

                    BottomToolbarItem.MagicBrush -> {
                        goToMagicBrushScreen(state)
                    }
                    BottomToolbarItem.BackgroundMode -> {
                        goToBackgroundModeScreen(state)
                    }
                    BottomToolbarItem.AddImage -> {
                        goToAddImageScreen(state)
                    }
                    BottomToolbarItem.RotateItem -> {
                        android.util.Log.d("RotateDebug", "RotateItem clicked, navigating to rotate screen")
                        goToRotateModeScreen(state)
                        android.util.Log.d("RotateDebug", "goToRotateModeScreen called")
                    }
                    else -> {}
                }
            }
        } }

        EditorScreenLayout(
            modifier = modifier,
            currentBitmap = currentBitmap,
            undoEnabled = viewModel.undoEnabled(),
            redoEnabled = viewModel.redoEnabled(),
            onUndo = viewModel::onUndo,
            onRedo = viewModel::onRedo,
            onDeleteImage = {
                val transparentBitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                transparentBitmap.setHasAlpha(true)
                viewModel.addBitmapToStack(transparentBitmap)
            },
            onBottomToolbarEvent = onBottomToolbarEvent,
            goToMainScreen = goToMainScreen,
            isPremium = isPremium,
            onSaveDraftClicked = onSaveDraftClicked,
            showOverlay = state.showOverlay
        )
    }

}

@Composable
private fun EditorScreenLayout(
    modifier: Modifier,
    currentBitmap: Bitmap,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDeleteImage: () -> Unit,
    onBottomToolbarEvent: (BottomToolbarEvent) -> Unit,
    goToMainScreen: () -> Unit,
    isPremium: Boolean = false,
    onSaveDraftClicked: (Bitmap) -> Unit = {},
    showOverlay: Boolean = false
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val bottomToolbarItems = remember {
        ImmutableList(EditorScreenUtils.getDefaultBottomToolbarItemsList())
    }

    val topToolbarHeight =  TOOLBAR_HEIGHT_SMALL
    val bottomToolbarHeight = TOOLBAR_HEIGHT_MEDIUM

    var toolbarVisible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = Unit) { toolbarVisible = true }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                toolbarVisible = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mOnBottomToolbarEvent = remember(currentBitmap) { { toolbarEvent: BottomToolbarEvent ->
        if (toolbarEvent is BottomToolbarEvent.OnItemClicked) {
            android.util.Log.d("RotateDebug", "Toolbar item clicked: ${toolbarEvent.toolbarItem}")

            if (currentBitmap.width <= 1 || currentBitmap.height <= 1) {
                if (toolbarEvent.toolbarItem != BottomToolbarItem.AddImage) {
                    context.toast("Vui lòng thêm ảnh trước")
                    return@remember
                }
            }

            if (toolbarEvent.toolbarItem == BottomToolbarItem.RemoveBg) {
                onBottomToolbarEvent(toolbarEvent)
            } else {
                coroutineScope.launch(Dispatchers.Main) {
                    toolbarVisible = false
                    android.util.Log.d("RotateDebug", "Toolbar hidden, waiting ${AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST}ms")
                    delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
                    android.util.Log.d("RotateDebug", "Delay done, calling onBottomToolbarEvent")
                    onBottomToolbarEvent(toolbarEvent)
                }
            }
        }
    } }

    val onCloseClickedLambda = remember(undoEnabled, redoEnabled, onUndo) { {
        if (undoEnabled) {
            onUndo()
        } else {
            showExitConfirmDialog = true
        }
    } }

    BackHandler {
        onCloseClickedLambda()
    }
    val onSaveClickedLambda = remember(currentBitmap) { {
        val hasTransparency = BitmapUtils.hasTransparentPixels(currentBitmap)
        val extension = if (hasTransparency) "png" else "jpg"
        val format = if (hasTransparency) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        
        val imgFile = File(context.filesDir, "edited_image.$extension")
        BitmapUtils.saveBitmap(currentBitmap, imgFile, format = format)
        FileUtils.saveFileToAppFolder(
            context = context,
            file = imgFile,
            onSuccess = { context.toast(R.string.image_saved_successfully) },
            onFailure = { context.toast(R.string.failed_to_save_image) },
        )
    } }

    val onShareClickedLambda = remember(currentBitmap) { {
        val hasTransparency = BitmapUtils.hasTransparentPixels(currentBitmap)
        val extension = if (hasTransparency) "png" else "jpg"
        val format = if (hasTransparency) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (hasTransparency) "image/png" else "image/jpeg"

        val imgFile = File(context.filesDir, "edited_image.$extension")
        BitmapUtils.saveBitmap(currentBitmap, imgFile, format = format)
        FileUtils.saveFileToAppFolder(
            context = context,
            file = imgFile,
            onSuccess = {
                val uri = FileUtils.getUriForFile(context, imgFile)
                if (uri != null) {
                    AppUtils.shareOnApp(
                        context = context,
                        appName = null,
                        uri = uri,
                        type = mimeType
                    )
                } else {
                    context.toast(R.string.something_went_wrong)
                }
            },
            onFailure = { context.toast(R.string.failed_to_save_image) },
        )
    } }

    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomedOrPanned by remember {
        derivedStateOf { scale != 1f || offset != Offset.Zero || rotation != 0f }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, rotationChange ->
        val minScale = 0.1f // Allow zooming out for both opaque and transparent images
        scale = (scale * zoomChange).coerceIn(minScale, 5f)
        offset += panChange
        rotation += rotationChange
    }

    val checkerboardBrush = rememberCheckerboardBrush()
    val bgBrush = if (currentBitmap.hasAlpha()) checkerboardBrush else SolidColor(MaterialTheme.colorScheme.background)

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(EditorToolButtonTemplate.ToolbarBackgroundColor)
    ) {
        val (topToolbar, bottomToolbar, bgImage) = createRefs()

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolbar).statusBarsPadding()
        ) {
            EditorTopToolBar(
                modifier = Modifier,
                undoEnabled = undoEnabled,
                redoEnabled = redoEnabled,
                toolbarHeight = topToolbarHeight,
                saveEnabled = undoEnabled,
                onUndo = onUndo,
                onRedo = onRedo,
                onCloseClicked = onCloseClickedLambda,
                onSaveClicked = onSaveClickedLambda,
                onShareClicked = onShareClickedLambda,
                onSaveDraftClicked = { showSaveDraftDialog = true }
            )
        }

        if (showSaveDraftDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDraftDialog = false },
                title = { Text(stringResource(R.string.save_draft_confirm_title)) },
                text = { Text(stringResource(R.string.save_draft_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onSaveDraftClicked(currentBitmap)
                            showSaveDraftDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save_draft_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDraftDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showExitConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = { Text(stringResource(R.string.confirm_exit_title)) },
                text = { Text(stringResource(R.string.confirm_exit_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmDialog = false
                            goToMainScreen()
                        }
                    ) {
                        Text(stringResource(R.string.discard))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitConfirmDialog = false }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        val topToolbarHeight =  TOOLBAR_HEIGHT_SMALL
        val bottomToolbarHeight = TOOLBAR_HEIGHT_MEDIUM

        val aspectRatio = remember(currentBitmap) {
            currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
        }
        Box(
            modifier = Modifier
                .constrainAs(bgImage) {
                    top.linkTo(topToolbar.bottom)
                    bottom.linkTo(bottomToolbar.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .background(bgBrush),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(aspectRatio)
                    .transformable(transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = rotation
                    ),
                contentAlignment = Alignment.Center
            ) {

            Image(
                modifier = Modifier
                    .fillMaxSize(),
                bitmap = currentBitmap.asImageBitmap(),
                contentScale = ContentScale.Fit,
                contentDescription = null,
                alpha = 1f
            )

            // Pink Overlay (Subject Mask)
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = currentBitmap.asImageBitmap(),
                    contentScale = ContentScale.Fit,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFFFF2D55).copy(alpha = 0.6f))
                )
            }

            if (currentBitmap.width > 1 || currentBitmap.height > 1) {
                EditorToolButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Xoá ảnh",
                    onClick = onDeleteImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    compact = true,
                    containerColorOverride = EditorToolButtonTemplate.ToolbarBackgroundColor.copy(alpha = 0.92f)
                )
            }
            }

        }

        // Fixed Reset zoom button in the bottom left of working area
        if (isZoomedOrPanned) {
            EditorToolButton(
                icon = Icons.Rounded.RestartAlt,
                contentDescription = "Reset",
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                    rotation = 0f
                },
                modifier = Modifier
                    .constrainAs(createRef()) {
                        bottom.linkTo(bottomToolbar.top, margin = 16.dp)
                        start.linkTo(parent.start, margin = 16.dp)
                    },
                label = "Reset",
                containerColorOverride = EditorToolButtonTemplate.ToolbarBackgroundColor.copy(alpha = 0.92f)
            )
        }



        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = bottomToolbarModifier(bottomToolbar)
        ) {
            Column(
                modifier = Modifier
                    .background(EditorToolButtonTemplate.ToolbarBackgroundColor)
                    .navigationBarsPadding()
            ) {
                BottomToolBarStatic(
                    modifier = Modifier,
                    toolbarHeight = bottomToolbarHeight,
                    toolbarItems = bottomToolbarItems,
                    onEvent = mOnBottomToolbarEvent
                )
                if (!isPremium) {
                    BannerAdView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .padding(top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }


    }
}



@Composable
fun rememberCheckerboardBrush(): ShaderBrush {
    val density = LocalDensity.current
    val tilePx = with(density) { 8.dp.toPx().toInt().coerceAtLeast(1) }
    val size = tilePx * 2
    
    val bmp = remember(tilePx) {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = false }
        
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        
        paint.color = android.graphics.Color.parseColor("#EEEEEE")
        canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
        canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
        bitmap
    }
    
    return remember(bmp) {
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewEditorScreen() {
    QuickEditTheme {
        EditorScreenLayout(
            modifier = Modifier,
            currentBitmap = ImageBitmap.imageResource(id = R.drawable.placeholder_image_3).asAndroidBitmap(),
            undoEnabled = false,
            redoEnabled = false,
            onUndo = {},
            onRedo = {},
            onDeleteImage = {},
            onBottomToolbarEvent = {},
            goToMainScreen = {}
        )
    }
}
