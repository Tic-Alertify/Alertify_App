package com.alertify.core.network

import com.alertify.core.storage.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage
) : Interceptor {

    private val publicPaths = listOf("/auth/login", "/auth/registro", "/auth/refresh")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (publicPaths.any { path.contains(it) }) {
            return chain.proceed(request)
        }

        val token = tokenStorage.getAccessTokenSync()
        val authenticatedRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        return chain.proceed(authenticatedRequest)
    }
}
