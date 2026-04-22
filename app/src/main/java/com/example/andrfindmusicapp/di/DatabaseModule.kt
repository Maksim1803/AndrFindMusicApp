package com.example.andrfindmusicapp.di

import android.content.Context
import com.example.andrfindmusicapp.data.local.AppDatabase
import com.example.andrfindmusicapp.data.local.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideTrackDao(database: AppDatabase): TrackDao {
        return database.trackDao()
    }
}
