package com.example.andrfindmusicapp.data.remote

import com.example.andrfindmusicapp.data.model.JamendoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface JamendoService {
    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("search") query: String,
        @Query("limit") limit: Int = 20
    ): JamendoResponse

    @GET("v3.0/tracks/")
    suspend fun getTracksByCategory(
        @Query("client_id") clientId: String = "59cb9dad",
        @Query("format") format: String = "json",
        @Query("tags") tag: String,
        @Query("order") order: String = "popularity_week",
        @Query("limit") limit: Int = 20
    ): JamendoResponse
}