package com.example.andrfindmusicapp.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Класс-синглтон для настройки и получения клиента Retrofit
object RetrofitClient {
    private const val BASE_URL = "https://api.jamendo.com/"

    // Свойство для получения настроенного сервиса JamendoService
    val jamendoService: JamendoService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JamendoService::class.java)
    }
}