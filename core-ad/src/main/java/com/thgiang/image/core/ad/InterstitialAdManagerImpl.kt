package com.thgiang.image.core.ad

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdManagerImpl @Inject constructor(
    private val adConfig: AdConfig,
    private val adLogger: AdLogger,
    @ApplicationContext private val appContext: Context
) : InterstitialAdManager {

    @Volatile override var isPremiumUser: Boolean = false

    private var interstitialAd: InterstitialAd? = null
    @Volatile private var isLoading = false
    private var retryCount = 0
    private var onAdClosedCallback: (() -> Unit)? = null
    private var pendingShowActivityRef: WeakReference<Activity>? = null
    private var pendingShowCallback: (() -> Unit)? = null
    private var lastShownTime: Long = 0

    override fun showAdIfAvailable(activity: Activity, onClosed: (() -> Unit)?) {
        if (isPremiumUser) {
            adLogger.d(TAG, "Premium user, bypass ad showing")
            onClosed?.invoke()
            return
        }

        if (NewUserAdPolicy.shouldBypassInterstitial(appContext)) {
            adLogger.d(TAG, "Low-trust user, bypass interstitial")
            NewUserAdPolicy.reportBypassIfNeeded(appContext, "interstitial")
            onClosed?.invoke()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastShownTime < adConfig.interstitialCooldownMs) {
            adLogger.d(TAG, "Ad cooldown in progress, bypassing")
            onClosed?.invoke()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            if (activity.isFinishing || activity.isDestroyed) {
                adLogger.w(TAG, "Activity is no longer valid; bypassing interstitial")
                onClosed?.invoke()
                return
            }
            adLogger.d(TAG, "Showing interstitial ad")
            onAdClosedCallback = onClosed
            try {
                ad.show(activity)
            } catch (error: RuntimeException) {
                adLogger.w(TAG, "Interstitial show threw: ${error.message}")
                interstitialAd = null
                onAdClosedCallback = null
                onClosed?.invoke()
                loadAd()
            }
        } else {
            adLogger.d(TAG, "No interstitial ad available, queueing pending show")
            pendingShowActivityRef = WeakReference(activity)
            pendingShowCallback = onClosed
            loadAd()
        }
    }

    override fun isAdAvailable(): Boolean = interstitialAd != null

    /** Called once by AdModule @Provides to kick off first load. */
    @RequiresPermission(Manifest.permission.INTERNET)
    internal fun initialize() {
        adLogger.d(TAG, "Initializing MobileAds SDK")
        MobileAds.initialize(appContext)
        loadAd()
    }

    private fun loadAd() {
        if (isPremiumUser) {
            adLogger.d(TAG, "User is premium, skip loading ads")
            return
        }
        if (!NewUserAdPolicy.shouldPreloadAds(appContext)) {
            adLogger.d(TAG, "Low-trust user, skip loading interstitial")
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { loadAd() }
            return
        }
        if (isLoading) return
        isLoading = true
        adLogger.d(TAG, "Loading interstitial ad...")
        InterstitialAd.load(
            appContext,
            adConfig.interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    adLogger.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isLoading = false
                    retryCount = 0
                    setupFullScreenCallback(ad)
                    showPendingIfReady()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLogger.w(TAG, "Interstitial ad failed to load: ${error.message}, code: ${error.code}")
                    interstitialAd = null
                    isLoading = false
                    if (retryCount >= adConfig.maxRetries) {
                        showPendingFallback()
                    }
                    scheduleRetry()
                }
            }
        )
    }

    private fun showPendingIfReady() {
        val activity = pendingShowActivityRef?.get()
        val callback = pendingShowCallback
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            pendingShowActivityRef = null
            pendingShowCallback = null
            callback?.invoke()
            return
        }
        val ad = interstitialAd ?: return
        pendingShowActivityRef = null
        pendingShowCallback = null
        onAdClosedCallback = callback
        adLogger.d(TAG, "Showing pending interstitial ad")
        try {
            ad.show(activity)
        } catch (error: RuntimeException) {
            adLogger.w(TAG, "Pending interstitial show threw: ${error.message}")
            interstitialAd = null
            onAdClosedCallback = null
            callback?.invoke()
            loadAd()
        }
    }

    private fun showPendingFallback() {
        val callback = pendingShowCallback ?: return
        pendingShowActivityRef = null
        pendingShowCallback = null
        callback.invoke()
    }

    private fun setupFullScreenCallback(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adLogger.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
                onAdClosedCallback?.invoke()
                onAdClosedCallback = null
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adLogger.w(TAG, "Interstitial ad failed to show: ${error.message}")
                interstitialAd = null
                onAdClosedCallback?.invoke()
                onAdClosedCallback = null
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                adLogger.d(TAG, "Interstitial ad showed")
                lastShownTime = System.currentTimeMillis()
            }
        }
    }

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
        private const val TAG = "InterstitialAdMgr"
    }
}
