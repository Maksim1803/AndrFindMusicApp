package com.example.andrfindmusicapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val duration: Int,
    val artistName: String,
    val albumName: String,
    val imageUrl: String,
    val audioUrl: String
)