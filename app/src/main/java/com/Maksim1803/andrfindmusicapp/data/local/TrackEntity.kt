package com.Maksim1803.andrfindmusicapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Класс для представления сущности трека в базе данных Room
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val duration: Int,
    val artistName: String,
    val albumName: String,
    val imageUrl: String,
    val audioUrl: String,
    val isFavorite: Boolean = false,
    val category: String = "" // Чтобы знать, к какой категории относится кэш
)