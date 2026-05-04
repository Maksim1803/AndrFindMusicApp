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

    private val _isLooping = MutableStateFlow(true) // По умолчанию включено
    val isLooping = _isLooping.asStateFlow()

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
                            // Находим трек в нашем текущем плейлисте по ID
                            val track = _playlist.value.find { it.id == id }
                            _currentTrack.value = track
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Реализация закольцовки: если плейлист закончился и включена закольцовка
                    if (playbackState == Player.STATE_ENDED && _isLooping.value) {
                        controller?.seekTo(0, 0)
                        controller?.play()
                    }
                }
            })

            // Устанавливаем режим повтора "All" для закольцовки внутри списка (Media3 сама умеет)
            controller?.repeatMode = if (_isLooping.value) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

            // Автоматически подгружаем избранное как основной плейлист
            trackDao.getAllFavorites()
                .map { entities -> entities.map { it.toTrack() } }
                .distinctUntilChanged()
                .collectLatest { tracks ->
                    _favoriteIds.value = tracks.map { it.id }.toSet()
                    if (_playlist.value.isEmpty()) {
                        _playlist.value = tracks
                        updateControllerPlaylist(tracks)
                    }
                }
        }
    }

    // Вспомогательный метод для создания MediaItem из модели Track
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

    // Метод для обновления плейлиста в медиа-контроллере
    private fun updateControllerPlaylist(tracks: List<Track>) {
        val mediaItems = tracks.map { createMediaItem(it) }
        controller?.setMediaItems(mediaItems)
    }

    // Метод для добавления или удаления трека из избранного
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isCurrentlyFav = trackDao.isFavorite(track.id)
            if (isCurrentlyFav) {
                trackDao.updateFavoriteStatus(track.id, false)
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
                trackDao.insertTrack(entity)
                trackDao.updateFavoriteStatus(track.id, true)
                // При добавлении в избранное также добавляем в текущую очередь
                addToQueue(track)
            }
        }
    }

    // Метод для воспроизведения конкретного трека с автоматическим добавлением в очередь
    fun playTrack(track: Track) {
        val index = _playlist.value.indexOfFirst { it.id == track.id }
        if (index != -1) {
            controller?.seekTo(index, 0)
        } else {
            // Если трека нет в списке, добавляем его в конец и переходим к нему
            addToQueue(track)
            controller?.seekTo(_playlist.value.size - 1, 0)
        }
        controller?.prepare()
        controller?.play()
        _currentTrack.value = track
    }

    // Метод для воспроизведения трека с установкой нового плейлиста (например, из поиска)
    fun playTrackWithPlaylist(track: Track, newPlaylist: List<Track>) {
        if (newPlaylist.isNotEmpty() && _playlist.value != newPlaylist) {
            _playlist.value = newPlaylist
            val mediaItems = newPlaylist.map { createMediaItem(it) }
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

    // Метод для добавления трека в текущую очередь воспроизведения
    fun addToQueue(track: Track) {
        val currentList = _playlist.value.toMutableList()
        if (!currentList.any { it.id == track.id }) {
            currentList.add(track)
            _playlist.value = currentList
            
            controller?.addMediaItem(createMediaItem(track))
        }
    }
    fun togglePlayPause() {
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play()
        }
    }

    // Метод для перехода к следующему треку
    fun skipToNext() {
        controller?.seekToNext()
    }

    // Метод для перехода к предыдущему треку
    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    // Метод для перемотки на указанную позицию
    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    // Метод для получения экземпляра контроллера плеера
    fun getController(): Player? = controller

    private fun com.example.andrfindmusicapp.data.local.TrackEntity.toTrack() = Track(
        id = id,
        name = name,
        duration = duration,
        artistName = artistName,
        albumName = albumName,
        imageUrl = imageUrl,
        audioUrl = audioUrl
    )

    // Метод для очистки ресурсов при уничтожении ViewModel
    override fun onCleared() {
        super.onCleared()
        controller?.release()
    }
}
