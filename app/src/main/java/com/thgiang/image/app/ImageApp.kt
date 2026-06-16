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
import java.io.File
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
            .build()
        Coil.setImageLoader(imageLoader)
        clearTransportRuntimeDb()
        if (!BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().setCustomKey("app_start_package", packageName)
        }
        // Initialize AdMob only after stale transport DB files have been removed.
        com.google.android.gms.ads.MobileAds.initialize(this)
    }

    private fun clearTransportRuntimeDb() {
        try {
            val dbDir = getDatabasePath("dummy").parentFile ?: return
            if (!dbDir.isDirectory) return

            val toDelete = listOf(
                "com.google.android.datatransport.events",
                "com.google.android.datatransport.events-journal",
                "com.google.android.datatransport.events-wal",
                "com.google.android.datatransport.events-shm"
            )
            var removed = 0
            for (baseName in toDelete) {
                val file = File(dbDir, baseName)
                if (file.exists() && file.delete()) {
                    removed++
                    Log.i(TAG, "Removed transport DB file: $baseName")
                }
            }
            dbDir.listFiles()?.forEach { file ->
                val name = file.name
                if ((name.startsWith("com.google.android.datatransport") || name.contains("datatransport")) && file.delete()) {
                    removed++
                    Log.i(TAG, "Removed transport DB file: $name")
                }
            }
            if (removed > 0) {
                Log.i(TAG, "Cleared $removed stale transport DB file(s)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "clearTransportRuntimeDb failed", e)
        }
    }

    override fun onTerminate() {
        if (::backgroundRemoverRepository.isInitialized) {
            backgroundRemoverRepository.close()
        }
        super.onTerminate()
    }

    companion object {
        private const val TAG = "ImageApp"
    }
}
