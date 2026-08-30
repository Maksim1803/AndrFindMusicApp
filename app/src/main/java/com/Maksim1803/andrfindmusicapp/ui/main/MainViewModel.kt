package com.Maksim1803.andrfindmusicapp.ui.main

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.Maksim1803.andrfindmusicapp.data.local.TrackDao
import com.Maksim1803.andrfindmusicapp.data.model.Track
import com.Maksim1803.andrfindmusicapp.service.PlaybackService
import com.Maksim1803.andrfindmusicapp.service.SleepTimerManager
import com.Maksim1803.andrfindmusicapp.utils.PreferenceProvider
import com.Maksim1803.andrfindmusicapp.utils.TrackDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Класс для управления общим состоянием приложения, плеером и данными
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    val sleepTimerManager: SleepTimerManager,
    private val downloadManager: TrackDownloadManager,
    private val preferenceProvider: PreferenceProvider
) : ViewModel() {

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist = _playlist.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()

    private val _reminderIds = MutableStateFlow<Set<String>>(emptySet())
    val reminderIds = _reminderIds.asStateFlow()

    // Храним время напоминания для каждого трека
    private val _reminderTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val reminderTimes = _reminderTimes.asStateFlow()

    private val _metadataOverrides = MutableStateFlow<Map<String, Track>>(emptyMap())
    val metadataOverrides = _metadataOverrides.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _errorEvents = MutableSharedFlow<Int>()
    val errorEvents = _errorEvents.asSharedFlow()

    private var bufferingJob: kotlinx.coroutines.Job? = null

    private var controller: MediaController? = null
    private val _isControllerReady = MutableStateFlow(false)

    // Метод для проверки наличия интернета
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Метод для уведомления об отсутствии сети, если трек онлайн
    fun checkNetworkAndNotifyIfOnline() {
        val isOnlineTrack = _currentTrack.value?.audioUrl?.startsWith("http") == true
        if (isOnlineTrack && !isNetworkAvailable()) {
            viewModelScope.launch {
                _errorEvents.emit(com.Maksim1803.andrfindmusicapp.R.string.error_no_internet)
            }
        }
    }

    // Метод для уведомления об отсутствии сети при действии (например, смена категории)
    fun notifyNoInternet() {
        if (!isNetworkAvailable()) {
            viewModelScope.launch {
                _errorEvents.emit(com.Maksim1803.andrfindmusicapp.R.string.error_no_internet)
            }
        }
    }

    init {
        // Загружаем сохраненные напоминания и времена
        val savedReminders = preferenceProvider.getReminders()
        val savedTimes = preferenceProvider.getReminderTimes().toMutableMap()
        
        // Миграция: если есть старое напоминание без времени, ставим ему 10:00
        var needsUpdate = false
        savedReminders.forEach { id ->
            if (!savedTimes.containsKey(id)) {
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 10)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    // Если 10:00 сегодня уже прошло, ставим на завтра
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
                savedTimes[id] = cal.timeInMillis
                needsUpdate = true
            }
        }
        
        if (needsUpdate) {
            preferenceProvider.saveReminderTimes(savedTimes)
        }

        _reminderIds.value = savedReminders
        _reminderTimes.value = savedTimes
        _metadataOverrides.value = preferenceProvider.getMetadataOverrides()

        viewModelScope.launch {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controller = controllerFuture.await()
            
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isBuffering = playbackState == Player.STATE_BUFFERING
                    _isBuffering.value = isBuffering
                    
                    // Если трек начал играть, отменяем все таймеры ошибок
                    if (playbackState == Player.STATE_READY) {
                        bufferingJob?.cancel()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    viewModelScope.launch {
                        _errorEvents.emit(com.Maksim1803.andrfindmusicapp.R.string.playback_error)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    viewModelScope.launch {
                        mediaItem?.mediaId?.let { id ->
                            val track = _playlist.value.find { it.id == id }
                            _currentTrack.value = track
                            preferenceProvider.saveLastState(track, _playlist.value)
                        }
                    }
                }
            })

            // Восстанавливаем последнее состояние если плеер пуст
            val lastPlaylist = preferenceProvider.getLastPlaylist()
            if (lastPlaylist.isNotEmpty() && _playlist.value.isEmpty()) {
                _playlist.value = lastPlaylist
                val mediaItems = lastPlaylist.map { createMediaItem(it) }
                controller?.setMediaItems(mediaItems)
                
                preferenceProvider.getLastTrack()?.let { lastTrack ->
                    val index = lastPlaylist.indexOfFirst { it.id == lastTrack.id }
                    if (index != -1) {
                        controller?.seekTo(index, 0)
                        _currentTrack.value = lastTrack
                    }
                }
                controller?.prepare()
            }

            _isControllerReady.value = true

            // Подгружаем избранное
            trackDao.getAllFavorites()
                .map { entities -> entities.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collectLatest { ids ->
                    _favoriteIds.value = ids
                }
        }
    }

    // Метод для создания MediaItem из объекта Track
    private fun createMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.audioUrl ?: "")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.name ?: "Unknown")
                    .setArtist(track.artistName ?: "Unknown Artist")
                    .setArtworkUri(if (!track.imageUrl.isNullOrEmpty()) android.net.Uri.parse(track.imageUrl) else null)
                    .build()
            )
            .build()
    }

    // Метод для запуска воспроизведения трека в контексте плейлиста
    fun playTrackWithPlaylist(track: Track, newPlaylist: List<Track>) {
        // Сразу обновляем текущий трек для мгновенного отклика UI
        _currentTrack.value = track

        // Запускаем независимый таймер на 5 секунд прямо сейчас
        bufferingJob?.cancel()
        bufferingJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (_isBuffering.value || !isPlaying.value) {
                val isOnline = _currentTrack.value?.audioUrl?.startsWith("http") == true
                if (isOnline) {
                    val errorRes = if (isNetworkAvailable()) {
                        com.Maksim1803.andrfindmusicapp.R.string.error_slow_connection
                    } else {
                        com.Maksim1803.andrfindmusicapp.R.string.error_no_internet
                    }
                    _errorEvents.emit(errorRes)
                }
            }
        }

        // Проверка сети (мгновенная)
        val isOnline = track.audioUrl?.startsWith("http") == true
        if (isOnline && !isNetworkAvailable()) {
            bufferingJob?.cancel()
            viewModelScope.launch {
                _errorEvents.emit(com.Maksim1803.andrfindmusicapp.R.string.error_no_internet)
            }
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // Ждем готовности контроллера
            _isControllerReady.first { it }
            
            val sortedPlaylist = newPlaylist.sortedWith(compareByDescending<Track> { 
                _favoriteIds.value.contains(it.id) 
            }.thenByDescending { 
                downloadManager.getDownloadedUri(it) != null 
            })

            val initialTracks = if (sortedPlaylist.isEmpty()) listOf(track) else sortedPlaylist
            
            // Преобразуем треки: если трек скачан, используем локальный URI
            val tracksToPlay = initialTracks.map { t ->
                val localUri = downloadManager.getDownloadedUri(t)
                if (localUri != null) t.copy(audioUrl = localUri.toString()) else t
            }
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _playlist.value = tracksToPlay
                controller?.setMediaItems(tracksToPlay.map { createMediaItem(it) })

                // Ищем индекс по ID, так как audioUrl мог измениться
                val index = tracksToPlay.indexOfFirst { it.id == track.id }
                if (index != -1) {
                    controller?.seekTo(index, 0)
                    _currentTrack.value = tracksToPlay[index]
                }

                controller?.prepare()
                controller?.play()
                controller?.repeatMode = Player.REPEAT_MODE_ALL
                preferenceProvider.saveLastState(track, tracksToPlay)
            }
        }
    }

    // Метод для переключения статуса "избранное" для трека
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isCurrentlyFav = trackDao.isFavorite(track.id)
            if (isCurrentlyFav) {
                trackDao.updateFavoriteStatus(track.id, false)
                removeFromPlaylist(track)
            } else {
                val entity = com.Maksim1803.andrfindmusicapp.data.local.TrackEntity(
                    id = track.id,
                    name = track.name ?: "Unknown",
                    duration = track.duration ?: 0,
                    artistName = track.artistName ?: "Unknown Artist",
                    albumName = track.albumName ?: "Unknown Album",
                    imageUrl = track.imageUrl ?: "",
                    audioUrl = track.audioUrl ?: "",
                    isFavorite = true,
                    category = track.folderName ?: ""
                )
                trackDao.insertIgnore(entity)
                trackDao.updateFavoriteStatus(track.id, true)
                addToCurrentPlaylist(track)
            }
        }
    }

    // Метод для добавления трека в текущий плейлист
    private fun addToCurrentPlaylist(track: Track) {
        val currentList = _playlist.value.toMutableList()
        if (!currentList.any { it.id == track.id }) {
            currentList.add(track)
            _playlist.value = currentList
            controller?.addMediaItem(createMediaItem(track))
            preferenceProvider.saveLastState(_currentTrack.value, _playlist.value)
        }
    }

    // Метод для удаления трека из текущего плейлиста
    fun removeFromPlaylist(track: Track) {
        val currentList = _playlist.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == track.id }
        if (index != -1) {
            currentList.removeAt(index)
            _playlist.value = currentList
            controller?.removeMediaItem(index)
            preferenceProvider.saveLastState(_currentTrack.value, _playlist.value)
        }
    }

    // Метод для переключения воспроизведение/пауза
    fun togglePlayPause() {
        if (controller?.isPlaying == true) controller?.pause() else controller?.play()
    }

    // Методы для управления воспроизведением
    fun skipToNext() = controller?.seekToNextMediaItem()
    fun skipToPrevious() = controller?.seekToPreviousMediaItem()
    fun seekTo(position: Long) = controller?.seekTo(position)

    // Метод для запуска таймера сна
    fun startSleepTimer(minutes: Int) {
        sleepTimerManager.startTimer(minutes) { controller?.pause() }
    }

    // Метод для остановки таймера сна
    fun stopSleepTimer() = sleepTimerManager.stopTimer()

    // Метод для скачивания трека
    fun downloadTrack(track: Track): Boolean {
        val isLocal = track.audioUrl?.startsWith("content://") == true || 
                     track.audioUrl?.startsWith("file://") == true ||
                     downloadManager.getDownloadedUri(track) != null
        if (isLocal) return false
        downloadManager.downloadTrack(track)
        addToCurrentPlaylist(track)
        return true
    }

    // Метод для установки напоминания для трека
    fun setReminder(track: Track, timeMillis: Long) {
        val currentIds = _reminderIds.value.toMutableSet()
        currentIds.add(track.id)
        _reminderIds.value = currentIds
        preferenceProvider.saveReminders(currentIds)

        val currentTimes = _reminderTimes.value.toMutableMap()
        currentTimes[track.id] = timeMillis
        _reminderTimes.value = currentTimes
        preferenceProvider.saveReminderTimes(currentTimes)
    }

    // Метод для удаления напоминания для трека
    fun removeReminder(trackId: String) {
        val currentIds = _reminderIds.value.toMutableSet()
        currentIds.remove(trackId)
        _reminderIds.value = currentIds
        preferenceProvider.saveReminders(currentIds)

        val currentTimes = _reminderTimes.value.toMutableMap()
        currentTimes.remove(trackId)
        _reminderTimes.value = currentTimes
        preferenceProvider.saveReminderTimes(currentTimes)
    }

    // Метод для обновления кастомных метаданных извне (если нужно)
    fun updateMetadataOverrides() {
        _metadataOverrides.value = preferenceProvider.getMetadataOverrides()
    }

    // Метод для получения количества локальных треков на устройстве
    fun getLocalTracksCount(): Int {
        val projection = arrayOf(android.provider.MediaStore.Audio.Media._ID)
        val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        return try {
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // Метод для удаления физического файла трека
    fun deleteTrack(track: Track) = downloadManager.deleteTrackFile(track)
    
    // Метод для получения контроллера плеера
    fun getController(): Player? = controller

    override fun onCleared() {
        super.onCleared()
        controller?.release()
    }
}
