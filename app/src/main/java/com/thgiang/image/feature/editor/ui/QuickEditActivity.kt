package com.thgiang.image.feature.editor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.thgiang.image.core.design.adaptive.AdaptiveEditorScaffold
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickEditActivity : AppCompatActivity() {

    @Inject
    lateinit var rewardedAdManager: com.thgiang.image.core.ad.RewardedAdManager

    private val quickEditViewModel: QuickEditViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
        val backgroundGradientPresetId = intent.getStringExtra(EXTRA_BACKGROUND_GRADIENT_PRESET_ID)
        val borderGradientPresetId = intent.getStringExtra(EXTRA_BORDER_GRADIENT_PRESET_ID)
        val targetTool = intent.getStringExtra(EXTRA_TARGET_TOOL)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            EditorTheme {
                AdaptiveEditorScaffold(windowSizeClass = windowSizeClass) {
                    QuickEditEditorNavigation(
                        viewModel = quickEditViewModel,
                        initialImageUri = uri,
                        draftId = draftId,
                        autoRemoveBackground = autoRemoveBg,
                        backgroundGradientPresetId = backgroundGradientPresetId,
                        borderGradientPresetId = borderGradientPresetId,
                        targetTool = targetTool,
                        rewardedAdManager = rewardedAdManager
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_DRAFT_ID = "extra_draft_id"
        private const val EXTRA_AUTO_REMOVE_BG = "extra_auto_remove_bg"
        private const val EXTRA_BACKGROUND_GRADIENT_PRESET_ID = "extra_background_gradient_preset_id"
        private const val EXTRA_BORDER_GRADIENT_PRESET_ID = "extra_border_gradient_preset_id"
        private const val EXTRA_TARGET_TOOL = "extra_target_tool"

        fun createIntent(
            context: Context,
            uri: Uri? = null,
            draftId: String? = null,
            autoRemoveBackground: Boolean = false,
            backgroundGradientPresetId: String? = null,
            borderGradientPresetId: String? = null,
            targetTool: String? = null
        ): Intent {
            return Intent(context, QuickEditActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, uri)
                putExtra(EXTRA_DRAFT_ID, draftId)
                putExtra(EXTRA_AUTO_REMOVE_BG, autoRemoveBackground)
                if (!backgroundGradientPresetId.isNullOrBlank()) {
                    putExtra(EXTRA_BACKGROUND_GRADIENT_PRESET_ID, backgroundGradientPresetId)
                }
                if (!borderGradientPresetId.isNullOrBlank()) {
                    putExtra(EXTRA_BORDER_GRADIENT_PRESET_ID, borderGradientPresetId)
                }
                if (!targetTool.isNullOrBlank()) {
                    putExtra(EXTRA_TARGET_TOOL, targetTool)
                }
            }
        }
    }
}
