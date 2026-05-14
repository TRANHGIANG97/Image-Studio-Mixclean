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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
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
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.feature.editor.model.DraftManager
import com.thgiang.image.feature.editor.model.LayerSnapshot
import com.thgiang.image.feature.editor.model.ProjectSnapshot
import com.abizer_r.quickedit.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thgiang.image.feature.premium.domain.PremiumRepository
import java.util.UUID
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class QuickEditActivity : AppCompatActivity() {

    @Inject
    lateinit var draftManager: DraftManager

    @Inject
    lateinit var backgroundRemoverRepository: BackgroundRemoverRepository

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
        android.util.Log.d("QuickEditActivity", "autoRemoveBackground flag: $autoRemoveBg")

        setContent {
            QuickEditTheme {
                QuickEditEditorNavigation(
                    initialImageUri = uri,
                    draftId = draftId,
                    draftManager = draftManager,
                    autoRemoveBackground = autoRemoveBg,
                    backgroundRemoverRepository = backgroundRemoverRepository,
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
    rewardedAdManager: com.thgiang.image.core.ad.RewardedAdManager? = null,
    premiumRepository: PremiumRepository? = null
) {
    val isPremium by (premiumRepository?.isPremium ?: kotlinx.coroutines.flow.flowOf(false))
        .collectAsStateWithLifecycle(initialValue = false)

    val navController = rememberNavController()
    val sharedEditorViewModel: SharedEditorViewModel = viewModel()
    val context = LocalContext.current

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val scope = rememberCoroutineScope()
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

    val goToRemoveBgScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        val bitmap = sharedEditorViewModel.getCurrentBitmap()
        isRemovingBg = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                backgroundRemoverRepository?.getForegroundBitmap(bitmap)
            }
            result?.fold(
                onSuccess = { fg ->
                    val resultBitmap = fg.copy(Bitmap.Config.ARGB_8888, true)
                    resultBitmap.setHasAlpha(true)
                    sharedEditorViewModel.addBitmapToStack(bitmap = resultBitmap, triggerRecomposition = true)
                    isRemovingBg = false
                    navController.navigate(NavDestinations.EDITOR_SCREEN) {
                        popUpTo(NavDestinations.EDITOR_SCREEN) { inclusive = true }
                    }
                },
                onFailure = { e ->
                    isRemovingBg = false
                    context.toast("Background removal failed")
                }
            )
        }
        Unit
    } }
    val goToBackgroundModeScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.BACKGROUND_MODE_SCREEN)
    } }
    val goToMagicBrushScreen = remember { { state: EditorScreenState ->
        sharedEditorViewModel.updateStacksFromEditorState(state)
        navController.navigate(NavDestinations.MAGIC_BRUSH_SCREEN)
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
                if (id != null) {
                    // Save bitmap
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
        }
    } }

    NavHost(
        navController = navController,
        startDestination = NavDestinations.EDITOR_SCREEN,
    ) {
        composable(route = NavDestinations.EDITOR_SCREEN) {
            var autoRemoveResult by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(bitmapLoaded, autoRemoveBackground) {
                if (bitmapLoaded && autoRemoveBackground && !autoRemoveDone) {
                    autoRemoveDone = true
                    isRemovingBg = true
                    val bitmap = sharedEditorViewModel.getCurrentBitmap()
                    val result = withContext(Dispatchers.Default) {
                        backgroundRemoverRepository?.getForegroundBitmap(bitmap)
                    }
                    result?.fold(
                        onSuccess = { fg ->
                            autoRemoveResult = fg.copy(Bitmap.Config.ARGB_8888, true).also { it.setHasAlpha(true) }
                        },
                        onFailure = { }
                    )
                    isRemovingBg = false
                }
            }

            LaunchedEffect(autoRemoveResult) {
                if (autoRemoveResult != null) {
                    sharedEditorViewModel.addBitmapToStack(bitmap = autoRemoveResult!!, triggerRecomposition = true)
                    autoRemoveResult = null
                    navController.navigate(NavDestinations.EDITOR_SCREEN) {
                        popUpTo(NavDestinations.EDITOR_SCREEN) { inclusive = true }
                    }
                }
            }

            val visualState by sharedEditorViewModel.recompositionTrigger
                .collectAsStateWithLifecycle()

            val initialEditorState = EditorScreenState(
                sharedEditorViewModel.bitmapStack,
                sharedEditorViewModel.bitmapRedoStack
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
                    goToMagicBrushScreen = goToMagicBrushScreen,
                    goToMainScreen = {
                        sharedEditorViewModel.resetStacks()
                        (context as? android.app.Activity)?.finish()
                    },
                    isPremium = isPremium,
                    onSaveDraftClicked = onSaveDraftClicked
                )

                if (isRemovingBg) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Removing background...",
                                color = Color.White
                            )
                        }
                    }
                }
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
