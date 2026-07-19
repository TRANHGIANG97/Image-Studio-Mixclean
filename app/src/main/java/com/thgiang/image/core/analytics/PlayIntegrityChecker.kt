package com.thgiang.image.core.analytics

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.thgiang.image.BuildConfig
import kotlinx.coroutines.tasks.await
import java.util.UUID

object PlayIntegrityChecker {

    suspend fun checkAndLog(context: Context) {
        if (BuildConfig.DEBUG) return

        val passed = runCatching {
            val manager = IntegrityManagerFactory.create(context)
            val request = IntegrityTokenRequest.builder()
                .setNonce(UUID.randomUUID().toString())
                .build()
            val response = manager.requestIntegrityToken(request).await()
            response.token().isNotEmpty()
        }.getOrElse { false }

        AppAnalytics.setIntegrityResult(context, passed)
    }
}
