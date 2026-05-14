package com.abizer_r.quickedit.ui.editorScreen

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    LaunchedEffect(key1 = Unit) {
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
            onBottomToolbarEvent = onBottomToolbarEvent,
            goToMainScreen = goToMainScreen,
            isPremium = isPremium,
            onSaveDraftClicked = onSaveDraftClicked
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
    onBottomToolbarEvent: (BottomToolbarEvent) -> Unit,
    goToMainScreen: () -> Unit,
    isPremium: Boolean = false,
    onSaveDraftClicked: (Bitmap) -> Unit = {}
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    val mOnBottomToolbarEvent = remember { { toolbarEvent: BottomToolbarEvent ->
        if (toolbarEvent is BottomToolbarEvent.OnItemClicked) {
            android.util.Log.d("RotateDebug", "Toolbar item clicked: ${toolbarEvent.toolbarItem}")
            coroutineScope.launch(Dispatchers.Main) {
                toolbarVisible = false
                android.util.Log.d("RotateDebug", "Toolbar hidden, waiting ${AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST}ms")
                delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
                android.util.Log.d("RotateDebug", "Delay done, calling onBottomToolbarEvent")
                onBottomToolbarEvent(toolbarEvent)
            }
        }
    } }

    val onCloseClickedLambda = remember { {
        // TODO - confirmation dialog (add throughout the app)
        goToMainScreen()
    } }

    BackHandler {
        onCloseClickedLambda()
    }
    val onSaveClickedLambda = remember { {
        val imgFile = File(context.filesDir, "edited_image.jpg")
        BitmapUtils.saveBitmap(currentBitmap, imgFile)
        FileUtils.saveFileToAppFolder(
            context = context,
            file = imgFile,
            onSuccess = { context.toast(R.string.image_saved_successfully) },
            onFailure = { context.toast(R.string.failed_to_save_image) },
        )
    } }

    val onShareClickedLambda = remember { {
        val imgFile = File(context.filesDir, "edited_image.jpg")
        BitmapUtils.saveBitmap(currentBitmap, imgFile)
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
                        type = "image/jpeg"
                    )
                } else {
                    context.toast(R.string.something_went_wrong)
                }
            },
            onFailure = { context.toast(R.string.failed_to_save_image) },
        )
    } }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomedOrPanned by remember {
        derivedStateOf { scale != 1f || offset != Offset.Zero }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange * scale
    }

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                onSaveDraftClicked = { onSaveDraftClicked(currentBitmap) }
            )
        }

        val topToolbarHeight =  TOOLBAR_HEIGHT_SMALL
        val bottomToolbarHeight = TOOLBAR_HEIGHT_MEDIUM

        val checkerboardBrush = rememberCheckerboardBrush()
        val aspectRatio = remember(currentBitmap) {
            currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
        }
        Box(
            modifier = Modifier
                .constrainAs(bgImage) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.wrapContent
                    height = Dimension.wrapContent
                }
                .padding(top = topToolbarHeight, bottom = bottomToolbarHeight)
                .aspectRatio(aspectRatio)
                .then(
                    if (currentBitmap.hasAlpha()) Modifier.background(checkerboardBrush)
                    else Modifier
                )
                .transformable(transformableState)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
        ) {

            Image(
                modifier = Modifier
                    .fillMaxSize(),
                bitmap = currentBitmap.asImageBitmap(),
                contentScale = ContentScale.Fit,
                contentDescription = null,
                alpha = 1f
            )

            // Reset zoom button
            if (isZoomedOrPanned) {
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        "Reset",
                        color = Color.White
                    )
                }
            }
        }



        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = bottomToolbarModifier(bottomToolbar)
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
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
            onBottomToolbarEvent = {},
            goToMainScreen = {}
        )
    }
}