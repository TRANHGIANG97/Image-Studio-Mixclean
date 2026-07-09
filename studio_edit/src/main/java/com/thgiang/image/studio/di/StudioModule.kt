package com.thgiang.image.studio.di

import com.thgiang.image.core.domain.logging.AppLogger
import com.thgiang.image.studio.logging.LogcatAppLogger
import com.thgiang.image.studio.util.FontDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StudioModule {

    /**
     * Swap [LogcatAppLogger] for a CrashlyticsAppLogger here once Firebase lands —
     * no call site changes needed.
     */
    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger =
        LogcatAppLogger().also { logger ->
            // FontDownloader is a legacy `object` and cannot be constructor-injected;
            // bridge the singleton logger so its font_fallback events reach the sink.
            FontDownloader.appLogger = logger
        }
}
