package com.example.andrfindmusicapp.di

import android.content.Context
import com.example.andrfindmusicapp.utils.PreferenceProvider
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
    fun providePreferenceProvider(@ApplicationContext context: Context): PreferenceProvider {
        return PreferenceProvider(context)
    }
}
