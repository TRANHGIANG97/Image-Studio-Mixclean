package com.thgiang.image.feature.premium.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.core.design.components.*
import androidx.compose.material.icons.rounded.*
import com.thgiang.image.feature.premium.domain.BillingProduct
import com.thgiang.image.feature.premium.domain.BillingProductType

data class PremiumUiState(
    val selectedPlan: String = "monthly",
    val products: List<BillingProduct> = emptyList(),
    val isPremium: Boolean = false
)

@Composable
fun PremiumScreen(
    onClose: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedPlan by remember { mutableStateOf("monthly") }

    LaunchedEffect(Unit) {
        viewModel.refreshProducts()
    }

    Scaffold(
        containerColor = ImageDesign.surfaces.base
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val maxContentWidth = if (maxWidth > 600.dp) 500.dp else maxWidth
            val heroHeight = maxHeight * 0.35f
            val isSmallScreen = maxHeight < 700.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ImageDesign.gradients.appBackground)
            ) {
                // Hero header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        (if (isSystemInDarkTheme()) Color.Black else Color.White).copy(alpha = 0.5f),
                                        ImageDesign.surfaces.base
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerTextColor = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = headerTextColor
                            )
                        }
                        Text(
                            text = "Restore",
                            style = MaterialTheme.typography.labelLarge,
                            color = headerTextColor,
                            modifier = Modifier.clickable { viewModel.restorePurchases() }
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val isDark = isSystemInDarkTheme()
                    val primaryTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    val secondaryTextColor = primaryTextColor.copy(alpha = 0.6f)

                    // Title section
                    Text(
                        text = "MixClean Pro",
                        style = if (isSmallScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Text(
                        text = "Unlock professional tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )

                    // Benefits
                    PremiumFeatureItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Pro Background Removal",
                        description = "State-of-the-art AI for perfect, sharp edges."
                    )
                    PremiumFeatureItem(
                        icon = Icons.Rounded.Layers,
                        title = "Batch Processing",
                        description = "Edit hundreds of photos at once instantly."
                    )
                    PremiumFeatureItem(
                        icon = Icons.Rounded.HighQuality,
                        title = "HD Export",
                        description = "Save your creations in full resolution without limits."
                    )
                    PremiumFeatureItem(
                        icon = Icons.Rounded.Block,
                        title = "Ad-Free Experience",
                        description = "Pure focus on your creativity with zero interruptions."
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Plan cards
                    val monthly = uiState.products.find { it.id == "mixclean_pro_monthly" }
                    val yearly = uiState.products.find { it.id == "mixclean_pro_yearly" }
                    val lifetime = uiState.products.find { it.id == "mixclean_pro_lifetime" }

                    PremiumPlanCard(
                        title = "Monthly",
                        price = monthly?.price ?: "$1.99",
                        subtext = "Flexible access to all Pro features",
                        isSelected = selectedPlan == "monthly",
                        onSelect = { selectedPlan = "monthly" }
                    )

                    PremiumPlanCard(
                        title = "Yearly",
                        price = yearly?.price ?: "$19.99",
                        subtext = "Best for professional creators",
                        isSelected = selectedPlan == "yearly",
                        isBestValue = true,
                        discountBadge = "SAVE 75%",
                        onSelect = { selectedPlan = "yearly" }
                    )

                    PremiumPlanCard(
                        title = "Lifetime",
                        price = lifetime?.price ?: "$49.99",
                        subtext = "One-time payment, forever yours",
                        isSelected = selectedPlan == "lifetime",
                        discountBadge = "ONE TIME",
                        onSelect = { selectedPlan = "lifetime" }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Subscribe button
                    ShimmerGradientButton(
                        text = when (selectedPlan) {
                            "lifetime" -> "GET LIFETIME ACCESS"
                            else -> "START FREE TRIAL"
                        },
                        onClick = {
                            viewModel.purchase(context as Activity, selectedPlan)
                        }
                    )

                    Text(
                        text = when (selectedPlan) {
                            "monthly" -> "Then $1.99/month. Cancel anytime."
                            "yearly" -> "Then $19.99/year. Cancel anytime."
                            "lifetime" -> "Pay once, enjoy forever."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        textAlign = TextAlign.Center
                    )

                    // Legal footer
                    Text(
                        text = "Subscription automatically renews unless canceled at least 24 hours before the end of the current period. " +
                                "Payment will be charged to your Play Store account at the end of the trial. " +
                                "Manage or cancel in your Account Settings.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = secondaryTextColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}


