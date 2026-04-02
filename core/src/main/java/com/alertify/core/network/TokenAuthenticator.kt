package com.alertify.core.network

import android.util.Log
import com.alertify.core.network.dto.RefreshRequest
import com.alertify.core.session.SessionEvent
import com.alertify.core.session.SessionEventBus
import com.alertify.core.storage.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header(HEADER_AUTH_RETRY) != null) {
            return null
        }

        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/refresh")) {
            return null
        }

        synchronized(lock) {
            val refreshToken = tokenStorage.getRefreshTokenSync()
            if (refreshToken.isNullOrBlank()) {
                tokenStorage.clearSync()
                SessionEventBus.emit(SessionEvent.SessionExpired)
                return null
            }

            val currentAccessToken = tokenStorage.getAccessTokenSync()
            val failedAccessToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.trim()

            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != failedAccessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .header(HEADER_AUTH_RETRY, "1")
                    .build()
            }

            return try {
                val refreshResponse = runBlocking {
                    authApi.refresh(RefreshRequest(refreshToken))
                }

                if (!refreshResponse.isSuccessful || refreshResponse.body() == null) {
                    tokenStorage.clearSync()
                    SessionEventBus.emit(SessionEvent.SessionExpired)
                    return null
                }

                val newTokens = refreshResponse.body()!!
                tokenStorage.saveAccessTokenSync(newTokens.accessToken)
                tokenStorage.saveRefreshTokenSync(newTokens.refreshToken)

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .header(HEADER_AUTH_RETRY, "1")
                    .build()
            } catch (ex: Exception) {
                Log.e(TAG, "Error refreshing token", ex)
                tokenStorage.clearSync()
                SessionEventBus.emit(SessionEvent.SessionExpired)
                null
            }
        }
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
        const val HEADER_AUTH_RETRY = "X-Auth-Retry"
    }
}
