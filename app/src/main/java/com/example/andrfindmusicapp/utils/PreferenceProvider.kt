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

    // Сохранение последнего трека и плейлиста
    fun saveLastState(track: Track?, playlist: List<Track>) {
        val editor = preference.edit()
        editor.putString(KEY_LAST_TRACK, gson.toJson(track))
        editor.putString(KEY_LAST_PLAYLIST, gson.toJson(playlist))
        editor.apply()
    }

    // Получение последнего трека
    fun getLastTrack(): Track? {
        val json = preference.getString(KEY_LAST_TRACK, null) ?: return null
        return try {
            gson.fromJson(json, Track::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Получение последнего плейлиста
    fun getLastPlaylist(): List<Track> {
        val json = preference.getString(KEY_LAST_PLAYLIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Track>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Проверка, нужно ли сменить рекомендованный трек (раз в сутки)
    fun shouldUpdateDailyTrack(): Boolean {
        val lastUpdate = preference.getLong(KEY_LAST_TRACK_UPDATE, 0)
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        return if (lastUpdate < today) {
            preference.edit().putLong(KEY_LAST_TRACK_UPDATE, System.currentTimeMillis()).apply()
            true
        } else {
            false
        }
    }

    // Сохранение/получение ежедневного трека
    fun saveDailyTrack(track: Track) {
        preference.edit().putString(KEY_DAILY_TRACK, gson.toJson(track)).apply()
    }

    fun getDailyTrack(): Track? {
        val json = preference.getString(KEY_DAILY_TRACK, null) ?: return null
        return try {
            gson.fromJson(json, Track::class.java)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_LAST_CATEGORY = "last_category"
        private const val DEFAULT_CATEGORY = "pop"
        private const val KEY_LAST_TRACK = "last_track"
        private const val KEY_LAST_PLAYLIST = "last_playlist"
        private const val KEY_LAST_TRACK_UPDATE = "last_track_update"
        private const val KEY_DAILY_TRACK = "daily_track"
    }
}
