package com.example.andrfindmusicapp.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// Класс для представления данных трека, получаемых из API
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

object DailyTrackProvider {
    var currentDailyTrack: Track? = null

    fun getBaseTrack(): Track = Track(
        id = "1148100",
        name = "5th Symphony",
        artistName = "Beethoven",
        albumName = "Classical Collection",
        duration = 420,
        audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1148100&format=mp31&from=app-59cb9dad",
        imageUrl = "https://usercontent.jamendo.com/v3/albums/covers/?id=141625"
    )
}
