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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject

// Класс для управления основным состоянием приложения и взаимодействия с медиа-контроллером
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val trackDao: TrackDao,
    val sleepTimerManager: SleepTimerManager
) : ViewModel() {

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist = _playlist.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()

    private var controller: MediaController? = null

    init {
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
                        }
                    }
                }
            })

            // Автоматически подгружаем список избранных ID для UI
            trackDao.getAllFavorites()
                .map { entities -> entities.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collectLatest { ids ->
                    _favoriteIds.value = ids
                }
        }
    }

    private fun createMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.audioUrl ?: "")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.name ?: "Unknown")
                    .setArtist(track.artistName ?: "Unknown Artist")
                    .setArtworkUri(if (track.imageUrl != null) android.net.Uri.parse(track.imageUrl) else null)
                    .build()
            )
            .build()
    }

    fun playTrackWithPlaylist(track: Track, newPlaylist: List<Track>) {
        val tracksToPlay = if (newPlaylist.isEmpty()) listOf(track) else newPlaylist
        
        if (_playlist.value != tracksToPlay) {
            _playlist.value = tracksToPlay
            val mediaItems = tracksToPlay.map { createMediaItem(it) }
            controller?.setMediaItems(mediaItems)
        }

        val index = _playlist.value.indexOfFirst { it.id == track.id }
        if (index != -1) {
            controller?.seekTo(index, 0)
        }

        controller?.prepare()
        controller?.play()
        _currentTrack.value = track
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isCurrentlyFav = trackDao.isFavorite(track.id)
            if (isCurrentlyFav) {
                // Если уже в избранном - убираем флаг
                trackDao.updateFavoriteStatus(track.id, false)
            } else {
                // Если нет в базе - вставляем, если есть - просто обновляем флаг
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
                // Используем insertIgnore, чтобы не затереть существующую категорию в кэше
                trackDao.insertIgnore(entity)
                trackDao.updateFavoriteStatus(track.id, true)
            }
        }
    }

    fun togglePlayPause() {
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play()
        }
    }

    fun skipToNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerManager.startTimer(minutes) {
            controller?.pause()
        }
    }

    fun stopSleepTimer() {
        sleepTimerManager.stopTimer()
    }

    fun getController(): Player? = controller

    override fun onCleared() {
        super.onCleared()
        controller?.release()
    }
}
