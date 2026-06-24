package com.thgiang.image.studio.di
import com.thgiang.image.studio.ui.editor.*

import com.thgiang.image.studio.ui.editor.model.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object EditorModule {
    
    /**
     * History manager - scoped to ViewModel lifecycle
     */
    @Provides
    @ViewModelScoped
    fun provideHistoryManager(): EditorHistoryManager = EditorHistoryManager(maxHistory = 30)
    
    /**
     * Bitmap pool - scoped to ViewModel to match renderer lifecycle
     * Note: Nếu muốn share pool giữa nhiều editor instances, chuyển sang SingletonComponent
     */
    @Provides
    @ViewModelScoped
    fun provideBitmapPool(): EditorBitmapPool = EditorBitmapPool(
        maxMemoryPercent = 0.15f,
        maxBucketSize = 6
    )
    
    /**
     * Render engine - depends on pool and context
     */
    @Provides
    @ViewModelScoped
    fun provideEditorRenderer(
        @ApplicationContext context: android.content.Context,
        bitmapPool: EditorBitmapPool
    ): EditorRenderer = EditorRenderer(context, bitmapPool)
}
