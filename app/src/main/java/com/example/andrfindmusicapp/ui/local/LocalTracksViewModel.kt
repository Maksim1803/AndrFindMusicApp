package com.example.andrfindmusicapp.ui.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andrfindmusicapp.data.local.LocalTrackProvider
import com.example.andrfindmusicapp.data.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel для управления списком локальных треков
@HiltViewModel
class LocalTracksViewModel @Inject constructor(
    private val localTrackProvider: LocalTrackProvider
) : ViewModel() {

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks = _localTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Метод для загрузки локальных треков в фоновом потоке
    fun loadLocalTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val tracks = localTrackProvider.fetchLocalTracks()
                _localTracks.value = tracks
            } catch (e: Exception) {
                _localTracks.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Метод для удаления трека
    fun deleteTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = localTrackProvider.deleteTrack(track)
            if (success) {
                loadLocalTracks() // Перезагружаем список после удаления
            }
        }
    }
}
