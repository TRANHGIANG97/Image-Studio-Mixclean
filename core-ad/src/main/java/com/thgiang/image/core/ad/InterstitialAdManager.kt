package com.thgiang.image.core.ad

import android.app.Activity

/** Interface cho Interstitial Ad — cho phép mock trong test. */
interface InterstitialAdManager {
    var isPremiumUser: Boolean
    fun showAdIfAvailable(activity: Activity, onClosed: (() -> Unit)? = null)
    fun isAdAvailable(): Boolean
}
