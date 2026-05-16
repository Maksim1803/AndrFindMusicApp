package com.example.andrfindmusicapp.ui.main

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.andrfindmusicapp.data.local.TrackDao
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.service.PlaybackService
import com.example.andrfindmusicapp.service.SleepTimerManager
import com.example.andrfindmusicapp.utils.PreferenceProvider
import com.example.andrfindmusicapp.utils.TrackDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject

// Класс для управления общим состоянием приложения, плеером и данными
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
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

    private var controller: MediaController? = null
    private val _isControllerReady = MutableStateFlow(false)

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

        viewModelScope.launch {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controller = controllerFuture.await()
            
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
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
        viewModelScope.launch {
            // Ждем готовности контроллера
            _isControllerReady.first { it }
            
            val sortedPlaylist = newPlaylist.sortedWith(compareByDescending<Track> { 
                _favoriteIds.value.contains(it.id) 
            }.thenByDescending { 
                downloadManager.getDownloadedUri(it) != null 
            })

            val tracksToPlay = if (sortedPlaylist.isEmpty()) listOf(track) else sortedPlaylist
            
            _playlist.value = tracksToPlay
            controller?.setMediaItems(tracksToPlay.map { createMediaItem(it) })

            val index = tracksToPlay.indexOfFirst { it.id == track.id }
            if (index != -1) controller?.seekTo(index, 0)

            controller?.prepare()
            controller?.play()
            controller?.repeatMode = Player.REPEAT_MODE_ALL
            _currentTrack.value = tracksToPlay.find { it.id == track.id } ?: track
            preferenceProvider.saveLastState(_currentTrack.value, _playlist.value)
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
                val entity = com.example.andrfindmusicapp.data.local.TrackEntity(
                    id = track.id,
                    name = track.name ?: "Unknown",
                    duration = track.duration ?: 0,
                    artistName = track.artistName ?: "Unknown Artist",
                    albumName = track.albumName ?: "Unknown Album",
                    imageUrl = track.imageUrl ?: "",
                    audioUrl = track.audioUrl ?: "",
                    isFavorite = true,
                    category = ""
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

    // Метод для удаления физического файла трека
    fun deleteTrack(track: Track) = downloadManager.deleteTrackFile(track)
    
    // Метод для получения контроллера плеера
    fun getController(): Player? = controller

    override fun onCleared() {
        super.onCleared()
        controller?.release()
    }
}
