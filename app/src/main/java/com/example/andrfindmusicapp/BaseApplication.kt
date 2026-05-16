package com.example.andrfindmusicapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Класс для инициализации Hilt и глобального контекста приложения
@HiltAndroidApp
class BaseApplication : Application() {

    @Inject
    lateinit var preferenceProvider: com.example.andrfindmusicapp.utils.PreferenceProvider

    override fun onCreate() {
        super.onCreate()
        com.example.andrfindmusicapp.utils.NotificationHelper.createNotificationChannel(this)
        
        // Применяем сохраненную тему при старте
        val isDarkMode = preferenceProvider.isDarkMode()
        val mode = if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }
}
