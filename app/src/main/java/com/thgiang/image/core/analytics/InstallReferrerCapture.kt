package com.thgiang.image.core.analytics

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

object InstallReferrerCapture {

    fun captureAsync(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(
            com.thgiang.image.core.ad.EngagementPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        if (prefs.getBoolean("install_referrer_captured", false)) return

        val client = InstallReferrerClient.newBuilder(appContext).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        runCatching {
                            val referrer = client.installReferrer.installReferrer
                            AppAnalytics.setInstallAttribution(appContext, referrer)
                            prefs.edit().putBoolean("install_referrer_captured", true).apply()
                        }
                    }
                }
                client.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() = Unit
        })
    }
}
