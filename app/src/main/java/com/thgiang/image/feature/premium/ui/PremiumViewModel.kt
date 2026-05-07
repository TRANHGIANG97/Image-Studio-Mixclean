package com.thgiang.image.feature.premium.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.feature.premium.domain.PremiumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val premiumRepository: PremiumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            premiumRepository.products.collect { products ->
                _uiState.value = _uiState.value.copy(products = products)
            }
        }
        viewModelScope.launch {
            premiumRepository.isPremium.collect { isPremium ->
                _uiState.value = _uiState.value.copy(isPremium = isPremium)
            }
        }
        premiumRepository.connectIfNeeded()
    }

    fun refreshProducts() {
        premiumRepository.connectIfNeeded()
    }

    fun purchase(activity: android.app.Activity, planType: String) {
        val productId = when (planType) {
            "monthly" -> "mixclean_pro_monthly"
            "yearly" -> "mixclean_pro_yearly"
            "lifetime" -> "mixclean_pro_lifetime"
            else -> return
        }
        premiumRepository.purchase(activity, productId)
    }

    fun restorePurchases() {
        premiumRepository.restorePurchases()
    }
}
