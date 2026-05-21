package com.example.andrfindmusicapp.data.remote

import com.example.andrfindmusicapp.utils.UnsafeOkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Класс-синглтон для настройки и получения клиента Retrofit
object RetrofitClient {
    private const val BASE_URL = "https://api.jamendo.com/"

    // Используем небезопасный клиент для обхода проблем с сертификатами на старых эмуляторах
    // Логирование отключено для релизной версии
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Свойство для получения настроенного сервиса JamendoService
    val jamendoService: JamendoService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JamendoService::class.java)
    }
}
