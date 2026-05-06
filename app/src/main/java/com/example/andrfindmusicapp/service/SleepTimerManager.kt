package com.example.andrfindmusicapp.service

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

// Класс для управления таймером сна (автоматическая остановка воспроизведения)
@Singleton
class SleepTimerManager @Inject constructor() {
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    
    private val _remainingTime = MutableLiveData<Long>(0L)
    val remainingTime: LiveData<Long> = _remainingTime

    private var _selectedMinutes = 0
    val selectedMinutes: Int get() = _selectedMinutes

    // Метод для запуска таймера на указанное количество минут
    fun startTimer(minutes: Int, onTimerFinished: () -> Unit) {
        stopTimer()
        _selectedMinutes = minutes
        val millis = minutes * 60 * 1000L
        _remainingTime.value = millis
        
        timerRunnable = object : Runnable {
            override fun run() {
                val current = _remainingTime.value ?: 0L
                if (current <= 1000L) {
                    _remainingTime.value = 0L
                    _selectedMinutes = 0
                    onTimerFinished()
                } else {
                    _remainingTime.value = current - 1000L
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(timerRunnable!!, 1000)
    }

    // Метод для принудительной остановки таймера
    fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        _remainingTime.value = 0L
        _selectedMinutes = 0
    }
    
    // Метод для проверки, запущен ли таймер в данный момент
    fun isRunning(): Boolean = (_remainingTime.value ?: 0L) > 0
}
