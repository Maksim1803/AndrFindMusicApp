package com.example.andrfindmusicapp.utils

import java.util.Locale

// Утилитарный класс для форматирования времени
object TimeUtils {
    // Форматирует секунды в строку вида "MM:SS"
    fun formatSeconds(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }

    // Форматирует миллисекунды в строку вида "MM:SS"
    fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
