package com.example.andrfindmusicapp.data.remote

import com.example.andrfindmusicapp.utils.UnsafeOkHttpClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Класс-синглтон для настройки и получения клиента Retrofit
object RetrofitClient {
    private const val BASE_URL = "https://api.jamendo.com/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Используем небезопасный клиент для обхода проблем с сертификатами на старых эмуляторах
    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
