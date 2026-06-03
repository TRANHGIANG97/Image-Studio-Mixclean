package com.thgiang.image.admin.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import com.thgiang.image.studio.model.StudioThemeplate
import androidx.compose.runtime.*
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.R

sealed class AdminScreenState {
    data object Dashboard : AdminScreenState()
    data class Builder(val cloudTemplate: CloudTemplate? = null) : AdminScreenState()
}

@AndroidEntryPoint
class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf<AdminScreenState>(AdminScreenState.Dashboard) }

                when (val screen = currentScreen) {
                    is AdminScreenState.Dashboard -> {
                        AdminDashboardScreen(
                            onNavigateToBuilder = { template -> currentScreen = AdminScreenState.Builder(template) },
                            onBack = { finish() }
                        )
                    }
                    is AdminScreenState.Builder -> {
                        val effectiveThemeplate = if (screen.cloudTemplate != null) {
                            // Editing existing template — use a lightweight themeplate
                            // whose assetPath will be immediately overwritten by LoadCloudTemplate
                            themeplateFromCloudTemplate(screen.cloudTemplate)
                        } else {
                            // Creating new template — use default canvas, user can change background
                            defaultNewTemplateThemeplate()
                        }
                        TemplateBuilderScreen(
                            themeplate = effectiveThemeplate,
                            initialCloudTemplate = screen.cloudTemplate,
                            onBack = { currentScreen = AdminScreenState.Dashboard },
                            onDone = { currentScreen = AdminScreenState.Dashboard }
                        )
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Builds a placeholder [StudioThemeplate] from an existing [CloudTemplate].
         * The ViewModel's LoadCloudTemplate will immediately overwrite the asset path
         * with the real background from the template JSON.
         */
        private fun themeplateFromCloudTemplate(cloudTemplate: CloudTemplate): StudioThemeplate {
            return StudioThemeplate(
                id = cloudTemplate.templateId,
                titleResId = R.string.themeplate_cosmetics_01, // fallback title
                assetPath = cloudTemplate.canvas.backgroundUrl.orEmpty(),
                objectSourceAssetPath = null,
                accentColor = androidx.compose.ui.graphics.Color.Black,
                category = cloudTemplate.categoryId
            )
        }

        /**
         * Provides a sensible default [StudioThemeplate] for new template creation.
         * The user will be prompted to select a background image immediately.
         */
        private fun defaultNewTemplateThemeplate(): StudioThemeplate {
            return StudioThemeplate(
                id = "new_template",
                titleResId = R.string.themeplate_cosmetics_01,
                assetPath = "", // empty → user picks background from gallery
                objectSourceAssetPath = null,
                accentColor = androidx.compose.ui.graphics.Color(0xFF7C4DFF),
                category = "custom"
            )
        }
    }
}
