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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

    private val _networkError = MutableLiveData<Int?>()
    val networkError: LiveData<Int?> get() = _networkError

    // Список для локальной фильтрации и хранения всех загруженных треков текущей категории
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
        
        // Сначала пробуем загрузить из кэша БД
        loadFromCache(category)
        // Затем обновляем из сети
        fetchTracksByTag(category)
    }

    // Метод для загрузки данных из локальной БД
    private fun loadFromCache(category: String) {
        viewModelScope.launch {
            trackDao.getTracksByCategory(category).collect { entities ->
                if (entities.isNotEmpty() && _tracks.value.isNullOrEmpty()) {
                    val domainTracks = entities.map { mapToDomain(it) }
                    allTracksCached = domainTracks
                    _tracks.value = domainTracks
                }
            }
        }
    }

    // Метод для первичного получения треков по тэгу (категории)
    private fun fetchTracksByTag(tag: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _networkError.value = null
            try {
                val response = withTimeout(10000) {
                    RetrofitClient.jamendoService.getTracksByCategory(tag = tag, offset = currentOffset)
                }
                val domainTracks = response.results ?: emptyList()
                
                if (currentOffset == 0) {
                    trackDao.clearCacheByCategory(tag)
                    allTracksCached = domainTracks
                    _tracks.value = domainTracks
                } else {
                    val currentList = _tracks.value ?: emptyList()
                    val newList = currentList + domainTracks
                    allTracksCached = newList
                    _tracks.value = newList
                }
                
                // Сохраняем в локальный кэш Room
                trackDao.upsertCacheTracks(domainTracks.map { mapToEntity(it, tag) })
                updatePaginationState(domainTracks.size)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _networkError.value = com.example.andrfindmusicapp.R.string.error_slow_connection
            } catch (e: Exception) {
                _networkError.value = com.example.andrfindmusicapp.R.string.error_no_internet
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Вспомогательный метод для маппинга в сущность БД
    private fun mapToEntity(track: Track, category: String): TrackEntity {
        return TrackEntity(
            id = track.id,
            name = track.name ?: "",
            artistName = track.artistName ?: "",
            albumName = track.albumName ?: "",
            imageUrl = track.imageUrl ?: "",
            audioUrl = track.audioUrl ?: "",
            duration = track.duration ?: 0,
            category = category,
            isFavorite = false
        )
    }

    // Вспомогательный метод для маппинга из сущности БД
    private fun mapToDomain(entity: TrackEntity): Track {
        return Track(
            id = entity.id,
            name = entity.name,
            artistName = entity.artistName,
            albumName = entity.albumName,
            imageUrl = entity.imageUrl,
            audioUrl = entity.audioUrl,
            duration = entity.duration
        )
    }

    // Метод для обработки события прокрутки списка и запуска пагинации
    fun doPagination(visibleItemCount: Int, totalItemCount: Int, pastVisibleItemCount: Int) {
        if (isPaginationLoading || isLastPage || _isLoading.value == true) return

        // Если осталось проскроллить меньше 5 элементов — грузим следующую страницу
        if ((visibleItemCount + pastVisibleItemCount) >= totalItemCount - 5) {
            loadNextPage()
        }
    }

    // Метод для загрузки следующей страницы данных (пагинация)
    private fun loadNextPage() {
        isPaginationLoading = true
        currentOffset += limit
        
        viewModelScope.launch {
            try {
                val response = withTimeout(10000) {
                    if (currentQuery != null) {
                        RetrofitClient.jamendoService.searchTracks(query = currentQuery!!, offset = currentOffset)
                    } else {
                        RetrofitClient.jamendoService.getTracksByCategory(tag = currentTag!!, offset = currentOffset)
                    }
                }
                
                val domainTracks = response.results ?: emptyList()
                
                if (domainTracks.isNotEmpty()) {
                    val currentList = _tracks.value ?: emptyList()
                    val newList = currentList + domainTracks
                    
                    // Важно: обновляем и основной кэш для корректного поиска
                    allTracksCached = newList
                    _tracks.value = newList
                    
                    // Сохраняем подгруженное в БД для офлайн режима
                    if (currentTag != null) {
                        trackDao.upsertCacheTracks(domainTracks.map { mapToEntity(it, currentTag!!) })
                    }
                }
                
                updatePaginationState(domainTracks.size)
            } catch (e: Exception) {
                // В случае ошибки откатываем оффсет назад, чтобы попробовать снова
                currentOffset -= limit
                _networkError.value = com.example.andrfindmusicapp.R.string.error_slow_connection
            } finally {
                isPaginationLoading = false
            }
        }
    }

    // Метод для поиска треков (локальный + сетевой)
    fun searchTracks(query: String) {
        searchJob?.cancel()
        _networkError.value = null
        
        if (query.isEmpty()) {
            _tracks.value = allTracksCached
            return
        }

        // 1. Сначала делаем мгновенную локальную фильтрацию по уже загруженным данным
        val lowerQuery = query.lowercase(Locale.getDefault())
        val localResult = allTracksCached.filter {
            val nameMatch = it.name?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true
            val artistMatch = it.artistName?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true
            nameMatch || artistMatch
        }
        
        _tracks.value = localResult

        // 2. Затем пробуем найти новые треки в сети с задержкой (debounce)
        searchJob = viewModelScope.launch {
            delay(700)
            resetPagination()
            currentQuery = query
            currentTag = null
            _isLoading.value = true
            try {
                val response = withTimeout(10000) {
                    RetrofitClient.jamendoService.searchTracks(query = query, offset = currentOffset)
                }
                val networkResults = response.results ?: emptyList()
                
                if (networkResults.isNotEmpty()) {
                    // Объединяем локальные и сетевые результаты, убирая дубликаты
                    val combined = (localResult + networkResults).distinctBy { it.id }
                    _tracks.value = combined
                    updatePaginationState(networkResults.size)
                    _networkError.value = null
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _networkError.value = com.example.andrfindmusicapp.R.string.error_slow_connection
            } catch (e: Exception) {
                _networkError.value = com.example.andrfindmusicapp.R.string.error_no_internet
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Сброс параметров пагинации при новой категории или поиске
    private fun resetPagination() {
        currentOffset = 0
        isLastPage = false
        isPaginationLoading = false
    }

    // Обновление состояния: если пришло меньше треков, чем лимит — значит это последняя страница
    private fun updatePaginationState(lastResultSize: Int) {
        if (lastResultSize < limit) {
            isLastPage = true
        }
    }
}
