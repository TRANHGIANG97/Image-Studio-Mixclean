package com.thgiang.image.core.ad

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManagerImpl @Inject constructor(
    private val adConfig: AdConfig,
    private val adLogger: AdLogger,
    @ApplicationContext private val appContext: Context
) : AppOpenAdManager, Application.ActivityLifecycleCallbacks {

    @Volatile override var isPremiumUser: Boolean = false

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var lastShowTime: Long = 0
    private var currentActivity: Activity? = null

    /** Called once by AdModule @Provides to start tracking. */
    internal fun initialize(application: Application) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            application.registerActivityLifecycleCallbacks(this)
            loadAd()
        }
    }

    override fun showAdIfAvailable(activity: Activity) {
        if (isPremiumUser) return
        if (isShowingAd) {
            adLogger.d(TAG, "Ad is already showing")
            return
        }
        if (!isAdAvailable()) {
            adLogger.d(TAG, "Ad is not available, loading a new one")
            loadAd()
            return
        }

        val currentTime = Date().time
        if (currentTime - lastShowTime < adConfig.appOpenCooldownMs) {
            val remaining = (adConfig.appOpenCooldownMs - (currentTime - lastShowTime)) / 1000
            adLogger.d(TAG, "App Open Ad cooldown. Remaining: ${remaining}s")
            return
        }

        adLogger.d(TAG, "Showing App Open Ad")
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adLogger.d(TAG, "App Open Ad dismissed")
                appOpenAd = null
                isShowingAd = false
                lastShowTime = Date().time
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                adLogger.w(TAG, "App Open Ad failed to show: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                adLogger.d(TAG, "App Open Ad showed")
                isShowingAd = true
            }
        }
        isShowingAd = true
        appOpenAd?.show(activity)
    }

    override fun wasAdDismissedRecently(): Boolean {
        return Date().time - lastShowTime < 1000 // 1 second buffer
    }

    private fun loadAd() {
        if (isPremiumUser || isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        adLogger.d(TAG, "Loading App Open Ad...")

        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            appContext,
            adConfig.appOpenAdUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    adLogger.d(TAG, "App Open Ad loaded successfully")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    currentActivity?.let { showAdIfAvailable(it) }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    adLogger.w(TAG, "App Open Ad failed: ${loadAdError.message}")
                    isLoadingAd = false
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val diff = Date().time - loadTime
        return diff < 3_600_000 * numHours
    }

    // --- Activity Lifecycle Callbacks ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }

    companion object {
        private const val TAG = "AppOpenAdMgr"
    }
}
