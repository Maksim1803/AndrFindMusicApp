package com.example.andrfindmusicapp.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.andrfindmusicapp.data.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Класс для работы с настройками (SharedPreferences) приложения
class PreferenceProvider(context: Context) {
    private val appContext = context.applicationContext
    private val preference: SharedPreferences = appContext.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Метод для сохранения последней выбранной категории
    fun saveCategory(category: String) {
        preference.edit().putString(KEY_LAST_CATEGORY, category).apply()
    }

    // Метод для получения последней выбранной категории
    fun getLastCategory(): String {
        return preference.getString(KEY_LAST_CATEGORY, DEFAULT_CATEGORY) ?: DEFAULT_CATEGORY
    }

    // Метод для сохранения последнего трека и плейлиста
    fun saveLastState(track: Track?, playlist: List<Track>) {
        val editor = preference.edit()
        editor.putString(KEY_LAST_TRACK, gson.toJson(track))
        editor.putString(KEY_LAST_PLAYLIST, gson.toJson(playlist))
        editor.apply()
    }

    // Метод для получения последнего трека
    fun getLastTrack(): Track? {
        val json = preference.getString(KEY_LAST_TRACK, null) ?: return null
        return try {
            gson.fromJson(json, Track::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Метод для получения последнего плейлиста
    fun getLastPlaylist(): List<Track> {
        val json = preference.getString(KEY_LAST_PLAYLIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Track>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Метод для сохранения списка ID напоминаний
    fun saveReminders(reminderIds: Set<String>) {
        preference.edit().putString(KEY_REMINDERS, gson.toJson(reminderIds)).apply()
    }

    // Метод для получения списка ID напоминаний
    fun getReminders(): Set<String> {
        val json = preference.getString(KEY_REMINDERS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    // Метод для сохранения времени напоминаний
    fun saveReminderTimes(times: Map<String, Long>) {
        preference.edit().putString(KEY_REMINDER_TIMES, gson.toJson(times)).apply()
    }

    // Метод для получения времени напоминаний
    fun getReminderTimes(): Map<String, Long> {
        val json = preference.getString(KEY_REMINDER_TIMES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Метод для сохранения темы приложения
    fun saveIsDarkMode(isDark: Boolean) {
        preference.edit().putBoolean(KEY_IS_DARK_MODE, isDark).apply()
    }

    // Метод для получения состояния темы
    fun isDarkMode(): Boolean {
        return preference.getBoolean(KEY_IS_DARK_MODE, false)
    }

    // Метод для сохранения языка приложения
    fun saveLanguage(lang: String) {
        preference.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    // Метод для получения сохраненного языка
    fun getLanguage(): String? {
        return preference.getString(KEY_LANGUAGE, null)
    }

    // Метод для получения состояния первого запуска
    fun isFirstLaunch(): Boolean {
        return preference.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    // Метод для установки флага первого запуска в false
    fun setFirstLaunchCompleted() {
        preference.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    // Метод для получения количества треков при прошлом сканировании
    fun getLastTrackCount(): Int {
        return preference.getInt(KEY_LAST_TRACK_COUNT, 0)
    }

    // Метод для сохранения текущего количества треков
    fun saveLastTrackCount(count: Int) {
        preference.edit().putInt(KEY_LAST_TRACK_COUNT, count).apply()
    }

    companion object {
        private const val KEY_LAST_CATEGORY = "last_category"
        private const val DEFAULT_CATEGORY = "pop"
        private const val KEY_LAST_TRACK = "last_track"
        private const val KEY_LAST_PLAYLIST = "last_playlist"
        private const val KEY_REMINDERS = "reminder_ids"
        private const val KEY_REMINDER_TIMES = "reminder_times"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_LAST_TRACK_COUNT = "last_track_count"
    }
}
