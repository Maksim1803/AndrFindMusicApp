package com.Maksim1803.andrfindmusicapp.ui.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Maksim1803.andrfindmusicapp.data.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LocalTracksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: com.Maksim1803.andrfindmusicapp.utils.TrackDownloadManager,
    private val preferenceProvider: com.Maksim1803.andrfindmusicapp.utils.PreferenceProvider
) : ViewModel() {

    private val _localTracks = MutableLiveData<List<Track>>()
    val localTracks: LiveData<List<Track>> = _localTracks

    private val _localFolders = MutableLiveData<Map<String, List<Track>>>()
    val localFolders: LiveData<Map<String, List<Track>>> = _localFolders

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _metadataOverrides = MutableLiveData<Map<String, Track>>(emptyMap())
    val metadataOverrides: LiveData<Map<String, Track>> = _metadataOverrides

    private val _renameEvent = MutableLiveData<Pair<Boolean, String?>>()
    val renameEvent: LiveData<Pair<Boolean, String?>> = _renameEvent

    private val _pendingIntent = MutableLiveData<android.app.PendingIntent?>()
    val pendingIntent: LiveData<android.app.PendingIntent?> = _pendingIntent

    // Кэш для хранения правок, чтобы MediaStore не затирал их старыми данными
    private val metadataOverridesCache = mutableMapOf<String, Track>()

    private var pendingAction: (() -> Unit)? = null

    init {
        // Загружаем сохраненные правки из SharedPreferences
        val saved = preferenceProvider.getMetadataOverrides()
        metadataOverridesCache.putAll(saved)
        _metadataOverrides.value = saved
    }

    fun loadLocalTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = fetchMusicFiles()
            
            // Применяем сессионные правки поверх данных из MediaStore
            val finalTracks = tracks.map { track ->
                metadataOverridesCache[track.id] ?: track
            }
            
            _localTracks.value = finalTracks
            _localFolders.value = finalTracks.groupBy { it.folderName ?: "Music" }
            _isLoading.value = false
        }
    }

    fun updateTrackMetadata(
        track: Track,
        newName: String,
        newAlbum: String,
        newArtist: String,
        newGenre: String,
        newYear: String,
        newFileName: String
    ) {
        viewModelScope.launch {
            try {
                val trackIdLong = track.id.removePrefix("local_").toLongOrNull() ?: return@launch
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackIdLong)

                val extension = track.displayName?.substringAfterLast(".", "mp3") ?: "mp3"
                val finalFileName = if (newFileName.contains(".")) newFileName else "$newFileName.$extension"

                val values = android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, newName)
                    put(MediaStore.Audio.Media.ALBUM, newAlbum)
                    put(MediaStore.Audio.Media.ARTIST, newArtist)
                    newYear.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        put(MediaStore.Audio.Media.GENRE, newGenre)
                    }
                    put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
                }

                val updatedRows = withContext(Dispatchers.IO) {
                    context.contentResolver.update(contentUri, values, null, null)
                }
                
                if (updatedRows > 0) {
                    // Сохраняем правку в кэш и ПЕРСИСТИРУЕМ её
                    val updatedTrack = track.copy(
                        name = newName,
                        albumName = newAlbum,
                        artistName = newArtist,
                        genre = newGenre,
                        year = newYear,
                        displayName = finalFileName
                    )
                    metadataOverridesCache[track.id] = updatedTrack
                    preferenceProvider.saveMetadataOverrides(metadataOverridesCache)
                    _metadataOverrides.value = metadataOverridesCache

                    // Обновляем текущее состояние мгновенно
                    val currentTracks = _localTracks.value?.toMutableList() ?: mutableListOf()
                    val index = currentTracks.indexOfFirst { it.id == track.id }
                    if (index != -1) {
                        currentTracks[index] = updatedTrack
                        _localTracks.value = currentTracks
                        _localFolders.value = currentTracks.groupBy { it.folderName ?: "Music" }
                    }

                    // Уведомляем систему
                    val filePath = withContext(Dispatchers.IO) {
                        context.contentResolver.query(contentUri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { 
                            if (it.moveToFirst()) it.getString(0) else null 
                        }
                    }
                    filePath?.let { path ->
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                    }

                    _renameEvent.value = true to null
                } else {
                    _renameEvent.value = false to "No rows updated"
                }
            } catch (e: SecurityException) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
                    e is android.app.RecoverableSecurityException) {
                    pendingAction = { updateTrackMetadata(track, newName, newAlbum, newArtist, newGenre, newYear, newFileName) }
                    _pendingIntent.value = e.userAction.actionIntent
                } else {
                    _renameEvent.value = false to e.message
                }
            } catch (e: Exception) {
                _renameEvent.value = false to e.message
            }
        }
    }

    fun onPermissionGranted() {
        pendingAction?.invoke()
        pendingAction = null
    }

    fun clearPendingIntent() {
        _pendingIntent.value = null
    }

    private suspend fun fetchMusicFiles(): List<Track> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<Track>()
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
            }
        }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dispCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            
            val genreCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else -1

            val folderCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: ""
                val artist = cursor.getString(artistCol) ?: "Unknown"
                val album = cursor.getString(albumCol) ?: "Unknown"
                val duration = cursor.getInt(durCol) / 1000
                val albumId = cursor.getLong(albIdCol)
                val displayName = cursor.getString(dispCol) ?: ""
                val year = cursor.getInt(yearCol)
                val dataPath = cursor.getString(dataCol)
                val genre = if (genreCol != -1) cursor.getString(genreCol) ?: "" else ""
                
                val folderName = if (folderCol != -1) {
                    cursor.getString(folderCol) ?: "Music"
                } else {
                    java.io.File(dataPath).parentFile?.name ?: "Music"
                }

                val albumArtUri = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                tracksList.add(
                    Track(
                        id = "local_$id",
                        name = if (title.isBlank() || title == "<unknown>") displayName.substringBeforeLast(".") else title,
                        artistName = artist,
                        albumName = album,
                        duration = duration,
                        audioUrl = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        imageUrl = albumArtUri,
                        folderName = if (dataPath.contains("Jamendo_Tracks")) "Jamendo_Tracks" else folderName,
                        genre = genre,
                        year = if (year > 0) year.toString() else "",
                        displayName = displayName
                    )
                )
            }
        }
        tracksList
    }
}
