package com.thgiang.image.core.ad

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManagerImpl @Inject constructor(
    private val adConfig: AdConfig,
    private val adLogger: AdLogger,
    @ApplicationContext private val appContext: Context
) : RewardedAdManager {

    private var rewardedAd: RewardedAd? = null
    @Volatile private var isLoading = false
    private var retryCount = 0

    private val isDebug: Boolean
        get() = (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun loadAd(onLoaded: (() -> Unit)?, onFailed: ((String) -> Unit)?) {
        if (isDebug) {
            adLogger.d(TAG, "Debug build: skip loading real AdMob rewarded ad")
            onLoaded?.invoke()
            return
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { loadAd(onLoaded, onFailed) }
            return
        }

        if (rewardedAd != null) {
            adLogger.d(TAG, "Rewarded ad already loaded")
            onLoaded?.invoke()
            return
        }

        if (isLoading) return
        isLoading = true

        adLogger.d(TAG, "Loading rewarded ad...")
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            adConfig.rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    adLogger.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = ad
                    isLoading = false
                    retryCount = 0
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    adLogger.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isLoading = false
                    onFailed?.invoke(loadAdError.message)
                    scheduleRetry()
                }
            }
        )
    }

    override fun showAd(
        activity: Activity,
        onRewardReceived: () -> Unit,
        onAdClosed: () -> Unit,
        onFailedToShow: ((String) -> Unit)?
    ) {
        if (isDebug) {
            adLogger.d(TAG, "Debug build: bypass rewarded ad showing and grant reward immediately")
            onRewardReceived()
            onAdClosed()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            adLogger.d(TAG, "Showing rewarded ad")

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    adLogger.d(TAG, "Rewarded ad dismissed")
                    rewardedAd = null
                    onAdClosed()
                    // Load next ad for caching
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    adLogger.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    onFailedToShow?.invoke(adError.message)
                    // Fallback to grant reward anyway if show fails
                    onRewardReceived()
                    onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {
                    adLogger.d(TAG, "Rewarded ad showed")
                }
            }

            ad.show(activity, OnUserEarnedRewardListener {
                adLogger.d(TAG, "User earned reward")
                onRewardReceived()
            })
        } else {
            adLogger.w(TAG, "No rewarded ad available to show, trying to load and granting bypass fallback")
            onRewardReceived()
            onAdClosed()
            loadAd()
        }
    }

    override fun isAdAvailable(): Boolean = isDebug || rewardedAd != null

    private fun scheduleRetry() {
        if (retryCount >= adConfig.maxRetries) {
            adLogger.d(TAG, "Max retries reached, will retry later")
            return
        }
        retryCount++
        CoroutineScope(Dispatchers.IO).launch {
            delay(adConfig.retryDelayMs)
            loadAd()
        }
    }

    companion object {
        private const val TAG = "RewardedAdMgr"
    }
}
