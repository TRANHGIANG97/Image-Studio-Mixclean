package com.thgiang.image.core.ad

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdModule {

    @Provides @Singleton
    fun provideAdConfig(): AdConfig = AdConfig()

    @Provides @Singleton
    fun provideAdLogger(): AdLogger = AndroidAdLogger()

    @Provides @Singleton
    fun provideInterstitialAdManager(
        impl: InterstitialAdManagerImpl
    ): InterstitialAdManager {
        impl.initialize()
        return impl
    }

    @Provides @Singleton
    fun provideAppOpenAdManager(
        impl: AppOpenAdManagerImpl,
        application: Application
    ): AppOpenAdManager {
        impl.initialize(application)
        return impl
    }

    @Provides @Singleton
    fun provideRewardedAdManager(
        impl: RewardedAdManagerImpl
    ): RewardedAdManager = impl

    @Provides @Singleton
    fun provideNativeAdManager(
        impl: NativeAdManagerImpl
    ): NativeAdManager = impl
}
