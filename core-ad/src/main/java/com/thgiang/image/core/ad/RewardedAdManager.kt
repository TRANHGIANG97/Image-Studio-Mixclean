package com.thgiang.image.core.ad

import android.app.Activity

/** Interface cho Rewarded Ad — cho phép mock trong test. */
interface RewardedAdManager {
    fun loadAd(onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null)
    fun showAd(
        activity: Activity,
        onRewardReceived: () -> Unit,
        onAdClosed: () -> Unit,
        onFailedToShow: ((String) -> Unit)? = null
    )
    fun isAdAvailable(): Boolean
}
