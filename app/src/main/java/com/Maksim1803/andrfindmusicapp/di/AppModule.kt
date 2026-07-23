package com.Maksim1803.andrfindmusicapp.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.annotation.SuppressLint
import com.Maksim1803.andrfindmusicapp.utils.PreferenceProvider
import com.Maksim1803.andrfindmusicapp.utils.UnsafeOkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Класс для предоставления общих зависимостей приложения через Hilt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Метод для предоставления провайдера настроек
    @Provides
    @Singleton
    fun providePreferenceProvider(@ApplicationContext context: Context): PreferenceProvider {
        return PreferenceProvider(context)
    }

    // Метод для предоставления атрибутов аудио
    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    // Метод для предоставления экземпляра ExoPlayer
    @SuppressLint("UnsafeOptInUsageError")
    @Provides
    @Singleton
    fun providePlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): Player {
        // Используем небезопасный OkHttp в качестве источника данных для плеера
        val unsafeOkHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
        val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(unsafeOkHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        
        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
}
