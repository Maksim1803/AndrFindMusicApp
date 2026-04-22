package com.example.andrfindmusicapp.ui.player

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.andrfindmusicapp.data.model.Track

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {
    val selectedTrack = MutableLiveData<Track?>()
    
    fun selectTrack(track: Track) {
        selectedTrack.value = track
    }
}
