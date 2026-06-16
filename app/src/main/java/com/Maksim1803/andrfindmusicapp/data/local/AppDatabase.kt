package com.Maksim1803.andrfindmusicapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Класс для управления базой данных Room
@Database(entities = [TrackEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    // Метод для получения доступа к DAO треков
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Метод для получения экземпляра базы данных (Singleton)
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
