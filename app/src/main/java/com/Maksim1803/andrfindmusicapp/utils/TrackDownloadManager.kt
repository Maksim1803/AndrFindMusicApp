package com.Maksim1803.andrfindmusicapp.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.Maksim1803.andrfindmusicapp.data.model.Track
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

    companion object {
        private const val JAMENDO_SUBDIR = "Jamendo_Tracks"
    }

    // Метод для запуска процесса загрузки трека
    fun downloadTrack(track: Track) {
        val url = track.audioUrl ?: return
        val fileName = "${track.artistName} - ${track.name}.mp3"
        
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.name)
            .setDescription(track.artistName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "$JAMENDO_SUBDIR/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadManager.enqueue(request)
    }

    // Метод для получения локального Uri, если трек скачан
    fun getDownloadedUri(track: Track): Uri? {
        val fileName = "${track.artistName} - ${track.name}.mp3"
        
        // Сначала проверяем в специальной папке Jamendo_Tracks
        val jamendoFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$JAMENDO_SUBDIR/$fileName")
        if (jamendoFile.exists()) return Uri.fromFile(jamendoFile)
        
        // Затем проверяем в корне папки Music (для совместимости)
        val legacyFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), fileName)
        if (legacyFile.exists()) {
            // Если нашли в корне, пробуем переместить в Jamendo_Tracks
            if (moveFileToJamendoFolder(legacyFile, fileName)) {
                return Uri.fromFile(jamendoFile)
            }
            return Uri.fromFile(legacyFile)
        }
        
        return null
    }

    // Внутренний метод для перемещения файла в папку Jamendo_Tracks
    private fun moveFileToJamendoFolder(sourceFile: File, fileName: String): Boolean {
        return try {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), JAMENDO_SUBDIR)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, fileName)
            val moved = sourceFile.renameTo(targetFile)
            if (moved) {
                // Оповещаем систему, что файлы переместились, чтобы MediaStore обновился
                android.media.MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath, targetFile.absolutePath), null, null)
            }
            moved
        } catch (_: Exception) {
            false
        }
    }

    // Метод для удаления физического файла трека
    fun deleteTrackFile(track: Track): Boolean {
        return try {
            val fileName = "${track.artistName} - ${track.name}.mp3"
            
            // Проверяем в приватной папке
            val privateFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
            if (privateFile.exists()) return privateFile.delete()

            // Проверяем в Jamendo_Tracks
            val jamendoFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$JAMENDO_SUBDIR/$fileName")
            if (jamendoFile.exists()) return jamendoFile.delete()

            // Проверяем в корне Music
            val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), fileName)
            if (publicFile.exists()) return publicFile.delete()
            
            false
        } catch (_: Exception) {
            false
        }
    }
}
