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

    override fun loadAd(onLoaded: (() -> Unit)?, onFailed: ((String) -> Unit)?) {
        onLoaded?.invoke()
    }

    override fun showAd(
        activity: Activity,
        onRewardReceived: () -> Unit,
        onAdClosed: () -> Unit,
        onFailedToShow: ((String) -> Unit)?
    ) {
        onRewardReceived()
        onAdClosed()
    }

    override fun isAdAvailable(): Boolean = true

    companion object {
        private const val TAG = "RewardedAdMgr"
    }
}
