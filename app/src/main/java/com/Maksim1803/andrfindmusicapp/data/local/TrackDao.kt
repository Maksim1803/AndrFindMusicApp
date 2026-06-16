package com.Maksim1803.andrfindmusicapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Интерфейс для взаимодействия с таблицей треков в базе данных
@Dao
interface TrackDao {
    // Метод для получения всех избранных треков
    @Query("SELECT * FROM tracks WHERE isFavorite = 1")
    fun getAllFavorites(): Flow<List<TrackEntity>>

    // Метод для получения треков по категории
    @Query("SELECT * FROM tracks WHERE category = :category")
    fun getTracksByCategory(category: String): Flow<List<TrackEntity>>

    // Метод для вставки списка треков с игнорированием конфликтов
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    // Метод для обновления категории трека
    @Query("UPDATE tracks SET category = :category WHERE id = :id")
    suspend fun updateTrackCategory(id: String, category: String)

    // Метод для обновления статуса избранного
    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    // Метод для вставки или замены трека
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    // Метод для проверки, является ли трек избранным
    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE id = :id AND isFavorite = 1)")
    suspend fun isFavorite(id: String): Boolean

    // Метод для удаления закэшированных треков определенной категории
    @Query("DELETE FROM tracks WHERE isFavorite = 0 AND category = :category")
    suspend fun clearCacheByCategory(category: String)

    // Метод для умного обновления кэша треков без потери статуса избранного
    @Transaction
    suspend fun upsertCacheTracks(tracks: List<TrackEntity>) {
        tracks.forEach { track ->
            val inserted = insertIgnore(track)
            if (inserted == -1L) {
                // Если трек уже есть, обновляем только категорию (не трогая isFavorite)
                updateTrackCategory(track.id, track.category)
            }
        }
    }

    // Метод для попытки вставки трека с игнорированием существующих
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(track: TrackEntity): Long
}
