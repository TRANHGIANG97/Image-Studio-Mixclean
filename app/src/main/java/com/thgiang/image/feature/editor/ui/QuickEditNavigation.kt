package com.thgiang.image.feature.editor.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abizer_r.quickedit.ui.SharedEditorViewModel
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundGradientPresets
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundModeScreen
import com.abizer_r.quickedit.ui.borderMode.BorderModeScreen
import com.abizer_r.quickedit.ui.cropMode.CropperScreen
import com.abizer_r.quickedit.ui.drawMode.DrawModeScreen
import com.abizer_r.quickedit.ui.editorScreen.EditorScreen
import com.abizer_r.quickedit.ui.editorScreen.EditorScreenState
import com.abizer_r.quickedit.ui.effectsMode.EffectsModeScreen
import com.abizer_r.quickedit.ui.magicBrush.MagicBrushScreen
import com.abizer_r.quickedit.ui.navigation.NavDestinations
import com.abizer_r.quickedit.ui.rotateMode.RotateModeScreen
import com.abizer_r.quickedit.ui.studioMode.StudioModeScreen
import com.abizer_r.quickedit.ui.textMode.TextModeScreen
import com.abizer_r.quickedit.utils.BorderGradientPresets
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils
import com.abizer_r.quickedit.utils.other.bitmap.BitmapStatus
import com.abizer_r.quickedit.utils.toast
import com.thgiang.image.app.MainActivity
import com.thgiang.image.app.navigation.Screen
import com.thgiang.image.core.design.components.BackgroundRemovalLoadingOverlay
import com.thgiang.image.core.design.components.ReviewPromptDialog
import com.thgiang.image.core.design.theme.ImageTheme
import com.thgiang.image.feature.home.ui.SingleImagePickerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PLAY_STORE_REVIEW_URL =
    "https://play.google.com/store/apps/details?id=com.thgiang.image"

private fun openPlayStore(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(PLAY_STORE_REVIEW_URL)
            )
        )
    }
}

private fun buildPickerRoute(autoRemoveBackground: Boolean, targetTool: String? = null): String {
    val safeTargetTool = targetTool.orEmpty()
    return Screen.SingleImagePicker.route +
        "?autoRemove=$autoRemoveBackground&backgroundGradientPresetId=&borderGradientPresetId=&targetTool=$safeTargetTool"
}

private fun launchMainWithRoute(context: Context, route: String) {
    context.startActivity(MainActivity.createIntent(context, route))
    (context as? android.app.Activity)?.finish()
}

@Composable
fun QuickEditEditorNavigation(
    viewModel: QuickEditViewModel,
    initialImageUri: Uri?,
    draftId: String? = null,
    autoRemoveBackground: Boolean = false,
    backgroundGradientPresetId: String? = null,
    borderGradientPresetId: String? = null,
    targetTool: String? = null,
    rewardedAdManager: com.thgiang.image.core.ad.RewardedAdManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val sharedEditorViewModel: SharedEditorViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val initialBackgroundGradientPreset = remember(backgroundGradientPresetId) {
        backgroundGradientPresetId?.let { id ->
            BackgroundGradientPresets.modernPresets.firstOrNull { it.id == id }
        }
    }
    val initialBorderGradientPreset = remember(borderGradientPresetId) {
        borderGradientPresetId?.let { id ->
            BorderGradientPresets.modernPresets.firstOrNull { it.id == id }
        }
    }

    // Load initial bitmap
    LaunchedEffect(initialImageUri, draftId) {
        viewModel.loadInitialBitmap(initialImageUri, draftId, sharedEditorViewModel)
    }

    // Collect event channel from ViewModel
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is QuickEditUiEvent.ShowToast -> {
                    context.toast(event.message)
                }
                QuickEditUiEvent.ShowSelfieFallbackWarning -> {
                    snackbarHostState.showSnackbar(
                        context.getString(com.thgiang.image.R.string.remove_bg_selfie_fallback_warning)
                    )
                }
                is QuickEditUiEvent.NavigateToBackground -> {
                    navController.navigate(
                        NavDestinations.BACKGROUND_MODE_SCREEN +
                                "?gradientPresetId=${event.presetId}"
                    )
                }
                is QuickEditUiEvent.NavigateToBorder -> {
                    navController.navigate(
                        NavDestinations.BORDER_MODE_SCREEN +
                                "?gradientPresetId=${event.presetId}"
                    )
                }
                is QuickEditUiEvent.NavigateToTool -> {
                    when (event.tool) {
                        "effects" -> navController.navigate(NavDestinations.EFFECTS_MODE_SCREEN)
                        "studio" -> navController.navigate(NavDestinations.STUDIO_MODE_SCREEN)
                        "magic" -> navController.navigate(NavDestinations.MAGIC_BRUSH_SCREEN)
                        "background" -> navController.navigate(NavDestinations.BACKGROUND_MODE_SCREEN)
                    }
                }
            }
        }
    }

    if (!uiState.bitmapLoaded) {
        BackgroundRemovalLoadingOverlay(
            modifier = Modifier.fillMaxSize(),
            message = ""
        )
        return
    }

    val goToCropModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.CROPPER_SCREEN)
    } }
    val goToDrawModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.DRAW_MODE_SCREEN)
    } }
    val goToTextModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.TEXT_MODE_SCREEN)
    } }
    val goToEffectsModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.EFFECTS_MODE_SCREEN)
    } }
    val goToBorderModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.BORDER_MODE_SCREEN)
    } }
    val goToStudioModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.STUDIO_MODE_SCREEN)
    } }

    // Auto-remove background on startup state
    var autoRemoveDone by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.bitmapLoaded, autoRemoveBackground) {
        if (uiState.bitmapLoaded && autoRemoveBackground && !autoRemoveDone) {
            autoRemoveDone = true
            viewModel.performBackgroundRemoval(
                sharedViewModel = sharedEditorViewModel,
                isAutoRemove = true,
                initialBackgroundGradientPresetId = backgroundGradientPresetId,
                initialBorderGradientPresetId = borderGradientPresetId,
                targetTool = targetTool
            )
        }
    }

    val goToRemoveBgScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        viewModel.performBackgroundRemoval(
            sharedViewModel = sharedEditorViewModel,
            isAutoRemove = false,
            initialBackgroundGradientPresetId = null,
            initialBorderGradientPresetId = null,
            targetTool = null
        )
    } }

    val goToBackgroundModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.BACKGROUND_MODE_SCREEN)
    } }
    val goToAddImageScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.ADD_IMAGE_PICKER_SCREEN)
    } }
    val goToMagicBrushScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.MAGIC_BRUSH_SCREEN)
    } }
    val goToRotateModeScreenNav = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.ROTATE_SCREEN)
    } }

    val onBackPressed = remember { {
        navController.navigateUp()
        Unit
    } }
    val onDoneClicked = remember { { bitmap: Bitmap ->
        sharedEditorViewModel.addBitmapToStack(
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false),
        )
        navController.navigate(NavDestinations.EDITOR_SCREEN) {
            popUpTo(NavDestinations.EDITOR_SCREEN) { inclusive = true }
        }
    } }

    var showSaveAdDialog by remember { mutableStateOf(false) }
    var isSaveAdLoading by remember { mutableStateOf(false) }
    var saveAdWatchCount by remember { mutableStateOf(0) }
    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val draftNamePrefix = stringResource(id = com.thgiang.image.R.string.draft_name_prefix)

    val onQuickEditSaveSuccess: (Uri?, File?) -> Unit = remember {
        { uri, file ->
            viewModel.onSaveSuccess(uri, file)
        }
    }

    val requestSaveAd: ((() -> Unit) -> Unit) = remember {
        { action ->
            action()
        }
    }

    val onSaveDraftClicked: (Bitmap) -> Unit = remember(draftNamePrefix) { { bitmap: Bitmap ->
        viewModel.saveDraft(bitmap, draftNamePrefix)
    } }

    NavHost(
        navController = navController,
        startDestination = NavDestinations.EDITOR_SCREEN,
    ) {
        composable(route = NavDestinations.EDITOR_SCREEN) { entry ->
            val pickedAddImageUri = entry.savedStateHandle.get<Uri>("add_image_uri")
            val ctx = LocalContext.current
            LaunchedEffect(pickedAddImageUri) {
                pickedAddImageUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        BitmapUtils.getScaledBitmap(ctx, uri).collect { status ->
                            if (status is BitmapStatus.Success) {
                                withContext(Dispatchers.Main) {
                                    sharedEditorViewModel.addBitmapToStack(
                                        bitmap = status.scaledBitmap.copy(Bitmap.Config.ARGB_8888, false),
                                        triggerRecomposition = true,
                                        addSafelyWithoutMultipleTriggers = false
                                    )
                                }
                            }
                        }
                    }
                    entry.savedStateHandle.remove<Uri>("add_image_uri")
                }
            }

            val visualState by sharedEditorViewModel.recompositionTrigger
                .collectAsStateWithLifecycle()
            val showOverlay by sharedEditorViewModel.showOverlay
                .collectAsStateWithLifecycle()

            val initialEditorState = EditorScreenState(
                sharedEditorViewModel.bitmapStack,
                sharedEditorViewModel.bitmapRedoStack,
                recompositionTrigger = visualState,
                showOverlay = showOverlay
            )
            Box(modifier = Modifier.fillMaxSize()) {
                EditorScreen(
                    modifier = Modifier.fillMaxSize(),
                    initialEditorScreenState = initialEditorState,
                    goToCropModeScreen = goToCropModeScreen,
                    goToDrawModeScreen = goToDrawModeScreen,
                    goToTextModeScreen = goToTextModeScreen,
                    goToEffectsModeScreen = goToEffectsModeScreen,
                    goToBorderModeScreen = goToBorderModeScreen,
                    goToStudioModeScreen = goToStudioModeScreen,
                    goToRemoveBgScreen = goToRemoveBgScreen,
                    goToBackgroundModeScreen = goToBackgroundModeScreen,
                    goToAddImageScreen = goToAddImageScreen,
                    goToMagicBrushScreen = goToMagicBrushScreen,
                    goToRotateModeScreen = goToRotateModeScreenNav,
                    goToMainScreen = {
                        sharedEditorViewModel.resetStacks()
                        (context as? android.app.Activity)?.finish()
                    },
                    isPremium = isPremium,
                    onSaveDraftClicked = onSaveDraftClicked,
                    onRequireSaveAd = requestSaveAd,
                    onSaveSuccess = { uri, file -> onQuickEditSaveSuccess(uri, file) }
                )

                if (uiState.isRemovingBg) {
                    BackgroundRemovalLoadingOverlay(
                        modifier = Modifier.fillMaxSize(),
                        message = uiState.autoRemoveStatusMessage,
                        showMessage = !uiState.autoRemoveStatusMessage.isNullOrEmpty()
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .zIndex(10f)
                )
            }
        }

        composable(route = NavDestinations.CROPPER_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                CropperScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                )
            }
        }

        composable(route = NavDestinations.DRAW_MODE_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                DrawModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                )
            }
        }

        composable(route = NavDestinations.TEXT_MODE_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                TextModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                )
            }
        }

        composable(route = NavDestinations.EFFECTS_MODE_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                EffectsModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                )
            }
        }

        composable(
            route = NavDestinations.BORDER_MODE_SCREEN + "?gradientPresetId={gradientPresetId}",
            arguments = listOf(
                navArgument("gradientPresetId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val gradientPresetId = entry.arguments
                ?.getString("gradientPresetId")
                ?.takeIf { it.isNotBlank() }
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                BorderModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                    initialGradientPresetId = gradientPresetId
                )
            }
        }

        composable(route = NavDestinations.STUDIO_MODE_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                StudioModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                    onCheckClicked = null
                )
            }
        }

        composable(route = NavDestinations.REMOVE_BG_MODE_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                com.abizer_r.quickedit.ui.removeBgMode.RemoveBgModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked
                )
            }
        }

        composable(
            route = NavDestinations.BACKGROUND_MODE_SCREEN + "?gradientPresetId={gradientPresetId}",
            arguments = listOf(
                navArgument("gradientPresetId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val pickedImageUri = entry.savedStateHandle.get<Uri>("background_image_uri")
            val gradientPresetId = entry.arguments
                ?.getString("gradientPresetId")
                ?.takeIf { it.isNotBlank() }
            var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val ctx = LocalContext.current

            LaunchedEffect(pickedImageUri) {
                pickedImageUri?.let { uri ->
                    val bitmap = BitmapUtils.getBitmapFromUri(ctx, uri)
                    pickedBitmap = bitmap
                    entry.savedStateHandle.remove<Uri>("background_image_uri")
                }
            }

            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                BackgroundModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                    onPickImageRequest = {
                        navController.navigate(NavDestinations.SINGLE_IMAGE_PICKER_SCREEN + "?autoRemove=false")
                    },
                    pickedImage = pickedBitmap,
                    initialGradientPresetId = gradientPresetId
                )
            }
        }

        composable(
            route = NavDestinations.SINGLE_IMAGE_PICKER_SCREEN + "?autoRemove={autoRemove}",
            arguments = listOf(
                navArgument("autoRemove") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val autoRemove = backStackEntry.arguments?.getBoolean("autoRemove") ?: false
            ImageTheme(darkTheme = false) {
                SingleImagePickerScreen(
                    onImageSelected = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("background_image_uri", uri)
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(route = NavDestinations.ADD_IMAGE_PICKER_SCREEN) {
            ImageTheme(darkTheme = false) {
                SingleImagePickerScreen(
                    onImageSelected = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("add_image_uri", uri)
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(route = NavDestinations.MAGIC_BRUSH_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                MagicBrushScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = { navController.navigateUp() },
                    onDoneClicked = { resultBitmap: Bitmap ->
                        sharedEditorViewModel.addBitmapToStack(
                            bitmap = resultBitmap.copy(Bitmap.Config.ARGB_8888, false),
                        )
                        navController.navigate(NavDestinations.EDITOR_SCREEN) {
                            popUpTo(NavDestinations.EDITOR_SCREEN) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(route = NavDestinations.ROTATE_SCREEN) {
            SafeScreenWrapper(sharedEditorViewModel, navController) { bmp ->
                RotateModeScreen(
                    immutableBitmap = ImmutableBitmap(bmp),
                    onBackPressed = onBackPressed,
                    onDoneClicked = onDoneClicked,
                )
            }
        }
    }


    if (uiState.showSaveSuccessScreen) {
        SaveSuccessScreen(
            savedImageUri = uiState.lastSavedImageUri,
            savedImageFile = uiState.lastSavedImageFile,
            onBack = { viewModel.dismissSaveSuccess() },
            onGoHome = {
                viewModel.dismissSaveSuccess()
                (context as? android.app.Activity)?.finish()
            },
            onCreateNew = {
                launchMainWithRoute(context, buildPickerRoute(autoRemoveBackground = true))
            },
            onOpenRemoveBg = {
                launchMainWithRoute(context, buildPickerRoute(autoRemoveBackground = true))
            },
            onBatchRemove = {
                launchMainWithRoute(context, Screen.BatchPicker.route)
            },
            onOpenEffects = {
                launchMainWithRoute(context, buildPickerRoute(autoRemoveBackground = true, targetTool = "effects"))
            },
            onOpenBackgroundPresets = {
                launchMainWithRoute(context, buildPickerRoute(autoRemoveBackground = true, targetTool = "background"))
            },
            onOpenStudio = {
                launchMainWithRoute(context, buildPickerRoute(autoRemoveBackground = true, targetTool = "studio"))
            },
            onOpenMagic = {
                launchMainWithRoute(context, buildPickerRoute(autoRemoveBackground = true, targetTool = "magic"))
            }
        )
    }

    if (uiState.showReviewPrompt) {
        ReviewPromptDialog(
            onRateNow = {
                viewModel.recordReviewAccepted()
                openPlayStore(context)
            },
            onLater = {
                viewModel.recordReviewDeclined()
            }
        )
    }

    if (showSaveAdDialog && rewardedAdManager != null) {
        val activity = context as? android.app.Activity
        com.thgiang.image.core.design.components.ModernRewardedAdDialog(
            count = saveAdWatchCount,
            isLoading = isSaveAdLoading,
            title = "Watch video to save",
            message = "Watch a short video before saving this image.",
            watchButtonText = "WATCH VIDEO",
            showUpgradeButton = false,
            onWatchAd = {
                activity?.let {
                    rewardedAdManager.showAd(
                        activity = it,
                        onRewardReceived = {
                            saveAdWatchCount = 1
                        },
                        onAdClosed = {
                            if (saveAdWatchCount >= 1) {
                                val action = pendingSaveAction
                                showSaveAdDialog = false
                                pendingSaveAction = null
                                saveAdWatchCount = 0
                                action?.invoke()
                            } else {
                                isSaveAdLoading = true
                                rewardedAdManager.loadAd(
                                    onLoaded = { isSaveAdLoading = false },
                                    onFailed = { isSaveAdLoading = false }
                                )
                            }
                        },
                        onFailedToShow = {
                            isSaveAdLoading = false
                        }
                    )
                }
            },
            onUpgrade = {
                showSaveAdDialog = false
                pendingSaveAction = null
            },
            onDismiss = {
                showSaveAdDialog = false
                pendingSaveAction = null
            }
        )
    }
}

@Composable
private fun SafeScreenWrapper(
    sharedEditorViewModel: SharedEditorViewModel,
    navController: androidx.navigation.NavController,
    content: @Composable (Bitmap) -> Unit
) {
    val bitmap = remember {
        runCatching { sharedEditorViewModel.getCurrentBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        content(bitmap)
    } else {
        LaunchedEffect(Unit) {
            navController.navigate(NavDestinations.EDITOR_SCREEN) {
                popUpTo(0) { inclusive = true }
            }
        }
        BackgroundRemovalLoadingOverlay(
            modifier = Modifier.fillMaxSize(),
            message = ""
        )
    }
}
