package com.thgiang.image.app

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                AppRoot(appViewModel = appViewModel)
            }
        }
    }
}
