package com.Maksim1803.andrfindmusicapp.ui.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
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
    private val downloadManager: com.Maksim1803.andrfindmusicapp.utils.TrackDownloadManager
) : ViewModel() {

    private val _localTracks = MutableLiveData<List<Track>>()
    val localTracks: LiveData<List<Track>> = _localTracks

    private val _localFolders = MutableLiveData<Map<String, List<Track>>>()
    val localFolders: LiveData<Map<String, List<Track>>> = _localFolders

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadLocalTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = fetchMusicFiles()
            _localTracks.value = tracks
            _localFolders.value = tracks.groupBy { it.folderName ?: "Music" }
            _isLoading.value = false
        }
    }

    private suspend fun fetchMusicFiles(): List<Track> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
            } else {
                MediaStore.Audio.Media.DATA
            }
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            
            val folderColumn = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            } else {
                cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn) ?: ""
                var artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                var album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getInt(durationColumn) / 1000
                val albumId = cursor.getLong(albumIdColumn)
                val displayName = cursor.getString(displayNameColumn) ?: ""
                
                var folderName = if (folderColumn != -1) {
                    val rawFolder = cursor.getString(folderColumn)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        rawFolder ?: "Music"
                    } else {
                        rawFolder?.let { java.io.File(it).parentFile?.name } ?: "Music"
                    }
                } else "Music"

                // Принудительно помечаем папку Jamendo_Tracks, если файл там
                if (displayName.contains("Jamendo_Tracks") || folderName == "Jamendo_Tracks") {
                    folderName = "Jamendo_Tracks"
                } else if (folderName == "Music" || folderName == "music") {
                    // Если трек в корне Music, проверяем, не наш ли он (по формату имени или ID)
                    // В данном случае просто вызываем проверку через DownloadManager
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val mockTrack = Track(id.toString(), title, null, artist, album, contentUri.toString(), null)
                    if (downloadManager.getDownloadedUri(mockTrack) != null) {
                        folderName = "Jamendo_Tracks"
                    }
                }

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Функция для проверки, является ли строка "кракозябрами" (китайскими иероглифами при ошибке кодировки)
                fun isGibberish(s: String): Boolean {
                    if (s.isEmpty() || s == "<unknown>") return true
                    // Если в строке есть иероглифы (диапазон CJK), но мы не в Китае - это почти наверняка ошибка кодировки
                    return s.any { it.code in 0x4E00..0x9FFF }
                }

                // Если данные от MediaStore похожи на мусор или иероглифы, пробуем альтернативы
                if (title.isEmpty() || title == "<unknown>" || isGibberish(title) || !title.any { it.isLetter() }) {
                    if (displayName.isNotEmpty() && !displayName.startsWith("track_")) {
                        title = displayName.substringBeforeLast(".")
                    } else {
                        // Крайний случай: пробуем MediaMetadataRetriever
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(context, contentUri)
                            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            if (!metaTitle.isNullOrBlank() && !isGibberish(metaTitle)) {
                                title = metaTitle
                            }
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                }
                
                // Если артист тоже в иероглифах, пробуем вытащить его из имени файла (если оно формата "Artist - Title")
                if (isGibberish(artist) || artist == "<unknown>") {
                    if (displayName.contains(" - ")) {
                        artist = displayName.substringBefore(" - ").trim()
                        if (title == displayName.substringBeforeLast(".")) {
                            title = displayName.substringAfter(" - ").substringBeforeLast(".").trim()
                        }
                    }
                }
                
                title = fixEncoding(title)
                artist = fixEncoding(artist)
                album = fixEncoding(album)
                
                // Формируем URI обложки альбома
                val albumArtUri = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                tracksList.add(
                    Track(
                        id = "local_$id",
                        name = title,
                        artistName = artist,
                        albumName = album,
                        duration = duration,
                        audioUrl = contentUri.toString(),
                        imageUrl = albumArtUri,
                        folderName = folderName
                    )
                )
            }
        }
        tracksList
    }

    private fun fixEncoding(s: String): String {
        if (s.isEmpty() || s == "<unknown>") return s
        return try {
            if (s.any { it.code in 128..255 }) {
                val bytes = s.toByteArray(java.nio.charset.StandardCharsets.ISO_8859_1)
                val decoded = String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
                if (decoded.any { it in 'а'..'я' || it in 'А'..'Я' }) decoded else s
            } else s
        } catch (_: Exception) { s }
    }
}
