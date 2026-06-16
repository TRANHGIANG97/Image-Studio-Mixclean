package com.thgiang.image.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thgiang.image.app.navigation.AppRoot
import com.thgiang.image.app.navigation.AppViewModel
import com.thgiang.image.core.design.theme.ImageTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_INITIAL_ROUTE = "extra_initial_route"

        fun createIntent(context: Context, initialRoute: String? = null): Intent {
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (!initialRoute.isNullOrBlank()) {
                    putExtra(EXTRA_INITIAL_ROUTE, initialRoute)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var initialRoute = intent.getStringExtra(EXTRA_INITIAL_ROUTE)
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "quickedit" && data.host == "preview") {
                val templateId = data.getQueryParameter("templateId")
                if (!templateId.isNullOrEmpty()) {
                    initialRoute = "studio_editor/$templateId"
                }
            }
        }

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val appState = appViewModel.uiState.collectAsState().value
            val selectedLanguage = appViewModel.selectedLanguage.collectAsState().value

            LaunchedEffect(selectedLanguage) {
                val targetLocales = when (selectedLanguage) {
                    "system" -> LocaleListCompat.getEmptyLocaleList()
                    else -> LocaleListCompat.forLanguageTags(selectedLanguage)
                }
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != targetLocales.toLanguageTags()) {
                    AppCompatDelegate.setApplicationLocales(targetLocales)
                }
            }

            ImageTheme(darkTheme = appState.isDarkMode) {
                AppRoot(
                    appViewModel = appViewModel,
                    initialRoute = initialRoute
                )
            }
        }
    }
}
