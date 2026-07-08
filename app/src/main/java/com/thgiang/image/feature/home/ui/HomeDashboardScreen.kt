package com.thgiang.image.feature.home.ui
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.R
import com.thgiang.image.core.model.QuickToolAction
import com.thgiang.image.feature.editor.ui.QuickEditActivity
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.design.theme.HomeUiTokens
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.feature.home.viewmodel.HomeViewModel
import com.abizer_r.quickedit.ui.mainScreen.CosmeticsThemeplateSection
import com.abizer_r.quickedit.ui.mainScreen.ProfessionalThemeplateSection
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.model.StudioThemeplateSection
import com.thgiang.image.studio.model.StudioThemeplates
import com.thgiang.image.studio.util.toAssetModel
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundGradientPreset
import com.abizer_r.quickedit.utils.BorderGradientPreset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.thgiang.image.core.ad.BannerAdView
import java.io.File
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.getValue
@OptIn(ExperimentalMaterial3Api::class)
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
    onNavigateToBackgroundPresetPicker: (BackgroundGradientPreset) -> Unit = {},
    onNavigateToBorderPresetPicker: (BorderGradientPreset) -> Unit = {},
    onSimplePick: () -> Unit = {},
    onOpenEffectsTool: () -> Unit = {},
    onOpenStudioTool: () -> Unit = {},
    onOpenMagicTool: () -> Unit = {},
    onThemeplateSelected: (StudioThemeplate) -> Unit = {},
    onOpenThemeplateGallery: (String) -> Unit = {},
    pickedUriFromPicker: Uri? = null,
    onConsumePickedUri: () -> Unit = {},
    isDarkMode: Boolean = false,
    isHomePreviewEnabled: Boolean = false,
    onOpenBackgroundPresetsTool: () -> Unit = {}
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
    var selectedBackgroundGradientPresetId by remember { mutableStateOf<String?>(null) }
    var selectedBorderGradientPresetId by remember { mutableStateOf<String?>(null) }

    // Check for saved drafts
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            draftCount = withContext(Dispatchers.IO) {
                draftManager.getAllDrafts().size
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Chỉ tải nếu chưa có dữ liệu — dùng cache khi quay lại từ chức năng khác
                homeViewModel.ensureTemplatesLoaded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    .padding(horizontal = HomeUiTokens.outerPadding)
            ) {
                val previewHeight = maxHeight * 0.45f

                PullToRefreshBox(
                    isRefreshing = uiState.isLoadingTemplates,
                    onRefresh = { homeViewModel.refreshTemplates() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                    Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))

                    HomeThemeplateSlider(
                        templates = uiState.professionalTemplates,
                        onThemeplateSelected = onThemeplateSelected
                    )

                    Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))

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
                        onOpenEffects = onOpenEffectsTool,
                        onOpenStudioTool = onOpenStudioTool,
                        onOpenMagicTool = onOpenMagicTool,
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
                        useHomeDarkStyle = isHomeDarkStyle,
                        onOpenBackgroundPresetsTool = onOpenBackgroundPresetsTool
                    )

                    Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))

                    if (uiState.isOffline) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .background(
                                    color = if (isHomeDarkStyle) Color(0xFF2C2C2C) else Color(0xFFFFF3CD),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (isHomeDarkStyle) Color(0xFF444444) else Color(0xFFFFEEBA),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Science,
                                contentDescription = null,
                                tint = if (isHomeDarkStyle) Color(0xFFE5A93B) else Color(0xFF856404),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Không có kết nối Internet. Đang hiển thị mẫu đã lưu.",
                                color = if (isHomeDarkStyle) Color(0xFFE5A93B) else Color(0xFF856404),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))
                    }

                    val cosmeticsTemplates = uiState.cosmeticsTemplates
                    val professionalTemplates = uiState.professionalTemplates
                    val sections = uiState.otherSections

                    if (uiState.isLoadingTemplates && cosmeticsTemplates.isEmpty() && professionalTemplates.isEmpty()) {
                        com.thgiang.image.feature.home.ui.components.SkeletonThemeplateSection(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))
                        com.thgiang.image.feature.home.ui.components.SkeletonThemeplateSection(
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CosmeticsThemeplateSection(
                            templates = cosmeticsTemplates,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenGallery = { onOpenThemeplateGallery("cosmetics") },
                            onThemeplateSelected = onThemeplateSelected
                        )

                        Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))

                        ProfessionalThemeplateSection(
                            templates = professionalTemplates,
                            sections = sections,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenGallery = onOpenThemeplateGallery,
                            onThemeplateSelected = onThemeplateSelected
                        )
                    }

                    Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))

                    BorderPresetDock(
                        selectedPresetId = selectedBorderGradientPresetId,
                        isProcessing = isBusy,
                        useHomeDarkStyle = isHomeDarkStyle,
                        onPresetClick = { preset ->
                            if (isBusy || homeViewModel.isAdDismissedRecently()) return@BorderPresetDock
                            selectedBorderGradientPresetId = preset.id
                            onNavigateToBorderPresetPicker(preset)
                        }
                    )

                    if (!isPremium) {
                        Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))
                        BannerAdView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = HomeUiTokens.outerPadding)
                        )
                    }

                    Spacer(modifier = Modifier.height(HomeUiTokens.sectionSpacing))
                }
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

// HomeSpacing object removed in favor of HomeUiTokens

// rememberImagePicker removed as we now only use internal pickers.

@Composable
private fun HomeThemeplateSlider(
    templates: List<StudioThemeplate>,
    onThemeplateSelected: (StudioThemeplate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (templates.isEmpty()) return

    val pageCount = templates.size
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged, templates) {
        if (!isDragged && templates.isNotEmpty()) {
            while (true) {
                delay(10000)
                val nextPage = (pagerState.currentPage + 1) % templates.size
                pagerState.animateScrollToPage(
                    nextPage,
                    animationSpec = tween(durationMillis = 1000)
                )
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        pageSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) { page ->
        val template = templates[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onThemeplateSelected(template) },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = (template.backgroundAssetPath ?: template.assetPath).toAssetModel(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.12f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.03f))
            )

            Box(modifier = Modifier.wrapContentSize()) {
                AsyncImage(
                    model = template.assetPath.toAssetModel(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxHeight(),
                    contentScale = ContentScale.Fit
                )
                
                template.objectSourceAssetPath?.let { objPath ->
                    AsyncImage(
                        model = objPath.toAssetModel(),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "Themeplate",
                        color = Color(0xFFFF6D00),
                        fontSize = 4.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.25.sp
                    )
                }
            }
        }
    }

}
