package com.example.andrfindmusicapp.data.remote

import javax.inject.Inject
import javax.inject.Singleton

// Класс для получения текста песен из удаленного репозитория
@Singleton
class LyricsRepository @Inject constructor(
    private val jamendoService: JamendoService
) {
    // Метод для получения текста песни по ID трека
    suspend fun getLyrics(trackId: String): String? {
        return try {
            val response = jamendoService.getTrackLyrics(trackId = trackId)
            response.results?.firstOrNull()?.lyrics
        } catch (e: Exception) {
            null
        }
    }
}
