package com.example.andrfindmusicapp.ui.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.example.andrfindmusicapp.data.local.TrackDao
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Класс для управления состоянием плеера и текущим выбранным треком
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val trackDao: TrackDao
) : ViewModel() {
    val selectedTrack = MutableLiveData<Track?>()
    
    // Получаем список избранных треков напрямую из БД в виде LiveData
    val favoriteTracks: LiveData<List<Track>> = trackDao.getAllFavorites().map { entities ->
        entities.map { it.toTrack() }
    }.asLiveData()

    // Метод для выбора трека и обновления LiveData
    fun selectTrack(track: Track) {
        selectedTrack.value = track
    }

    // Вспомогательная функция расширения (или можно добавить в TrackEntity)
    private fun TrackEntity.toTrack() = Track(
        id = id,
        name = name,
        duration = duration ?: 0,
        artistName = artistName ?: "",
        albumName = albumName ?: "",
        imageUrl = imageUrl ?: "",
        audioUrl = audioUrl ?: ""
    )
}
