package com.thgiang.image.app.navigation

import android.app.Activity
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import com.thgiang.image.core.design.components.ModernRewardedAdDialog
import com.thgiang.image.core.design.components.ReviewPromptDialog
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.feature.editor.ui.QuickEditActivity
import com.thgiang.image.feature.home.ui.HomeDashboardScreen
import com.thgiang.image.feature.home.ui.RemovalQualitySelector
import com.thgiang.image.feature.premium.ui.PremiumScreen
import com.thgiang.image.feature.home.ui.SingleImagePickerScreen
import com.thgiang.image.feature.remove.ui.BatchRemoveScreen
import com.thgiang.image.feature.remove.ui.MultiImagePickerScreen
import com.thgiang.image.feature.settings.ui.SettingsScreen
import com.thgiang.image.studio.ui.editor.ThemeplateEditorScreen
import com.thgiang.image.studio.ui.gallery.ThemeplateGalleryScreen
import com.thgiang.image.studio.model.StudioThemeplates
import com.thgiang.image.studio.model.StudioThemeplate
import java.util.Locale
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundGradientPreset
import com.abizer_r.quickedit.utils.BorderGradientPreset
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PLAY_STORE_REVIEW_URL =
    "https://play.google.com/store/apps/details?id=com.thgiang.image"

private enum class ReviewPromptSource {
    Auto,
    Manual
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    appViewModel: AppViewModel,
    initialRoute: String? = null
) {
    val appState by appViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    val navController = rememberNavController()
    LaunchedEffect(initialRoute) {
        initialRoute?.let { route ->
            navController.navigate(route)
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showPremiumScreen by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showRewardedAdDialog by remember { mutableStateOf(false) }
    var showSaveRewardedAdDialog by remember { mutableStateOf(false) }
    var showReviewPromptDialog by remember { mutableStateOf(false) }
    var reviewPromptSource by remember { mutableStateOf(ReviewPromptSource.Auto) }
    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isAutoRemoveForPicker by rememberSaveable { mutableStateOf(false) }
    var isRemoveBgEditorForPicker by remember { mutableStateOf(false) }
    var isPresetModeForPicker by remember { mutableStateOf(false) }
    val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val languageOptions = homeLanguageOptions(context)
    val selectedLanguageLabel = languageOptions
        .firstOrNull { it.code == appState.selectedLanguage }
        ?.label
        ?: systemLanguageLabel(context)

    BackHandler {
        when {
            showReviewPromptDialog -> {
                showReviewPromptDialog = false
                if (reviewPromptSource == ReviewPromptSource.Auto) {
                    appViewModel.markReviewDeclined()
                }
                return@BackHandler
            }
            showRewardedAdDialog -> {
                showRewardedAdDialog = false
                appViewModel.resetBatchAdState()
                return@BackHandler
            }
            showSaveRewardedAdDialog -> {
                showSaveRewardedAdDialog = false
                pendingSaveAction = null
                appViewModel.resetBatchAdState()
                return@BackHandler
            }
            showQualitySheet -> {
                showQualitySheet = false
                return@BackHandler
            }
            showPremiumScreen -> {
                showPremiumScreen = false
                return@BackHandler
            }
            currentRoute == Screen.Home.route -> showExitDialog = true
            else -> navController.popBackStack()
        }
    }

    // ── Premium full-screen overlay ────────────────────────────────────
    if (showPremiumScreen) {
        PremiumScreen(
            onClose = { showPremiumScreen = false }
        )
        return
    }

    fun navigateToHome() {
        navController.navigate(Screen.Home.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    fun openReviewStore() {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(PLAY_STORE_REVIEW_URL)
                )
            )
        }
    }

    fun showReviewPrompt(source: ReviewPromptSource) {
        reviewPromptSource = source
        showReviewPromptDialog = true
    }

    fun requestSaveVideoAd(action: () -> Unit) {
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.menu_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_home)) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    selected = currentRoute == Screen.Home.route,
                    onClick = {
                        navigateToHome()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_gallery)) },
                    icon = { Icon(Icons.Default.Collections, contentDescription = null) },
                    selected = currentRoute == Screen.BatchPicker.route || currentRoute == Screen.BatchRemove.route,
                    onClick = {
                        appViewModel.setBatchUris(emptyList())
                        navController.navigate(Screen.BatchPicker.route)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        navController.navigate(Screen.Settings.route)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (isDebug) {
                    NavigationDrawerItem(
                        label = { Text("Admin Tools") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            try {
                                val adminClass = Class.forName("com.thgiang.image.admin.ui.AdminActivity")
                                val intent = android.content.Intent(context, adminClass)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore if not available
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (currentRoute == Screen.Home.route) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                            Color(0xFF1E1E1E).copy(alpha = 0.88f)
                        } else {
                            Color.White.copy(alpha = 0.92f)
                        },
                        shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color(0xFFE8E0D6)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close()
                                        else drawerState.open()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.menu_title),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "MixClean",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF00C6FF), Color(0xFF4F8BFE), Color(0xFFB54BFF))
                                        )
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.home_hero_subtitle_short),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                        Color.White.copy(alpha = 0.5f)
                                    } else {
                                        Color(0xFF8A8A8A)
                                    },
                                    maxLines = 1
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showReviewPrompt(ReviewPromptSource.Manual) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RateReview,
                                        contentDescription = stringResource(R.string.review_app_button),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { languageMenuExpanded = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = stringResource(R.string.language_title),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = languageMenuExpanded,
                                    onDismissRequest = { languageMenuExpanded = false }
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.current_language_menu, selectedLanguageLabel))
                                        }
                                    )
                                    languageOptions.forEach { option ->
                                        LanguageDropdownItem(
                                            label = option.label,
                                            code = option.code,
                                            selectedLanguage = appState.selectedLanguage,
                                            onLanguageClick = { code ->
                                                languageMenuExpanded = false
                                                appViewModel.setLanguage(code)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding: PaddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) { backStackEntry ->
                    val pickedUri = backStackEntry.savedStateHandle.get<android.net.Uri>("picked_uri")

                    HomeDashboardScreen(
                        onOpenRemoveBgEditor = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=&borderGradientPresetId=&targetTool="
                                )
                            }
                        },
                        onBatchRemove = {
                            if (appViewModel.isAdDismissedRecently()) return@HomeDashboardScreen
                            appViewModel.setBatchUris(emptyList())
                            navController.navigate(Screen.BatchPicker.route)
                        },
                        onNavigateToPicker = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                isAutoRemoveForPicker = false
                                isPresetModeForPicker = true
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=false&backgroundGradientPresetId=&borderGradientPresetId=&targetTool="
                                )
                            }
                        },
                        onNavigateToBackgroundPresetPicker = { preset: BackgroundGradientPreset ->
                            if (!appViewModel.isAdDismissedRecently()) {
                                isAutoRemoveForPicker = false
                                isPresetModeForPicker = false
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=${preset.id}&borderGradientPresetId=&targetTool="
                                )
                            }
                        },
                        onNavigateToBorderPresetPicker = { preset: BorderGradientPreset ->
                            if (!appViewModel.isAdDismissedRecently()) {
                                isAutoRemoveForPicker = false
                                isPresetModeForPicker = false
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=&borderGradientPresetId=${preset.id}&targetTool="
                                )
                            }
                        },
                        onSimplePick = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=false&backgroundGradientPresetId=&borderGradientPresetId=&targetTool="
                                )
                            }
                        },
                        onOpenEffectsTool = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=&borderGradientPresetId=&targetTool=effects"
                                )
                            }
                        },
                        onOpenStudioTool = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=&borderGradientPresetId=&targetTool=studio"
                                )
                            }
                        },
                        onOpenMagicTool = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=&borderGradientPresetId=&targetTool=magic"
                                )
                            }
                        },
                        onOpenBackgroundPresetsTool = {
                            if (!appViewModel.isAdDismissedRecently()) {
                                isAutoRemoveForPicker = false
                                isPresetModeForPicker = false
                                navController.navigate(
                                    Screen.SingleImagePicker.route +
                                        "?autoRemove=true&backgroundGradientPresetId=&borderGradientPresetId=&targetTool=background"
                                )
                            }
                        },
                        onThemeplateSelected = { themeplate: StudioThemeplate ->
                            appViewModel.selectTemplate(themeplate) {
                                navController.navigate(Screen.StudioEditor.createRoute(themeplate.id))
                            }
                        },
                        onOpenThemeplateGallery = { tabIndex ->
                            navController.navigate(Screen.ThemeplateGallery.createRoute(tabIndex))
                        },
                        pickedUriFromPicker = pickedUri,
                        onConsumePickedUri = { 
                            backStackEntry.savedStateHandle.remove<android.net.Uri>("picked_uri")
                        },
                        contentPadding = innerPadding,
                        isPremium = appState.isPremium,
                        onOpenPro = { showPremiumScreen = true },
                        preferredRemovalQuality = appState.preferredRemovalQuality,
                        onPreferredRemovalQualityChange = appViewModel::setPreferredRemovalQuality,
                        onOpenDrafts = { navController.navigate(Screen.Drafts.route) },
                        isDarkMode = appState.isDarkMode,
                        isHomePreviewEnabled = appState.isHomePreviewEnabled
                    )
                }
                composable(
                    route = Screen.SingleImagePicker.route + "?autoRemove={autoRemove}&backgroundGradientPresetId={backgroundGradientPresetId}&borderGradientPresetId={borderGradientPresetId}&targetTool={targetTool}",
                    arguments = listOf(
                        navArgument("autoRemove") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("backgroundGradientPresetId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("borderGradientPresetId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("targetTool") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val autoRemove = backStackEntry.arguments?.getBoolean("autoRemove") ?: false
                    val backgroundGradientPresetId = backStackEntry.arguments
                        ?.getString("backgroundGradientPresetId")
                        ?.takeIf { it.isNotBlank() }
                    val borderGradientPresetId = backStackEntry.arguments
                        ?.getString("borderGradientPresetId")
                        ?.takeIf { it.isNotBlank() }
                    val targetTool = backStackEntry.arguments
                        ?.getString("targetTool")
                        ?.takeIf { it.isNotBlank() }
                    
                    SingleImagePickerScreen(
                        onImageSelected = { uri ->
                            try {
                                FirebaseAnalytics.getInstance(context).logEvent("select_image", null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            if (isPresetModeForPicker) {
                                navController.previousBackStackEntry?.savedStateHandle?.set("picked_uri", uri)
                                navController.popBackStack()
                                isPresetModeForPicker = false
                            } else {
                                navController.popBackStack(Screen.Home.route, inclusive = false)
                                activity?.startActivity(
                                    QuickEditActivity.createIntent(
                                        context, uri,
                                        autoRemoveBackground = autoRemove,
                                        backgroundGradientPresetId = backgroundGradientPresetId,
                                        borderGradientPresetId = borderGradientPresetId,
                                        targetTool = targetTool
                                    )
                                )
                                isPresetModeForPicker = false
                                isRemoveBgEditorForPicker = false
                            }
                        },

                        onCancel = { 
                            isPresetModeForPicker = false
                            navController.popBackStack() 
                        }
                    )
                }
                composable(Screen.BatchPicker.route) {
                    MultiImagePickerScreen(
                        onImagesSelected = { uris ->
                            val merged = (appState.batchUris + uris).distinct()
                            appViewModel.setBatchUris(merged)
                            if (!navController.popBackStack(
                                    Screen.BatchRemove.route,
                                    inclusive = false
                                )
                            ) {
                                navController.navigate(Screen.BatchRemove.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                            }
                        },
                        onCancel = {
                            val targetRoute = try {
                                navController.getBackStackEntry(Screen.BatchRemove.route)
                                Screen.BatchRemove.route
                            } catch (_: IllegalArgumentException) {
                                Screen.Home.route
                            }
                            navController.popBackStack(targetRoute, inclusive = false)
                        }
                    )
                }
                composable(Screen.BatchRemove.route) {
                    BatchRemoveScreen(
                        initialUris = appState.batchUris,
                        onBack = {
                            appViewModel.setBatchUris(emptyList())
                            navController.popBackStack(
                                Screen.Home.route,
                                inclusive = false
                            )
                        },
                        onAddMore = { navController.navigate(Screen.BatchPicker.route) },
                        contentPadding = innerPadding,
                        onRequireSaveAd = { action -> requestSaveVideoAd(action) },
                        onSaveSuccess = { appViewModel.recordSuccessfulSave { showReviewPrompt(ReviewPromptSource.Auto) } }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        isDarkMode = appState.isDarkMode,
                        onDarkModeChange = appViewModel::setDarkMode,
                        selectedLanguage = appState.selectedLanguage,
                        onLanguageChange = appViewModel::setLanguage,
                        isPremium = appState.isPremium,
                        onOpenPro = { showPremiumScreen = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                composable(Screen.Drafts.route) {
                    com.thgiang.image.feature.drafts.ui.DraftsScreen(
                        onBack = { navController.popBackStack() },
                        onSelectDraft = { draft ->
                            navController.popBackStack(Screen.Home.route, inclusive = false)
                            if (draft.isTemplate) {
                                // For templates, we pass a dummy themeplateId ("draft") since the viewModel will load from draftId
                                navController.navigate(Screen.StudioEditor.createRoute("draft", draft.id))
                            } else {
                                activity?.startActivity(
                                    QuickEditActivity.createIntent(context, draftId = draft.id)
                                )
                            }
                        }
                    )
                }
                composable(
                    route = Screen.StudioEditor.route,
                    arguments = listOf(
                        navArgument("themeplateId") {
                            type = NavType.StringType
                        },
                        navArgument("draftId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) {
                    val themeplateId = it.arguments?.getString("themeplateId") ?: ""
                    val draftId = it.arguments?.getString("draftId")
                    val themeplate = StudioThemeplates.findById(themeplateId) ?: StudioThemeplate(
                        id = "draft", 
                        titleResId = com.thgiang.image.R.string.home_draft, 
                        assetPath = "",
                        accentColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                    if (true) {
                        ThemeplateEditorScreen(
                            themeplate = themeplate,
                            onBack = { navController.popBackStack() },
                            onDone = { _ ->
                                navController.popBackStack(Screen.Home.route, inclusive = false)
                            },
                            onRequireExportAd = { action -> requestSaveVideoAd(action) },
                            onExportSuccess = {
                                appViewModel.recordSuccessfulSave { showReviewPrompt(ReviewPromptSource.Auto) }
                            },
                            onPickImage = { onSelected, onCancel ->
                                SingleImagePickerScreen(
                                    onImageSelected = onSelected,
                                    onCancel = onCancel
                                )
                            }
                        )
                    }
                }
                composable(
                    route = Screen.ThemeplateGallery.route,
                    arguments = listOf(
                        navArgument("initialTab") {
                            type = NavType.IntType
                            defaultValue = 0
                        }
                    )
                ) { backStackEntry ->
                    val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
                    ThemeplateGalleryScreen(
                        initialTabIndex = initialTab,
                        onBack = { navController.popBackStack() },
                        onThemeplateSelected = { themeplateId, isPremium ->
                            val dummyTemplate = StudioThemeplate(
                                id = themeplateId,
                                titleResId = 0,
                                assetPath = "",
                                accentColor = androidx.compose.ui.graphics.Color.Transparent,
                                isPremium = isPremium
                            )
                            appViewModel.selectTemplate(dummyTemplate) {
                                navController.navigate(Screen.StudioEditor.createRoute(themeplateId))
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Rewarded Ad dialog for batch ──────────────────────────────────────
    if (showRewardedAdDialog) {
        val adState by appViewModel.batchAdState.collectAsState()
        val watchCount by appViewModel.batchAdWatchCount.collectAsState()

        ModernRewardedAdDialog(
            count = watchCount,
            isLoading = adState is BatchAdState.Loading,
            onWatchAd = {
                activity?.let {
                    appViewModel.watchAdForBatch(it) {
                        showRewardedAdDialog = false
                        appViewModel.setBatchUris(emptyList())
                        navController.navigate(Screen.BatchPicker.route)
                    }
                }
            },
            onUpgrade = {
                showRewardedAdDialog = false
                appViewModel.resetBatchAdState()
                showPremiumScreen = true
            },
            onDismiss = {
                showRewardedAdDialog = false
                appViewModel.resetBatchAdState()
            }
        )
    }

    if (showSaveRewardedAdDialog) {
        val adState by appViewModel.batchAdState.collectAsState()
        val watchCount by appViewModel.batchAdWatchCount.collectAsState()

        ModernRewardedAdDialog(
            count = watchCount,
            isLoading = adState is BatchAdState.Loading,
            title = "Watch video to save",
            message = "Watch a short video before saving this image.",
            watchButtonText = "WATCH VIDEO",
            showUpgradeButton = false,
            onWatchAd = {
                activity?.let {
                    appViewModel.watchAdForBatch(it) {
                        val action = pendingSaveAction
                        showSaveRewardedAdDialog = false
                        pendingSaveAction = null
                        action?.invoke()
                    }
                }
            },
            onUpgrade = {
                showSaveRewardedAdDialog = false
                pendingSaveAction = null
                appViewModel.resetBatchAdState()
            },
            onDismiss = {
                showSaveRewardedAdDialog = false
                pendingSaveAction = null
                appViewModel.resetBatchAdState()
            }
        )
    }

    if (showReviewPromptDialog) {
        ReviewPromptDialog(
            onRateNow = {
                showReviewPromptDialog = false
                appViewModel.markReviewAccepted()
                openReviewStore()
            },
            onLater = {
                showReviewPromptDialog = false
                if (reviewPromptSource == ReviewPromptSource.Auto) {
                    appViewModel.markReviewDeclined()
                }
            }
        )
    }

    val blockedTemplate by appViewModel.premiumLimitBlockedTemplate.collectAsState()
    if (blockedTemplate != null) {
        AlertDialog(
            onDismissRequest = { appViewModel.dismissPremiumLimitDialog() },
            title = {
                Text(
                    text = "Giới hạn Premium hôm nay",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Bạn đã chỉnh sửa hết 3/3 template Premium miễn phí của ngày hôm nay. Hãy nâng cấp lên gói PRO để sử dụng không giới hạn, hoặc xem một video quảng cáo ngắn để nhận thêm lượt mở cho template này.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appViewModel.dismissPremiumLimitDialog()
                        showPremiumScreen = true
                    }
                ) {
                    Text(
                        text = "MUA BẢN PRO",
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val template = blockedTemplate
                            if (template != null && activity != null) {
                                appViewModel.watchAdForPremiumSlot(activity, template.id) {
                                    appViewModel.dismissPremiumLimitDialog()
                                    navController.navigate(Screen.StudioEditor.createRoute(template.id))
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "XEM QUẢNG CÁO",
                            color = Color(0xFF00C6FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { appViewModel.dismissPremiumLimitDialog() }
                    ) {
                        Text(
                            text = "ĐÓNG",
                            color = Color.Gray
                        )
                    }
                }
            }
        )
    }

    // ── Quality sheet ─────────────────────────────────────────────────────
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            sheetState = qualitySheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_remove_background_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                RemovalQualitySelector(
                    preferredQuality = appState.preferredRemovalQuality,
                    isPremium = appState.isPremium,
                    useHomeDarkStyle = appState.isDarkMode,
                    onSelectStandard = { appViewModel.setPreferredRemovalQuality("standard") },
                    onSelectPro = { appViewModel.setPreferredRemovalQuality("pro") },
                    onProLockedClick = {
                        showQualitySheet = false
                        showPremiumScreen = true
                    }
                )
            }
        }
    }

    // ── Exit dialog ───────────────────────────────────────────────────────
    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirmExit = {
                showExitDialog = false
                activity?.finish()
            },
            onDismiss = { showExitDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExitConfirmationDialog(onConfirmExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exit_dialog_title)) },
        text = { Text(stringResource(R.string.exit_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirmExit) {
                Text(stringResource(R.string.exit_dialog_exit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.exit_dialog_cancel))
            }
        }
    )
}

@Composable
private fun LanguageDropdownItem(
    label: String,
    code: String,
    selectedLanguage: String,
    onLanguageClick: (String) -> Unit
) {
    DropdownMenuItem(
        text = { Text(if (selectedLanguage == code) "✓ $label" else label) },
        onClick = { onLanguageClick(code) }
    )
}

private data class HomeLanguageOption(
    val code: String,
    val label: String
)

@Composable
private fun homeLanguageOptions(context: Context): List<HomeLanguageOption> = listOf(
    HomeLanguageOption("system", systemLanguageLabel(context)),
    HomeLanguageOption("en", stringResource(R.string.language_english)),
    HomeLanguageOption("af", "Afrikaans"),
    HomeLanguageOption("am", "አማርኛ"),
    HomeLanguageOption("ar", stringResource(R.string.language_arabic)),
    HomeLanguageOption("az", "Azərbaycanca"),
    HomeLanguageOption("be", "Беларуская"),
    HomeLanguageOption("bg", "Български"),
    HomeLanguageOption("bn", "বাংলা"),
    HomeLanguageOption("ca", "Català"),
    HomeLanguageOption("cs", "Čeština"),
    HomeLanguageOption("da", "Dansk"),
    HomeLanguageOption("de", stringResource(R.string.language_german)),
    HomeLanguageOption("el", "Ελληνικά"),
    HomeLanguageOption("es", stringResource(R.string.language_spanish)),
    HomeLanguageOption("et", "Eesti"),
    HomeLanguageOption("eu", "Euskara"),
    HomeLanguageOption("fa", "فارسی"),
    HomeLanguageOption("fi", "Suomi"),
    HomeLanguageOption("fr", stringResource(R.string.language_french)),
    HomeLanguageOption("gl", "Galego"),
    HomeLanguageOption("gu", "ગુજરાતી"),
    HomeLanguageOption("hi-IN", stringResource(R.string.language_hindi)),
    HomeLanguageOption("hr", "Hrvatski"),
    HomeLanguageOption("hu", "Magyar"),
    HomeLanguageOption("id", stringResource(R.string.language_indonesian)),
    HomeLanguageOption("is", "Íslenska"),
    HomeLanguageOption("it", stringResource(R.string.language_italian)),
    HomeLanguageOption("he", "עברית"),
    HomeLanguageOption("ja", stringResource(R.string.language_japanese)),
    HomeLanguageOption("ka", "ქართული"),
    HomeLanguageOption("kk", "Қазақша"),
    HomeLanguageOption("km", "ខ្មែរ"),
    HomeLanguageOption("kn", "ಕನ್ನಡ"),
    HomeLanguageOption("ko", stringResource(R.string.language_korean)),
    HomeLanguageOption("lo", "ລາວ"),
    HomeLanguageOption("lt", "Lietuvių"),
    HomeLanguageOption("lv", "Latviešu"),
    HomeLanguageOption("mk", "Македонски"),
    HomeLanguageOption("ml", "മലയാളം"),
    HomeLanguageOption("mn", "Монгол"),
    HomeLanguageOption("mr", "मराठी"),
    HomeLanguageOption("ms", "Bahasa Melayu"),
    HomeLanguageOption("my", "မြန်မာ"),
    HomeLanguageOption("ne", "नेपाली"),
    HomeLanguageOption("nl", "Nederlands"),
    HomeLanguageOption("no", "Norsk"),
    HomeLanguageOption("pl", stringResource(R.string.language_polish)),
    HomeLanguageOption("pt-BR", stringResource(R.string.language_portuguese_br)),
    HomeLanguageOption("ro", "Română"),
    HomeLanguageOption("ru", "Русский"),
    HomeLanguageOption("si", "සිංහල"),
    HomeLanguageOption("sk", "Slovenčina"),
    HomeLanguageOption("sl", "Slovenščina"),
    HomeLanguageOption("sr", "Српски"),
    HomeLanguageOption("sv", "Svenska"),
    HomeLanguageOption("sw", "Kiswahili"),
    HomeLanguageOption("ta", "தமிழ்"),
    HomeLanguageOption("te", "తెలుగు"),
    HomeLanguageOption("th", stringResource(R.string.language_thai)),
    HomeLanguageOption("tl", "Filipino"),
    HomeLanguageOption("tr-TR", stringResource(R.string.language_turkish)),
    HomeLanguageOption("uk", "Українська"),
    HomeLanguageOption("ur", "اردو"),
    HomeLanguageOption("uz", "Oʻzbek"),
    HomeLanguageOption("vi", stringResource(R.string.language_vietnamese)),
    HomeLanguageOption("zh-CN", stringResource(R.string.language_chinese_simplified)),
    HomeLanguageOption("zh-TW", stringResource(R.string.language_chinese_traditional)),
    HomeLanguageOption("zu", "IsiZulu")
)

private fun systemLanguageLabel(context: Context): String {
    val locale = systemLocale()
    val config = Configuration()
    config.setLocale(locale)
    val localizedContext = context.createConfigurationContext(config)
    return localizedContext.getString(R.string.language_system)
}

private fun systemLocale(): Locale {
    val systemConfig = Resources.getSystem().configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        systemConfig.locales[0]
    } else {
        @Suppress("DEPRECATION")
        systemConfig.locale
    }
}

@Composable
private fun ProBadgeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(brush = ImageDesign.gradients.cta)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.pro_badge),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}


