package com.thgiang.image.core.data.backgroundremove

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object GooglePlayServicesHelper {
    private const val TAG = "GooglePlayServices"
    private const val PLAY_SERVICES_PACKAGE = "com.google.android.gms"

    fun isUpdateRecommended(context: Context): Boolean {
        val status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return status != ConnectionResult.SUCCESS
    }

    fun openUpdatePage(context: Context) {
        val marketUri = Uri.parse("market://details?id=$PLAY_SERVICES_PACKAGE")
        val webUri = Uri.parse(
            "https://play.google.com/store/apps/details?id=$PLAY_SERVICES_PACKAGE",
        )
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, marketUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Play Store app not found, opening web URL", e)
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    fun isPlayServicesRelatedFailure(error: Throwable?): Boolean {
        if (error == null) return false
        val message = generateSequence(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return message.contains("play services") ||
            message.contains("google play services") ||
            message.contains("module is not available") ||
            message.contains("moduleinstall") ||
            message.contains("remoteexception") ||
            message.contains("failed to segment") ||
            message.contains("failed to create mediapipe")
    }
}
