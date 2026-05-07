package com.thgiang.image.feature.premium.domain

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.StateFlow

data class BillingProduct(
    val id: String,
    val title: String,
    val price: String,
    val currencyCode: String,
    val type: BillingProductType,
    val freeTrialPeriod: String? = null,
    val introductoryPrice: String? = null,
    val productDetails: ProductDetails,
    val offerToken: String? = null
)

enum class BillingProductType { SUBSCRIPTION, LIFETIME }

interface PremiumRepository {
    val isPremium: StateFlow<Boolean>
    val products: StateFlow<List<BillingProduct>>

    fun connectIfNeeded()
    fun purchase(activity: Activity, productId: String)
    fun restorePurchases()
}
