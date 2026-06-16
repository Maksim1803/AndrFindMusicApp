package com.Maksim1803.andrfindmusicapp.data.remote

import com.Maksim1803.andrfindmusicapp.data.model.JamendoResponse
import retrofit2.http.GET
import retrofit2.http.Query

// Интерфейс для работы с API Jamendo
interface JamendoService {
    // Метод для поиска треков по текстовому запросу
    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("search") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): JamendoResponse

    // Метод для получения списка треков по категории (тэгу)
    @GET("v3.0/tracks/")
    suspend fun getTracksByCategory(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("tags") tag: String,
        @Query("order") order: String = "popularity_week",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): JamendoResponse
}