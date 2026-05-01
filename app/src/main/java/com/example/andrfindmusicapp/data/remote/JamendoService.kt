package com.example.andrfindmusicapp.data.remote

import com.example.andrfindmusicapp.data.model.JamendoResponse
import com.example.andrfindmusicapp.data.model.LyricsResponse
import retrofit2.http.GET
import retrofit2.http.Query

// Интерфейс для работы с API Jamendo
interface JamendoService {
    // Метод для поиска треков по текстовому запросу
    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("search") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): JamendoResponse

    // Метод для получения списка треков по категории (тэгу)
    @GET("v3.0/tracks/")
    suspend fun getTracksByCategory(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("tag") tag: String,
        @Query("order") order: String = "popularity_week",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): JamendoResponse

    // Метод для получения текста песни по ID трека
    @GET("v3.0/tracks/lyrics/")
    suspend fun getTrackLyrics(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("track_id") trackId: String
    ): LyricsResponse
}