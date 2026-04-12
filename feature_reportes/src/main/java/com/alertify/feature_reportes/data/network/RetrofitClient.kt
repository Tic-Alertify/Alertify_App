package com.alertify.feature_reportes.data.network

import com.alertify.feature_reportes.config.ConfigManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .addInterceptor(AuthInterceptor("TU_TOKEN_TEMPORAL_AQUI")) // Paso previo al Login real
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: AlertifyApiService by lazy {
        Retrofit.Builder()
            // Usamos la URL centralizada del ConfigManager que creamos antes
            .baseUrl(ConfigManager.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlertifyApiService::class.java)
    }
}