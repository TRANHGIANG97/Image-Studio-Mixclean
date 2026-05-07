package com.thgiang.image.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thgiang.image.app.navigation.AppRoot
import com.thgiang.image.app.navigation.AppViewModel
import com.thgiang.image.core.ad.AppOpenAdManager
import com.thgiang.image.core.design.theme.ImageTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var appOpenAdManager: AppOpenAdManager

    override fun onStart() {
        super.onStart()
        appOpenAdManager.showAdIfAvailable(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var showStartAd by mutableStateOf(true)

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val appState = appViewModel.uiState.collectAsState().value
            val selectedLanguage = appViewModel.selectedLanguage.collectAsState().value
            val isPremium = appState.isPremium

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
                if (showStartAd) {
                    SplashAdScreen(
                        isPremium = isPremium,
                        onDone = { showStartAd = false }
                    )
                } else {
                    AppRoot(appViewModel = appViewModel)
                }
            }
        }
    }
}

@Composable
private fun SplashAdScreen(
    isPremium: Boolean,
    onDone: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (isPremium) {
            onDone()
            return@LaunchedEffect
        }

        // Wait for AppOpenAd (shown in onStart) to finish, then proceed
        delay(4000)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "MixClean",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF007AFF),
                strokeWidth = 2.dp
            )
        }
    }
}
