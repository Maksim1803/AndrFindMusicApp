package com.example.andrfindmusicapp.utils

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ВНИМАНИЕ: Этот класс отключает проверку SSL-сертификатов.
// Использовать ТОЛЬКО для отладки на старых эмуляторах, где устарели корневые сертификаты.
object UnsafeOkHttpClient {
    fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            // Создаем trust manager, который не проверяет цепочки сертификатов
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Устанавливаем наш trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            
            val sslSocketFactory = sslContext.socketFactory

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }

            return builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
