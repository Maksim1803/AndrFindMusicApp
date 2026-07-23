package com.Maksim1803.andrfindmusicapp.di

import android.content.Context
import com.Maksim1803.andrfindmusicapp.data.local.AppDatabase
import com.Maksim1803.andrfindmusicapp.data.local.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Класс для предоставления зависимостей базы данных через Hilt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Метод для предоставления экземпляра базы данных
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    // Метод для предоставления DAO треков
    @Provides
    fun provideTrackDao(database: AppDatabase): TrackDao {
        return database.trackDao()
    }
}
