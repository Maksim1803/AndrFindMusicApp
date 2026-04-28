package com.example.andrfindmusicapp.di

import android.content.Context
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
}
