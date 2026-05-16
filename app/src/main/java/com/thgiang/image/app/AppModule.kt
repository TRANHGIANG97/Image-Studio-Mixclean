package com.thgiang.image.app

import android.content.Context
import android.os.Build
import com.thgiang.image.core.background.BackgroundGenerator
import com.thgiang.image.core.background.DefaultBackgroundGenerator
import com.thgiang.image.core.data.backgroundremove.MlKitBackgroundRemoverRepository
import com.thgiang.image.core.data.gallery.GalleryRepository
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.data.settings.DatastoreUserPreferencesRepository
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.domain.settings.UserPreferencesRepository
import com.abizer_r.quickedit.backgroundremove.ModNetBackgroundRemoverRepository
import com.thgiang.image.feature.premium.data.BillingManager
import com.thgiang.image.feature.premium.domain.PremiumRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBackgroundRemoverRepository(
        @ApplicationContext context: Context
    ): BackgroundRemoverRepository {
        return MlKitBackgroundRemoverRepository(
            context = context,
            allowSelfieFallback = shouldAllowSelfieFallbackForCurrentAbi()
        )
    }

    @Provides
    @Singleton
    fun provideHairDetailRemoverRepository(
        @ApplicationContext context: Context
    ): ModNetBackgroundRemoverRepository {
        return ModNetBackgroundRemoverRepository(context)
    }

    @Provides
    @Singleton
    fun provideImageSaveRepository(
        @ApplicationContext context: Context
    ): ImageSaveRepository {
        return ImageSaveRepository(context)
    }

    @Provides
    @Singleton
    fun provideBackgroundGenerator(): BackgroundGenerator {
        return DefaultBackgroundGenerator()
    }

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        @ApplicationContext context: Context
    ): UserPreferencesRepository {
        return DatastoreUserPreferencesRepository(context)
    }

    @Provides
    @Singleton
    fun provideGalleryRepository(
        @ApplicationContext context: Context
    ): GalleryRepository {
        return GalleryRepository(context)
    }

    @Provides
    @Singleton
    fun providePremiumRepository(
        billingManager: BillingManager
    ): PremiumRepository {
        return billingManager
    }


    @Provides
    @Singleton
    fun provideDraftManager(
        @ApplicationContext context: Context
    ): com.thgiang.image.feature.editor.model.DraftManager {
        return com.thgiang.image.feature.editor.model.DraftManager(context)
    }

    private fun shouldAllowSelfieFallbackForCurrentAbi(): Boolean {
        return Build.SUPPORTED_ABIS.any { abi ->
            abi.contains("x86", ignoreCase = true)
        }
    }
}
