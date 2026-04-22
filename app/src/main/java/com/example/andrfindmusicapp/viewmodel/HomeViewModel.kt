package com.example.andrfindmusicapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.data.remote.RetrofitClient
import com.example.andrfindmusicapp.utils.PreferenceProvider
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferenceProvider: PreferenceProvider
) : ViewModel() {

    private val _tracks = MutableLiveData<List<Track>>()
    val tracks: LiveData<List<Track>> get() = _tracks

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    init {
        loadLastCategoryTracks()
    }

    fun loadLastCategoryTracks() {
        val category = preferenceProvider.getLastCategory()
        fetchTracksByTag(category)
    }

    fun searchTracks(query: String) {
        if (query.isBlank()) {
            loadLastCategoryTracks()
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.jamendoService.searchTracks(query = query)
                _tracks.value = response.results
            } catch (e: Exception) {
                // Обработка ошибки
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchTracksByTag(tag: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.jamendoService.getTracksByCategory(tag = tag)
                _tracks.value = response.results
            } catch (e: Exception) {
                // Обработка ошибки
            } finally {
                _isLoading.value = false
            }
        }
    }
}