package com.example.andrfindmusicapp.ui.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.andrfindmusicapp.data.local.TrackDao
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.data.remote.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// Класс для управления состоянием плеера и текущим выбранным треком
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {
    val selectedTrack = MutableLiveData<Track?>()

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading = _isLyricsLoading.asStateFlow()
    
    // Получаем список избранных треков напрямую из БД в виде LiveData
    val favoriteTracks: LiveData<List<Track>> = trackDao.getAllFavorites().map { entities ->
        entities.map { it.toTrack() }
    }.asLiveData()

    // Метод для выбора трека и обновления LiveData
    fun selectTrack(track: Track) {
        selectedTrack.value = track
    }

    fun loadLyrics(trackId: String) {
        viewModelScope.launch {
            _isLyricsLoading.value = true
            _lyrics.value = lyricsRepository.getLyrics(trackId)
            _isLyricsLoading.value = false
        }
    }

    fun clearLyrics() {
        _lyrics.value = null
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
