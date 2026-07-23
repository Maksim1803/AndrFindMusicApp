package com.Maksim1803.andrfindmusicapp.service

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Класс для управления таймером сна, автоматически останавливающим воспроизведение
@Singleton
class SleepTimerManager @Inject constructor() {
    private var timer: CountDownTimer? = null
    
    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft = _timeLeft.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private var _lastSetMinutes = 0
    val lastSetMinutes: Int get() = if (_isTimerRunning.value) _lastSetMinutes else -1

    // Метод для запуска таймера на указанное количество минут
    fun startTimer(minutes: Int, onTimerFinished: () -> Unit) {
        _lastSetMinutes = minutes
        timer?.cancel()
        val millis = minutes * 60 * 1000L
        
        timer = object : CountDownTimer(millis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _timeLeft.value = millisUntilFinished
                _isTimerRunning.value = true
            }

            override fun onFinish() {
                _timeLeft.value = 0
                _isTimerRunning.value = false
                onTimerFinished()
            }
        }.start()
    }

    // Метод для отмены таймера
    fun stopTimer() {
        timer?.cancel()
        _timeLeft.value = 0
        _isTimerRunning.value = false
    }
}
