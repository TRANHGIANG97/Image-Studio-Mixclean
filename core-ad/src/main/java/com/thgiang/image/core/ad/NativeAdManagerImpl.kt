package com.thgiang.image.core.ad

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeAdManagerImpl @Inject constructor(
    private val adConfig: AdConfig,
    private val adLogger: AdLogger,
    @ApplicationContext private val appContext: Context
) : NativeAdManager {

    override var isPremiumUser: Boolean = false

    @Volatile private var isLoading = false
    private var retryCount = 0
    private val maxRetries = 2

    override fun loadNativeAd(context: Context, onLoaded: (NativeAd) -> Unit, onFailed: (String) -> Unit) {
        if (isPremiumUser) {
            onFailed("Premium user")
            return
        }
        if (isLoading) {
            adLogger.d("NativeAdManager", "Already loading, skipping")
            return
        }
        isLoading = true
        adLogger.d("NativeAdManager", "Loading native ad (retry=$retryCount)...")

        val adUnitId = adConfig.nativeAdUnitId

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                adLogger.d("NativeAdManager", "Native ad loaded")
                isLoading = false
                retryCount = 0
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLogger.w("NativeAdManager", "Native ad failed: ${error.message}")
                    isLoading = false
                    if (retryCount < maxRetries) {
                        retryCount++
                        adLogger.d("NativeAdManager", "Retrying in 5s...")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadNativeAd(context, onLoaded, onFailed)
                        }, 5000L)
                    } else {
                        retryCount = 0
                        onFailed(error.message)
                    }
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder()
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}
