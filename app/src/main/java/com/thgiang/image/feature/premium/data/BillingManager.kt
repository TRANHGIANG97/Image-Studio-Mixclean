package com.thgiang.image.feature.premium.data

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.*
import com.thgiang.image.feature.premium.PremiumFeatureFlags
import com.thgiang.image.feature.premium.domain.BillingProduct
import com.thgiang.image.feature.premium.domain.BillingProductType
import com.thgiang.image.feature.premium.domain.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PremiumRepository, PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val MONTHLY = "mixclean_pro_monthly"
        const val YEARLY = "mixclean_pro_yearly"
        const val LIFETIME = "mixclean_pro_lifetime"
        val SUBSCRIPTION_IDS = listOf(MONTHLY, YEARLY)
        val INAPP_IDS = listOf(LIFETIME)
    }

    /** Soft purchase failures (missing product, not ready, launchBillingFlow error). */
    private val _purchaseErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val purchaseErrors: SharedFlow<String> = _purchaseErrors.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
    }

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _products = MutableStateFlow<List<BillingProduct>>(emptyList())
    override val products: StateFlow<List<BillingProduct>> = _products.asStateFlow()

    private var isConnected = false
    private var connectionRetries = 0

    init {
        if (PremiumFeatureFlags.enabled) {
            connect()
        }
    }

    private fun connect() {
        if (!PremiumFeatureFlags.enabled) return
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
        if (!PremiumFeatureFlags.enabled) return
        if (!isConnected || !billingClient.isReady) {
            isConnected = false
            connect()
        }
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
        // Master switch: never touch BillingClient / ProxyBillingActivity.
        if (!PremiumFeatureFlags.enabled) return
        if (activity.isFinishing || activity.isDestroyed) {
            softFail("Activity finishing/destroyed; skip purchase for $productId")
            return
        }
        // BillingClient requires launchBillingFlow on the main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    purchase(activity, productId)
                }
            }
            return
        }
        if (!billingClient.isReady) {
            isConnected = false
            softFail("BillingClient not ready; reconnecting for $productId")
            connectIfNeeded()
            return
        }

        val product = _products.value.find { it.id == productId }
        if (product == null) {
            softFail("Product not available (not in Play Console or not queried): $productId")
            return
        }

        val productDetailsParams = buildProductDetailsParams(product) ?: return

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        try {
            val result = billingClient.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                softFail(
                    "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}"
                )
            }
        } catch (t: Throwable) {
            // Never let billing launch exceptions crash the app.
            softFail("launchBillingFlow threw: ${t.message}")
        }
    }

    /**
     * Builds launch params only when ProductDetails / offerToken are valid.
     * Launching with a blank offerToken (subs) or stale/null offer can start
     * ProxyBillingActivity with a null PendingIntent → NPE in onCreate.
     */
    private fun buildProductDetailsParams(
        product: BillingProduct
    ): BillingFlowParams.ProductDetailsParams? {
        val details = product.productDetails
        return when (product.type) {
            BillingProductType.SUBSCRIPTION -> {
                val offerToken = product.offerToken?.takeIf { it.isNotBlank() }
                    ?: details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.offerToken
                        ?.takeIf { it.isNotBlank() }
                if (offerToken == null) {
                    softFail("Missing offerToken for subscription ${product.id}")
                    return null
                }
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            }
            BillingProductType.LIFETIME -> {
                if (details.oneTimePurchaseOfferDetails == null) {
                    softFail("Missing oneTimePurchaseOfferDetails for ${product.id}")
                    return null
                }
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            }
        }
    }

    private fun softFail(reason: String) {
        Log.w(TAG, reason)
        _purchaseErrors.tryEmit(reason)
    }

    override fun restorePurchases() {
        if (!PremiumFeatureFlags.enabled) return
        if (!isConnected) {
            connect()
            return
        }
        queryExistingPurchases()
    }

    private fun queryExistingPurchases() {
        if (!PremiumFeatureFlags.enabled) return
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
        if (!PremiumFeatureFlags.enabled) return
        if (isConnected) {
            billingClient.endConnection()
            isConnected = false
        }
    }
}
