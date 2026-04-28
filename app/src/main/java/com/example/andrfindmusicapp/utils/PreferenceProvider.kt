package com.example.andrfindmusicapp.utils

import android.content.Context
import android.content.SharedPreferences

// Класс для работы с настройками (SharedPreferences) приложения
class PreferenceProvider(context: Context) {
    private val appContext = context.applicationContext
    private val preference: SharedPreferences = appContext.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

    // Метод для сохранения последней выбранной категории
    fun saveCategory(category: String) {
        preference.edit().putString(KEY_LAST_CATEGORY, category).apply()
    }

    // Метод для получения последней выбранной категории
    fun getLastCategory(): String {
        return preference.getString(KEY_LAST_CATEGORY, DEFAULT_CATEGORY) ?: DEFAULT_CATEGORY
    }

    companion object {
        private const val KEY_LAST_CATEGORY = "last_category"
        private const val DEFAULT_CATEGORY = "pop" // По умолчанию грузим поп-музыку
    }
}