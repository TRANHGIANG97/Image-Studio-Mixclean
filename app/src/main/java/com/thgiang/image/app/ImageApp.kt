package com.thgiang.image.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class ImageApp : Application() {

    @Inject
    lateinit var backgroundRemoverRepository: BackgroundRemoverRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance().setCustomKey("app_start_package", packageName)
        // Khởi tạo AdMob SDK ngay khi app mở
        com.google.android.gms.ads.MobileAds.initialize(this)
        
        appScope.launch {
            delay(3000)
            fixTransportRuntimeDbIfNeeded()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun fixTransportRuntimeDbIfNeeded() {
        try {
            val prefs = getSharedPreferences("image_app_prefs", MODE_PRIVATE)
            val lastVersion = prefs.getInt("transport_db_cleaned_version", 0)
            val currentVersion = runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            }.getOrElse { 1 }
            if (lastVersion >= currentVersion) return

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
                prefs.edit().putInt("transport_db_cleaned_version", currentVersion).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fixTransportRuntimeDbIfNeeded failed", e)
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
