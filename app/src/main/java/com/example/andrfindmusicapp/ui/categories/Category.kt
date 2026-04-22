package com.example.andrfindmusicapp.ui.categories

data class Category(
    val id: Int,
    val name: String,
    val tag: String // Это будет использоваться для запроса в Jamendo
)