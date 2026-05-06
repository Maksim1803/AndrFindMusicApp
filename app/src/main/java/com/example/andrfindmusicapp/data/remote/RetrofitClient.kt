package com.example.andrfindmusicapp.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Класс-синглтон для настройки и получения клиента Retrofit
object RetrofitClient {
    private const val BASE_URL = "https://api.jamendo.com/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    // Свойство для получения настроенного сервиса JamendoService
    val jamendoService: JamendoService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JamendoService::class.java)
    }
}
