package com.thgiang.image.feature.home.ui
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.R
import com.thgiang.image.core.model.QuickToolAction
import com.thgiang.image.feature.editor.ui.QuickEditActivity
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.feature.home.viewmodel.HomeViewModel
import com.thgiang.image.studio.ui.home.StudioSection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.thgiang.image.core.ad.BannerAdView
import java.io.File


@Composable
fun HomeDashboardScreen(
    onOpenRemoveBgEditor: () -> Unit = {},
    onBatchRemove: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    isPremium: Boolean = false,
    onOpenPro: () -> Unit = {},
    preferredRemovalQuality: String = "standard",
    onPreferredRemovalQualityChange: (String) -> Unit = {},
    onOpenDrafts: () -> Unit = {},
    onNavigateToPicker: () -> Unit = {},
    onSimplePick: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    pickedUriFromPicker: Uri? = null,
    onConsumePickedUri: () -> Unit = {},
    isDarkMode: Boolean = false,
    isHomePreviewEnabled: Boolean = false
) {
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = hiltViewModel()
    
    LaunchedEffect(pickedUriFromPicker) {
        if (pickedUriFromPicker != null) {
            homeViewModel.onImagePicked(pickedUriFromPicker)
            onConsumePickedUri()
        }
    }
    val uiState by homeViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isBusy = uiState.isProcessing || uiState.isPresetFlowProcessing
    val draftManager = remember { com.thgiang.image.feature.editor.model.DraftManager(context) }
    var draftCount by remember { mutableIntStateOf(0) }
    val hasDrafts = draftCount > 0
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAutoRemoveMode by remember { mutableStateOf(false) }

    // Check for saved drafts
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            draftCount = withContext(Dispatchers.IO) {
                draftManager.getAllDrafts().size
            }
        }
    }
    val editImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                homeViewModel.reset()
            }
        }
    }
    val removeBgEditorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            homeViewModel.reset()
        }
    }
    // Removed system pickerLauncher to use app's internal picker instead
    // as per user requirement to only use SingleImagePickerScreen/MultiImagePickerScreen.


    LaunchedEffect(Unit) {
        homeViewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(uiState.pendingEditorUri) {
        val editorUri = uiState.pendingEditorUri ?: return@LaunchedEffect
        editImageLauncher.launch(QuickEditActivity.createIntent(context, editorUri))
        homeViewModel.consumePendingEditorUri()
    }

    LaunchedEffect(isPremium, preferredRemovalQuality) {
        homeViewModel.setUseProQuality(isPremium && preferredRemovalQuality == "pro")
    }

    if (showUpgradeSheet) {
        UpgradeBottomSheet(
            onDismiss = { showUpgradeSheet = false },
            onGoPro = {
                showUpgradeSheet = false
                onOpenPro()
            }
        )
    }



    val isHomeDarkStyle = isDarkMode
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isHomeDarkStyle) HomeDarkStyle.background else ImageDesign.surfaces.base
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isHomeDarkStyle) Modifier.background(HomeDarkStyle.background)
                        else if (isSystemInDarkTheme()) Modifier.background(Color(0xFF1A1A1A))
                        else Modifier.background(ImageDesign.gradients.appBackground)
                    )
                    .padding(contentPadding)
                    .padding(horizontal = 3.dp)
            ) {
                val previewHeight = maxHeight * 0.45f

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(HomeSpacing.hero))

                    Spacer(modifier = Modifier.height(4.dp))


                    if (isHomePreviewEnabled) {
                        HomeRemoveBackgroundSection(
                            selectedImageUri = uiState.selectedImageUri,
                            processedImageUri = uiState.processedImageUri,
                            processedHasAlpha = uiState.processedHasAlpha,
                            isProcessing = uiState.isProcessing,
                            progress = uiState.progress,
                            previewHeight = previewHeight,
                            useHomeDarkStyle = isHomeDarkStyle,
                            isAutoSliderEnabled = true, // Animation is on if preview is enabled
                            lastSliderBeforeUri = uiState.lastSliderBeforeUri,
                            lastSliderAfterUri = uiState.lastSliderAfterUri,
                            onPickImage = {
                                if (!homeViewModel.isAdDismissedRecently()) {
                                    isAutoRemoveMode = true
                                    onOpenRemoveBgEditor()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    AiToolDock(
                        isPremium = isPremium,
                        onOpenRemoveBgEditor = {
                            if (!homeViewModel.isAdDismissedRecently()) {
                                isAutoRemoveMode = true
                                onOpenRemoveBgEditor()
                            }
                        },
                        onBatchRemove = onBatchRemove,
                        onLockedClick = { showUpgradeSheet = true },
                        hasDraft = hasDrafts,
                        draftCount = draftCount,
                        onRestoreDraft = onOpenDrafts,
                        useHomeDarkStyle = isHomeDarkStyle
                    )

                    Spacer(modifier = Modifier.height(HomeSpacing.section))

                    PresetDock(
                        isPremium = isPremium,
                        onLockedClick = { showUpgradeSheet = true },
                        useHomeDarkStyle = isHomeDarkStyle,
                        isProcessing = isBusy,
                        selectedPreset = uiState.selectedPresetStyle,
                        onPresetClick = { preset ->
                            if (isBusy || homeViewModel.isAdDismissedRecently()) return@PresetDock
                            homeViewModel.onPresetSelected(preset.style)
                            onNavigateToPicker()
                        }
                    )

                    Spacer(modifier = Modifier.height(HomeSpacing.section))

                    StudioSection(
                        forceDarkStyle = isHomeDarkStyle,
                        onCategoryClick = { category ->
                            if (category.id == "cosmetics") {
                                onOpenStudio()
                            }
                        }
                    )

                    if (!isPremium) {
                        Spacer(modifier = Modifier.height(HomeSpacing.section))
                        BannerAdView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(HomeSpacing.section))

                    BorderPresetDock(
                        selectedPreset = uiState.selectedBorderPresetStyle,
                        isProcessing = isBusy,
                        useHomeDarkStyle = isHomeDarkStyle,
                        onPresetClick = { style ->
                            if (isBusy || homeViewModel.isAdDismissedRecently()) return@BorderPresetDock
                            homeViewModel.onBorderPresetSelected(style)
                            onNavigateToPicker()
                        }
                    )

                    Spacer(modifier = Modifier.height(HomeSpacing.section))
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )

            if (!isPremium) {
                // Banner đã được chuyển lên giữa các section
            }
        }
    }
}

private object HomeSpacing {
    val hero = 24.dp
    val section = 20.dp
}

// rememberImagePicker removed as we now only use internal pickers.



