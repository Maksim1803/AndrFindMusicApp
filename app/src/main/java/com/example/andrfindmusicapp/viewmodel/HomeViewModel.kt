package com.example.andrfindmusicapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.data.remote.RetrofitClient
import com.example.andrfindmusicapp.utils.PreferenceProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Класс для управления данными главного экрана и выполнения поисковых запросов
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferenceProvider: PreferenceProvider,
    private val trackDao: com.example.andrfindmusicapp.data.local.TrackDao
) : ViewModel() {

    private val _tracks = MutableLiveData<List<Track>>()
    val tracks: LiveData<List<Track>> get() = _tracks

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    // Список для локальной фильтрации (как в FindAFilm)
    private var allTracksCached = listOf<Track>()
    
    private var currentOffset = 0
    private val limit = 20
    private var isPaginationLoading = false
    private var isLastPage = false
    private var currentQuery: String? = null
    private var currentTag: String? = null
    private var searchJob: Job? = null

    init {
        loadLastCategoryTracks()
    }

    // Метод для загрузки треков последней выбранной категории
    fun loadLastCategoryTracks() {
        val category = preferenceProvider.getLastCategory()
        resetPagination()
        currentTag = category
        currentQuery = null
        fetchTracksByTag(category)
    }

    // Метод для запроса треков по тегу из API
    private fun fetchTracksByTag(tag: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.jamendoService.getTracksByCategory(tag = tag, offset = currentOffset)
                val domainTracks = response.results ?: emptyList()
                
                allTracksCached = domainTracks
                _tracks.value = domainTracks
                
                updatePaginationState(domainTracks.size)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Метод поиска, из FindAFilm
    fun searchTracks(query: String) {
        searchJob?.cancel()
        
        if (query.isEmpty()) {
            _tracks.value = allTracksCached
            return
        }

        // 1. Сначала делаем мгновенную локальную фильтрацию
        val lowerQuery = query.lowercase(Locale.getDefault())
        val localResult = allTracksCached.filter {
            val nameMatch = it.name?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true
            val artistMatch = it.artistName?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true
            nameMatch || artistMatch
        }
        
        // Сразу обновляем UI локальными данными
        _tracks.value = localResult

        // 2. Затем пробуем найти новые треки в сети с небольшой задержкой
        searchJob = viewModelScope.launch {
            delay(700)
            currentQuery = query
            currentTag = null
            try {
                val response = RetrofitClient.jamendoService.searchTracks(query = query)
                val networkResults = response.results ?: emptyList()
                
                if (networkResults.isNotEmpty()) {
                    // Объединяем результаты, убирая дубликаты по ID
                    val combined = (localResult + networkResults).distinctBy { it.id }
                    _tracks.value = combined
                }
            } catch (e: Exception) {
                // Если сеть упала, у нас все еще есть локальные результаты
            }
        }
    }

    // Метод для сброса состояния пагинации
    private fun resetPagination() {
        currentOffset = 0
        isLastPage = false
        isPaginationLoading = false
    }

    // Метод для обновления состояния пагинации
    private fun updatePaginationState(lastResultSize: Int) {
        if (lastResultSize < limit) {
            isLastPage = true
        }
    }

    // Метод для обработки пагинации при скролле
    fun doPagination(visibleItemCount: Int, totalItemCount: Int, pastVisibleItemCount: Int) {
        if (isPaginationLoading || isLastPage || currentQuery != null) return
        if ((visibleItemCount + pastVisibleItemCount) >= totalItemCount - 5) {
            loadNextPage()
        }
    }

    // Метод для загрузки следующей страницы результатов
    private fun loadNextPage() {
        isPaginationLoading = true
        currentOffset += limit
        viewModelScope.launch {
            try {
                val response = RetrofitClient.jamendoService.getTracksByCategory(tag = currentTag ?: "", offset = currentOffset)
                val domainTracks = response.results ?: emptyList()
                val currentList = _tracks.value ?: emptyList()
                _tracks.value = currentList + domainTracks
                updatePaginationState(domainTracks.size)
            } catch (e: Exception) {
                currentOffset -= limit
            } finally {
                isPaginationLoading = false
            }
        }
    }
    
    // Метод для преобразования сущности БД в доменную модель
    private fun mapToDomain(entity: TrackEntity) = Track(
        id = entity.id, name = entity.name, artistName = entity.artistName,
        albumName = entity.albumName, imageUrl = entity.imageUrl,
        audioUrl = entity.audioUrl, duration = entity.duration
    )
}
