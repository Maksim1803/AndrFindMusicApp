package com.example.andrfindmusicapp.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// Класс для представления данных трека, получаемых из API
// Поля сделаны nullable, чтобы избежать ошибок парсинга при отсутствии данных от API
data class Track(
    val id: String,
    val name: String?,
    val duration: Int?,
    @SerializedName("artist_name") val artistName: String?,
    @SerializedName("album_name") val albumName: String?,
    @SerializedName("audio") val audioUrl: String?,
    @SerializedName("image") val imageUrl: String?
) : Serializable

// Класс для представления ответа от API Jamendo
data class JamendoResponse(
    val results: List<Track>?
)

// Модели для получения текста песни
data class LyricsResponse(
    val results: List<LyricsResult>?
)

data class LyricsResult(
    val id: String,
    val lyrics: String?
)
