package com.example.andrfindmusicapp.utils

import java.util.Locale

// Класс для утилит по работе с форматом времени
object TimeUtils {
    // Метод для форматирования миллисекунд в строку вида "mm:ss" или "hh:mm:ss"
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    // Метод для форматирования секунд в строку вида "mm:ss"
    fun formatSeconds(seconds: Int?): String {
        return formatTime(seconds?.toLong()?.times(1000) ?: 0L)
    }
}
