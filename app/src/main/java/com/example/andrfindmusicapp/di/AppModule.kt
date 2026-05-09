package com.example.andrfindmusicapp.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.andrfindmusicapp.utils.PreferenceProvider
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
    @Provides
    @Singleton
    fun providePlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): Player {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
