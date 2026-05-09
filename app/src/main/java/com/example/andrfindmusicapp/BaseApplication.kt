package com.example.andrfindmusicapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Класс для инициализации Hilt и глобального контекста приложения
@HiltAndroidApp
class BaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.andrfindmusicapp.utils.NotificationHelper.createNotificationChannel(this)
    }
}
