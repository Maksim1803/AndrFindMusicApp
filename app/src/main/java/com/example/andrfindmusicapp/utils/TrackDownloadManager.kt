package com.example.andrfindmusicapp.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.andrfindmusicapp.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Класс для управления загрузкой аудиофайлов на устройство
@Singleton
class TrackDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Метод для запуска процесса загрузки трека
    fun downloadTrack(track: Track) {
        val url = track.audioUrl ?: return
        val fileName = "${track.artistName} - ${track.name}.mp3"
        
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.name)
            .setDescription(track.artistName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadManager.enqueue(request)
    }

    // Метод для получения локального Uri, если трек скачан
    fun getDownloadedUri(track: Track): Uri? {
        val fileName = "${track.artistName} - ${track.name}.mp3"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), fileName)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    // Метод для удаления физического файла трека
    fun deleteTrackFile(track: Track): Boolean {
        return try {
            val fileName = "${track.artistName} - ${track.name}.mp3"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
            if (file.exists()) {
                file.delete()
            } else {
                // Если не нашли в приватной папке, пробуем публичную
                val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), fileName)
                publicFile.delete()
            }
        } catch (_: Exception) {
            false
        }
    }
}
