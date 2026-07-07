package com.thgiang.image.app

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thgiang.image.BuildConfig
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@HiltAndroidApp
class ImageApp : Application() {

    @Inject
    lateinit var backgroundRemoverRepository: BackgroundRemoverRepository

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
        if (!BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().setCustomKey("app_start_package", packageName)
        }
        com.google.android.gms.ads.MobileAds.initialize(this)
    }

    override fun onTerminate() {
        if (::backgroundRemoverRepository.isInitialized) {
            backgroundRemoverRepository.close()
        }
        super.onTerminate()
    }
}
