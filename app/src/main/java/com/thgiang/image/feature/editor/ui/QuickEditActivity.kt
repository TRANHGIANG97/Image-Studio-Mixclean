package com.thgiang.image.feature.editor.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.StrictMode
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.thgiang.image.core.util.processors.ProcessorUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.abizer_r.quickedit.theme.QuickEditTheme
import com.abizer_r.quickedit.ui.SharedEditorViewModel
import com.abizer_r.quickedit.ui.cropMode.CropperScreen
import com.abizer_r.quickedit.ui.drawMode.DrawModeScreen
import com.abizer_r.quickedit.ui.editorScreen.EditorScreen
import com.abizer_r.quickedit.ui.editorScreen.EditorScreenState
import com.abizer_r.quickedit.ui.effectsMode.EffectsModeScreen
import com.abizer_r.quickedit.ui.navigation.NavDestinations
import com.abizer_r.quickedit.ui.textMode.TextModeScreen
import com.abizer_r.quickedit.ui.borderMode.BorderModeScreen
import com.abizer_r.quickedit.ui.studioMode.StudioModeScreen
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundModeScreen
import com.abizer_r.quickedit.ui.magicBrush.MagicBrushScreen
import com.abizer_r.quickedit.ui.rotateMode.RotateModeScreen
import com.abizer_r.quickedit.backgroundremove.ModNetBackgroundRemoverRepository
import com.thgiang.image.feature.home.ui.SingleImagePickerScreen
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import com.abizer_r.quickedit.utils.other.bitmap.BitmapStatus
import com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils
import dagger.hilt.android.AndroidEntryPoint
import java.util.Stack
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.ui.zIndex
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.diagnostics.ImageProcessingCrashReporter
import com.thgiang.image.feature.editor.model.DraftManager
import com.thgiang.image.feature.editor.model.LayerSnapshot
import com.thgiang.image.feature.editor.model.ProjectSnapshot
import com.abizer_r.quickedit.utils.other.QuickToolsPortraitClassifier
import com.abizer_r.quickedit.utils.toast
import com.thgiang.image.core.design.components.BackgroundRemovalLoadingOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.thgiang.image.feature.premium.domain.PremiumRepository
import java.util.UUID
import java.io.File
import javax.inject.Inject
import android.util.Log

private const val TAG = "QuickEditRemoveBg"
private const val QUICK_TOOLS_FACE_DETECTION_TIMEOUT_MS = 10_000L
private const val QUICK_TOOLS_REMOVE_BG_TIMEOUT_MS = 45_000L

@AndroidEntryPoint
class QuickEditActivity : AppCompatActivity() {

    @Inject
    lateinit var draftManager: DraftManager

    @Inject
    lateinit var backgroundRemoverRepository: BackgroundRemoverRepository

    @Inject
    lateinit var hairDetailBackgroundRemoverRepository: ModNetBackgroundRemoverRepository

    @Inject
    lateinit var rewardedAdManager: com.thgiang.image.core.ad.RewardedAdManager

    @Inject
    lateinit var premiumRepository: PremiumRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(EXTRA_IMAGE_URI)
        val draftId = if (intent.hasExtra(EXTRA_DRAFT_ID)) intent.getStringExtra(EXTRA_DRAFT_ID) else null
        val autoRemoveBg = intent.getBooleanExtra(EXTRA_AUTO_REMOVE_BG, false)
        Log.d(TAG, "onCreate autoRemoveBackground=$autoRemoveBg draftId=$draftId uri=$uri")

        setContent {
            QuickEditTheme {
                QuickEditEditorNavigation(
                    initialImageUri = uri,
                    draftId = draftId,
                    draftManager = draftManager,
                    autoRemoveBackground = autoRemoveBg,
                    backgroundRemoverRepository = backgroundRemoverRepository,
                    hairDetailBackgroundRemoverRepository = hairDetailBackgroundRemoverRepository,
                    rewardedAdManager = rewardedAdManager,
                    premiumRepository = premiumRepository
                )
            }
        }
    }

    companion object {
        private const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_DRAFT_ID = "extra_draft_id"
        private const val EXTRA_AUTO_REMOVE_BG = "extra_auto_remove_bg"

        fun createIntent(
            context: Context,
            uri: Uri? = null,
            draftId: String? = null,
            autoRemoveBackground: Boolean = false
        ): Intent {
            return Intent(context, QuickEditActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, uri)
                putExtra(EXTRA_DRAFT_ID, draftId)
                putExtra(EXTRA_AUTO_REMOVE_BG, autoRemoveBackground)
            }
        }
    }
}

@Composable
fun QuickEditEditorNavigation(
    initialImageUri: Uri?,
    draftId: String? = null,
    draftManager: DraftManager,
    autoRemoveBackground: Boolean = false,
    backgroundRemoverRepository: BackgroundRemoverRepository? = null,
    hairDetailBackgroundRemoverRepository: ModNetBackgroundRemoverRepository? = null,
    rewardedAdManager: com.thgiang.image.core.ad.RewardedAdManager? = null,
    premiumRepository: PremiumRepository? = null
) {
    val isPremium by (premiumRepository?.isPremium ?: kotlinx.coroutines.flow.flowOf(false))
        .collectAsStateWithLifecycle(initialValue = false)

    val navController = rememberNavController()
    val sharedEditorViewModel: SharedEditorViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmapLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(initialImageUri, draftId) {
        if (draftId != null) {
            val snapshot = draftManager.getSnapshot(draftId)
            if (snapshot != null && snapshot.layers.isNotEmpty()) {
                val layer = snapshot.layers.first()
                val draftDir = File(File(context.cacheDir, "drafts"), draftId)
                val bitmapFile = File(draftDir, layer.cacheFileName)
                if (bitmapFile.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(bitmapFile.absolutePath)
                    if (bitmap != null) {
                        sharedEditorViewModel.addBitmapToStack(
                            bitmap = bitmap,
                            triggerRecomposition = true
                        )
                        bitmapLoaded = true
                        return@LaunchedEffect
                    }
                }
            }
        }

        if (initialImageUri != null) {
            BitmapUtils.getScaledBitmap(context, initialImageUri).collect { status ->
                when (status) {
                    is BitmapStatus.Success -> {
                        sharedEditorViewModel.addBitmapToStack(
                            bitmap = status.scaledBitmap.copy(Bitmap.Config.ARGB_8888, false),
                            triggerRecomposition = true
                        )
                        bitmapLoaded = true
                    }
                    is BitmapStatus.Failed -> {
                        loadError = true
                    }
                    else -> { /* Processing */ }
                }
            }
        }
    }



    if (!bitmapLoaded) {
        BackgroundRemovalLoadingOverlay(
            modifier = Modifier.fillMaxSize(),
            message = stringResource(id = com.thgiang.image.R.string.loading_image)
        )
        return
    }

    val scope = rememberCoroutineScope()
    val selfieFallbackWarning = stringResource(id = com.thgiang.image.R.string.remove_bg_selfie_fallback_warning)
    val showSelfieFallbackWarning = {
        scope.launch {
            snackbarHostState.showSnackbar(selfieFallbackWarning)
        }
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
    var isRemovingBg by remember { mutableStateOf(false) }
    var autoRemoveStatusMessage by remember { mutableStateOf<String?>(null) }
    val quickToolsPortraitClassifier = remember { QuickToolsPortraitClassifier() }

    val goToRemoveBgScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.REMOVE_BG_MODE_SCREEN)
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
        android.util.Log.d("RotateDebug", "goToRotateModeScreen: navigating")
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
    val onHairDetailDoneClicked = remember { { bitmap: Bitmap ->
        sharedEditorViewModel.addBitmapToStack(
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true).also { it.setHasAlpha(true) },
            triggerRecomposition = true,
            addSafelyWithoutMultipleTriggers = false
        )
        navController.navigate(NavDestinations.EDITOR_SCREEN) {
            popUpTo(NavDestinations.EDITOR_SCREEN) { inclusive = true }
        }
    } }

    // ── Studio ad state ──────────────────────────────────────────────────
    var showStudioAdDialog by remember { mutableStateOf(false) }
    var isStudioAdLoading by remember { mutableStateOf(false) }
    var studioAdWatchCount by remember { mutableStateOf(0) }
    var pendingStudioBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val onStudioCheckClicked: (Bitmap) -> Unit = remember {
        { bitmap ->
            pendingStudioBitmap = bitmap
            studioAdWatchCount = 0
            isStudioAdLoading = true
            rewardedAdManager?.loadAd(
                onLoaded = { isStudioAdLoading = false },
                onFailed = { isStudioAdLoading = false }
            )
            showStudioAdDialog = true
        }
    }

    val onSaveDraftClicked: (Bitmap) -> Unit = remember { { bitmap: Bitmap ->
        scope.launch {
            val draftName = "Draft_${System.currentTimeMillis()}"
            val layerId = UUID.randomUUID().toString()
            val cacheFileName = "layer_$layerId.bin"
            
            val snapshot = ProjectSnapshot(
                selectedLayerIndex = 0,
                layers = listOf(
                    LayerSnapshot(
                        id = layerId,
                        type = "IMAGE",
                        cacheFileName = cacheFileName
                    )
                )
            )
            
            withContext(Dispatchers.IO) {
                val id = draftManager.createDraft(draftName, snapshot)
                val draftDir = draftManager.getDraftDir(id)
                val bitmapFile = File(draftDir, cacheFileName)
                val fos = java.io.FileOutputStream(bitmapFile)
                try {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                } finally {
                    fos.close()
                }
            }
        }
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

            LaunchedEffect(bitmapLoaded, autoRemoveBackground) {
                if (bitmapLoaded && autoRemoveBackground && !autoRemoveDone) {
                    autoRemoveDone = true
                    val subjectRemover = backgroundRemoverRepository
                    if (subjectRemover == null) {
                        context.toast("Background remover not available")
                        return@LaunchedEffect
                    }
                    isRemovingBg = true
                    autoRemoveStatusMessage = "Đang tìm khuôn mặt"
                    try {
                        val bitmap = sharedEditorViewModel.getCurrentBitmap()
                        ImageProcessingCrashReporter.setActiveTool("quick_tools_remove_bg")
                        ImageProcessingCrashReporter.setBitmapInfo("input", bitmap)
                        ImageProcessingCrashReporter.setRemoveBgRoute(isAutoRemove = true)
                        Log.d(TAG, "autoRemoveBackground flow start bitmap=${bitmap.width}x${bitmap.height} hasAlpha=${bitmap.hasAlpha()}")
                        ImageProcessingCrashReporter.setStage("quick_tools_face_detection")
                        val faceStartMs = android.os.SystemClock.elapsedRealtime()
                        val hasFace = runCatching {
                            withTimeout(QUICK_TOOLS_FACE_DETECTION_TIMEOUT_MS) {
                                quickToolsPortraitClassifier.hasDetectableFace(bitmap).getOrThrow()
                            }
                        }
                            .also {
                                ImageProcessingCrashReporter.setDuration(
                                    stage = "face_detection",
                                    durationMs = android.os.SystemClock.elapsedRealtime() - faceStartMs
                                )
                            }
                            .onFailure {
                                Log.e(TAG, "autoRemoveBackground: face detection failed", it)
                                ImageProcessingCrashReporter.recordNonFatal(it, "quick_tools_face_detection")
                            }
                            .getOrDefault(false)
                        val modNetRemover = hairDetailBackgroundRemoverRepository
                        val useModNet = hasFace && modNetRemover != null
                        autoRemoveStatusMessage = if (hasFace) {
                            "Xác định có khuôn mặt\nĐang tiến hành xoá phông"
                        } else {
                            "Xác định không có khuôn mặt\nĐang tiến hành xoá phông"
                        }

                        Log.d(
                            TAG,
                            "autoRemoveBackground: hasFace=$hasFace remover=${if (useModNet) "ModNet" else "ML Kit Subject"}"
                        )
                        val selectedRemoverName = if (useModNet) "modnet" else "mlkit_subject"
                        ImageProcessingCrashReporter.setRemoveBgRoute(
                            isAutoRemove = true,
                            hasFace = hasFace,
                            remover = selectedRemoverName
                        )

                        ImageProcessingCrashReporter.setStage("quick_tools_remove_bg")
                        val removeStartMs = android.os.SystemClock.elapsedRealtime()
                        var result = runCatching {
                            withTimeout(QUICK_TOOLS_REMOVE_BG_TIMEOUT_MS) {
                                withContext(Dispatchers.Default) {
                                    if (useModNet) {
                                        Log.d(TAG, "autoRemoveBackground: calling ModNet remover")
                                        ImageProcessingCrashReporter.setStage("quick_tools_call_modnet")
                                        modNetRemover!!.getForegroundBitmap(bitmap).getOrThrow()
                                    } else {
                                        Log.d(TAG, "autoRemoveBackground: calling ML Kit Subject remover")
                                        ImageProcessingCrashReporter.setStage("quick_tools_call_mlkit_subject")
                                        subjectRemover.getForegroundBitmap(bitmap).getOrThrow()
                                    }
                                }
                            }
                        }.also {
                            ImageProcessingCrashReporter.setDuration(
                                stage = "remove_bg",
                                durationMs = android.os.SystemClock.elapsedRealtime() - removeStartMs
                            )
                        }

                        if (result.isFailure && useModNet) {
                            result.exceptionOrNull()?.let {
                                Log.e(TAG, "autoRemoveBackground: ModNet failed, falling back to ML Kit Subject", it)
                                ImageProcessingCrashReporter.recordNonFatal(it, "quick_tools_modnet_fallback")
                            }
                            ImageProcessingCrashReporter.setRemoveBgRoute(
                                isAutoRemove = true,
                                hasFace = hasFace,
                                remover = "mlkit_subject_fallback"
                            )
                            ImageProcessingCrashReporter.setStage("quick_tools_remove_bg_fallback")
                            val fallbackStartMs = android.os.SystemClock.elapsedRealtime()
                            result = runCatching {
                                withTimeout(QUICK_TOOLS_REMOVE_BG_TIMEOUT_MS) {
                                    withContext(Dispatchers.Default) {
                                        subjectRemover.getForegroundBitmap(bitmap).getOrThrow()
                                    }
                                }
                            }.also {
                                ImageProcessingCrashReporter.setDuration(
                                    stage = "fallback_remove_bg",
                                    durationMs = android.os.SystemClock.elapsedRealtime() - fallbackStartMs
                                )
                            }
                        }

                        result.fold(
                            onSuccess = { fg ->
                                Log.d(TAG, "autoRemoveBackground: remover success fg=${fg.width}x${fg.height} hasAlpha=${fg.hasAlpha()}")
                        val resultBitmap = ProcessorUtils.trimTransparentBounds(
                                    fg.copy(Bitmap.Config.ARGB_8888, true)
                                ).also { it.setHasAlpha(true) }
                                ImageProcessingCrashReporter.setBitmapInfo("output", resultBitmap)
                                Log.d(TAG, "autoRemoveBackground: trimmed result prepared=${resultBitmap.width}x${resultBitmap.height}")
                                Log.d(TAG, "autoRemoveBackground: adding result to stack sizeBefore=${sharedEditorViewModel.bitmapStack.size}")
                                sharedEditorViewModel.addBitmapToStack(
                                    bitmap = resultBitmap,
                                    triggerRecomposition = true,
                                    addSafelyWithoutMultipleTriggers = false
                                )
                                Log.d(TAG, "autoRemoveBackground: added result to stack sizeAfter=${sharedEditorViewModel.bitmapStack.size}")
                                if (subjectRemover.consumeSelfieFallbackWarning()) {
                                    showSelfieFallbackWarning()
                                }
                            },
                            onFailure = {
                                Log.e(TAG, "autoRemoveBackground: remover failed", it)
                                ImageProcessingCrashReporter.recordNonFatal(it, "quick_tools_remove_bg")
                                context.toast("Background removal failed")
                            }
                        )
                    } finally {
                        autoRemoveStatusMessage = null
                        isRemovingBg = false
                    }
                }
            }

            val visualState by sharedEditorViewModel.recompositionTrigger
                .collectAsStateWithLifecycle()

            val initialEditorState = EditorScreenState(
                sharedEditorViewModel.bitmapStack,
                sharedEditorViewModel.bitmapRedoStack,
                recompositionTrigger = visualState
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
                    onSaveDraftClicked = onSaveDraftClicked
                )

                if (isRemovingBg) {
                    BackgroundRemovalLoadingOverlay(
                        modifier = Modifier.fillMaxSize(),
                        message = autoRemoveStatusMessage,
                        showMessage = autoRemoveStatusMessage != null
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
            CropperScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
            )
        }

        composable(route = NavDestinations.DRAW_MODE_SCREEN) {
            DrawModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
            )
        }

        composable(route = NavDestinations.TEXT_MODE_SCREEN) {
            TextModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
            )
        }

        composable(route = NavDestinations.EFFECTS_MODE_SCREEN) {
            EffectsModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
            )
        }

        composable(route = NavDestinations.BORDER_MODE_SCREEN) {
            BorderModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
            )
        }
        
        composable(route = NavDestinations.STUDIO_MODE_SCREEN) {
            StudioModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
                onCheckClicked = if (rewardedAdManager != null) onStudioCheckClicked else null
            )
        }

        composable(route = NavDestinations.REMOVE_BG_MODE_SCREEN) {
            com.abizer_r.quickedit.ui.removeBgMode.RemoveBgModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked
            )
        }

        composable(route = NavDestinations.BACKGROUND_MODE_SCREEN) { entry ->
            val pickedImageUri = entry.savedStateHandle.get<Uri>("background_image_uri")
            var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val ctx = LocalContext.current

            LaunchedEffect(pickedImageUri) {
                pickedImageUri?.let { uri ->
                    val bitmap = BitmapUtils.getBitmapFromUri(ctx, uri)
                    pickedBitmap = bitmap
                    entry.savedStateHandle.remove<Uri>("background_image_uri")
                }
            }

            BackgroundModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
                onPickImageRequest = {
                    navController.navigate(NavDestinations.SINGLE_IMAGE_PICKER_SCREEN + "?autoRemove=false")
                },
                pickedImage = pickedBitmap
            )
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

        composable(route = NavDestinations.ADD_IMAGE_PICKER_SCREEN) {
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

        composable(route = NavDestinations.MAGIC_BRUSH_SCREEN) {
            MagicBrushScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
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

        composable(route = NavDestinations.ROTATE_SCREEN) {
            android.util.Log.d("RotateDebug", "ROTATE_SCREEN composable: rendering RotateModeScreen (QuickEditActivity)")
            RotateModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressed,
                onDoneClicked = onDoneClicked,
            )
        }
    }

    // ── Rewarded Ad dialog for Studio save ──────────────────────────────
    if (showStudioAdDialog && rewardedAdManager != null) {
        val activity = context as? android.app.Activity
        com.thgiang.image.core.design.components.ModernRewardedAdDialog(
            count = studioAdWatchCount,
            isLoading = isStudioAdLoading,
            onWatchAd = {
                activity?.let {
                    rewardedAdManager.showAd(
                        activity = it,
                        onRewardReceived = {
                            studioAdWatchCount = 1
                        },
                        onAdClosed = {
                            if (studioAdWatchCount >= 1) {
                                showStudioAdDialog = false
                                pendingStudioBitmap?.let { bmp ->
                                    onDoneClicked(bmp)
                                }
                                pendingStudioBitmap = null
                                studioAdWatchCount = 0
                            } else {
                                isStudioAdLoading = true
                                rewardedAdManager.loadAd(
                                    onLoaded = { isStudioAdLoading = false },
                                    onFailed = { isStudioAdLoading = false }
                                )
                            }
                        },
                        onFailedToShow = {
                            isStudioAdLoading = false
                        }
                    )
                }
            },
            onUpgrade = {
                showStudioAdDialog = false
                pendingStudioBitmap = null
            },
            onDismiss = {
                showStudioAdDialog = false
                pendingStudioBitmap = null
            }
        )
    }
}
