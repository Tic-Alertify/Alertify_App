package com.alertify.feature_reportes.data.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token") // Formato estándar JWT
            .addHeader("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}