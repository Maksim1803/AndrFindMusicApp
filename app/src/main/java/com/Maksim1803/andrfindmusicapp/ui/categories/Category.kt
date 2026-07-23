package com.Maksim1803.andrfindmusicapp.ui.categories

// Класс для представления категории музыки
data class Category(
    val id: Int,
    val name: String,
    val tag: String // Это будет использоваться для запроса в Jamendo
)