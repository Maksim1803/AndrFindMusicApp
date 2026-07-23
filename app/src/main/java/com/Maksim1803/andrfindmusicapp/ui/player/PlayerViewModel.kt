package com.Maksim1803.andrfindmusicapp.ui.player

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.Maksim1803.andrfindmusicapp.data.model.Track

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Класс для управления состоянием плеера и текущим выбранным треком
@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {
    val selectedTrack = MutableLiveData<Track?>()
    
    // Метод для выбора трека и обновления LiveData
    fun selectTrack(track: Track) {
        selectedTrack.value = track
    }
}
