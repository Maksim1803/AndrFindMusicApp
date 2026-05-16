package com.example.andrfindmusicapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.andrfindmusicapp.utils.UnsafeOkHttpClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Класс для инициализации Hilt и глобального контекста приложения
@HiltAndroidApp
class BaseApplication : Application(), ImageLoaderFactory {

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

    // Настройка Coil для использования нашего "небезопасного" клиента при загрузке картинок
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
