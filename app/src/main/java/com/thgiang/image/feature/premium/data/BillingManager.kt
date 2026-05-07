package com.thgiang.image.feature.premium.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.thgiang.image.feature.premium.domain.BillingProduct
import com.thgiang.image.feature.premium.domain.BillingProductType
import com.thgiang.image.feature.premium.domain.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PremiumRepository, PurchasesUpdatedListener {

    companion object {
        const val MONTHLY = "mixclean_pro_monthly"
        const val YEARLY = "mixclean_pro_yearly"
        const val LIFETIME = "mixclean_pro_lifetime"
        val SUBSCRIPTION_IDS = listOf(MONTHLY, YEARLY)
        val INAPP_IDS = listOf(LIFETIME)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _products = MutableStateFlow<List<BillingProduct>>(emptyList())
    override val products: StateFlow<List<BillingProduct>> = _products.asStateFlow()

    private var isConnected = false
    private var connectionRetries = 0

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    connectionRetries = 0
                    queryExistingPurchases()
                    queryAllProducts()
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
                if (connectionRetries < 3) {
                    connectionRetries++
                    scope.launch {
                        kotlinx.coroutines.delay(3000L * connectionRetries)
                        connect()
                    }
                }
            }
        })
    }

    override fun connectIfNeeded() {
        if (!isConnected) connect()
    }

    private fun queryAllProducts() {
        // Subscriptions
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(SUBSCRIPTION_IDS.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()

        billingClient.queryProductDetailsAsync(subParams) { _, details ->
            val products = details.mapNotNull { it.toBillingProduct(BillingProductType.SUBSCRIPTION) }
            if (products.isNotEmpty()) {
                val filtered = _products.value.filter { it.type != BillingProductType.SUBSCRIPTION }
                _products.value = filtered + products
            }
        }

        // In-app
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(INAPP_IDS.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { _, details ->
            val products = details.mapNotNull { it.toBillingProduct(BillingProductType.LIFETIME) }
            if (products.isNotEmpty()) {
                val filtered = _products.value.filter { it.type != BillingProductType.LIFETIME }
                _products.value = filtered + products
            }
        }
    }

    private fun ProductDetails.toBillingProduct(type: BillingProductType): BillingProduct? {
        return when (type) {
            BillingProductType.SUBSCRIPTION -> {
                val offer = subscriptionOfferDetails?.firstOrNull() ?: return null
                val phases = offer.pricingPhases.pricingPhaseList
                if (phases.isEmpty()) return null
                val fullPhase = phases.last()
                val introPhase = phases.firstOrNull()

                BillingProduct(
                    id = productId,
                    title = name ?: productId,
                    price = fullPhase.formattedPrice,
                    currencyCode = fullPhase.priceCurrencyCode,
                    type = type,
                    freeTrialPeriod = if (introPhase != null && introPhase.billingCycleCount > 0 && introPhase.recurrenceMode == ProductDetails.RecurrenceMode.NON_RECURRING) {
                        "${introPhase.billingCycleCount} day(s)"
                    } else null,
                    introductoryPrice = introPhase?.formattedPrice,
                    productDetails = this,
                    offerToken = offer.offerToken
                )
            }
            BillingProductType.LIFETIME -> {
                val price = oneTimePurchaseOfferDetails ?: return null
                BillingProduct(
                    id = productId,
                    title = name ?: productId,
                    price = price.formattedPrice,
                    currencyCode = price.priceCurrencyCode,
                    type = type,
                    productDetails = this
                )
            }
        }
    }

    override fun purchase(activity: Activity, productId: String) {
        val product = _products.value.find { it.id == productId } ?: return

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product.productDetails)
                        .apply {
                            product.offerToken?.let { setOfferToken(it) }
                        }
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(activity, params)
    }

    override fun restorePurchases() {
        if (!isConnected) {
            connect()
            return
        }
        queryExistingPurchases()
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPremium.value = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    _isPremium.value = true
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            for (purchase in purchases) {
                if (!purchase.isAcknowledged) {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    ) { _ -> }
                }
            }
            queryExistingPurchases()
        }
    }

    fun onDestroy() {
        billingClient.endConnection()
    }
}
