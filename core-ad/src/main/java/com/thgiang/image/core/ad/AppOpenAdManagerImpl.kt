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
        // Open App Ads disabled
        return
    }

    override fun wasAdDismissedRecently(): Boolean {
        return false
    }

    private fun loadAd() {
        // Open App Ads disabled
        return
    }

    private fun isAdAvailable(): Boolean = false

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
