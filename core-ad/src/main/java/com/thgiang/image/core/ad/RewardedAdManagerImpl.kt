package com.thgiang.image.core.ad

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManagerImpl @Inject constructor(
    private val adConfig: AdConfig,
    private val adLogger: AdLogger,
    @ApplicationContext private val appContext: Context
) : RewardedAdManager {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<Pair<(() -> Unit)?, ((String) -> Unit)?>>()

    override fun loadAd(onLoaded: (() -> Unit)?, onFailed: ((String) -> Unit)?) {
        if (rewardedAd != null) {
            onLoaded?.invoke()
            return
        }
        
        pendingCallbacks.add(onLoaded to onFailed)
        if (isLoading) return
        
        isLoading = true
        adLogger.d(TAG, "Loading rewarded ad...")
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(appContext, adConfig.rewardedAdUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                adLogger.w(TAG, "Rewarded ad failed to load: ${adError.message}")
                rewardedAd = null
                isLoading = false
                
                val callbacks = pendingCallbacks.toList()
                pendingCallbacks.clear()
                callbacks.forEach { it.second?.invoke(adError.message) }
            }

            override fun onAdLoaded(ad: RewardedAd) {
                adLogger.d(TAG, "Rewarded ad loaded")
                rewardedAd = ad
                isLoading = false
                
                val callbacks = pendingCallbacks.toList()
                pendingCallbacks.clear()
                callbacks.forEach { it.first?.invoke() }
            }
        })
    }

    override fun showAd(
        activity: Activity,
        onRewardReceived: () -> Unit,
        onAdClosed: () -> Unit,
        onFailedToShow: ((String) -> Unit)?
    ) {
        if (rewardedAd == null) {
            adLogger.w(TAG, "Rewarded ad not ready yet")
            onAdClosed()
            onFailedToShow?.invoke("Rewarded ad is not ready yet")
            loadAd()
            return
        }

        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adLogger.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                onAdClosed()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                adLogger.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                rewardedAd = null
                onAdClosed()
                onFailedToShow?.invoke(adError.message)
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                adLogger.d(TAG, "Rewarded ad showed")
            }
        }

        rewardedAd?.show(activity, OnUserEarnedRewardListener { rewardItem ->
            adLogger.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewardReceived()
        })
    }

    override fun isAdAvailable(): Boolean = rewardedAd != null

    companion object {
        private const val TAG = "RewardedAdMgr"
    }
}
