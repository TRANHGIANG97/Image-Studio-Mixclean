package com.thgiang.image.app

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thgiang.image.BuildConfig
import com.thgiang.image.core.analytics.AppAnalytics
import com.thgiang.image.core.analytics.InstallReferrerCapture
import com.thgiang.image.core.analytics.PlayIntegrityChecker
import com.thgiang.image.core.analytics.SessionQualityTracker
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.data.backgroundremove.MlKitDeviceSupport
import com.thgiang.image.core.data.backgroundremove.SelfieFallbackSegmenter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@HiltAndroidApp
class ImageApp : Application() {

    @Inject
    lateinit var backgroundRemoverRepository: BackgroundRemoverRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        // Allow HttpURLConnection to follow HTTP→HTTPS redirects (needed for admin_web proxy fallback)
        System.setProperty("http.redirectToHttps", "true")
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("CoilNetwork", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(loggingInterceptor)
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .okHttpClient(okHttpClient)
            .eventListener(object : coil.EventListener {
                override fun onStart(request: coil.request.ImageRequest) {
                    super.onStart(request)
                    Log.d("CoilError", "Start loading image URL: ${request.data}")
                }
                override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                    super.onSuccess(request, result)
                    Log.d("CoilError", "Success loading image URL: ${request.data}")
                }
                override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                    super.onError(request, result)
                    Log.e("CoilError", "Error loading image URL: ${request.data}", result.throwable)
                }
            })
            .build()
        Coil.setImageLoader(imageLoader)
        installProxyBillingCrashGuard()
        if (!BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            com.google.firebase.analytics.FirebaseAnalytics
                .getInstance(this)
                .setAnalyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().setCustomKey("app_start_package", packageName)
        }
        com.thgiang.image.core.ad.NewUserAdPolicy.bypassListener = { adType, reason ->
            AppAnalytics.onAdBypassed(applicationContext, adType, reason)
        }
        registerActivityLifecycleCallbacks(AppEngagementLifecycleCallbacks())
        InstallReferrerCapture.captureAsync(this)
        if (BuildConfig.DEBUG && MlKitDeviceSupport.isX86Emulator()) {
            appScope.launch {
                runCatching { SelfieFallbackSegmenter.warmUp(applicationContext) }
                    .onFailure { Log.w("ImageApp", "Selfie warm-up failed", it) }
            }
        }
        appScope.launch {
            PlayIntegrityChecker.checkAndLog(applicationContext)
        }
        com.google.android.gms.ads.MobileAds.initialize(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE,
            -> {
                Coil.imageLoader(this).memoryCache?.clear()
                System.gc()
                Log.w("ImageApp", "onTrimMemory level=$level — cleared image memory cache")
            }
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_BACKGROUND,
            -> {
                Coil.imageLoader(this).memoryCache?.clear()
            }
        }
    }

    /**
     * Play Billing's ProxyBillingActivity NPEs when started with a null BUY_INTENT
     * PendingIntent (bad launchBillingFlow params, Play Services glitches, or
     * external/bot intents). Prevent process death for this known library crash.
     */
    private fun installProxyBillingCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isProxyBillingNullPendingIntentCrash(throwable)) {
                Log.w(
                    "ImageApp",
                    "Swallowed ProxyBillingActivity null PendingIntent crash",
                    throwable
                )
                runCatching {
                    FirebaseCrashlytics.getInstance().recordException(throwable)
                }
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun isProxyBillingNullPendingIntentCrash(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val message = current.message.orEmpty()
            val fromProxyBilling = current.stackTrace.any {
                it.className.contains("ProxyBillingActivity")
            }
            val nullPendingIntent =
                current is NullPointerException &&
                    (message.contains("getIntentSender") || message.contains("PendingIntent"))
            val unableToStartProxy =
                message.contains("ProxyBillingActivity") &&
                    (message.contains("PendingIntent") || message.contains("NullPointerException"))
            if ((fromProxyBilling && nullPendingIntent) || unableToStartProxy) {
                return true
            }
            // Also match the wrapping RuntimeException: Unable to start activity ... ProxyBillingActivity
            if (message.contains("ProxyBillingActivity") &&
                current.cause is NullPointerException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private inner class AppEngagementLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityResumed(activity: android.app.Activity) {
            SessionQualityTracker.onScreenView()
        }
        override fun onActivityPaused(activity: android.app.Activity) = Unit
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: android.app.Activity) = Unit

        override fun onActivityStarted(activity: android.app.Activity) {
            if (startedActivityCount++ == 0) {
                SessionQualityTracker.onSessionStart()
                AppAnalytics.onAppSessionStart(applicationContext)
            }
        }

        override fun onActivityStopped(activity: android.app.Activity) {
            if (--startedActivityCount <= 0) {
                startedActivityCount = 0
                SessionQualityTracker.scoreAndLog(applicationContext)
            }
        }
    }

    override fun onTerminate() {
        if (::backgroundRemoverRepository.isInitialized) {
            backgroundRemoverRepository.close()
        }
        super.onTerminate()
    }
}
