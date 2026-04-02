package com.alertify.core.storage

import com.alertify.core.network.AuthApi
import com.alertify.core.network.dto.LogoutRequest
import com.alertify.core.session.SessionEvent
import com.alertify.core.session.SessionEventBus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionManager @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi
) {
    suspend fun onLoginSuccess(accessToken: String, refreshToken: String) {
        tokenStorage.saveAccessToken(accessToken)
        tokenStorage.saveRefreshToken(refreshToken)
    }

    suspend fun isLoggedIn(): Boolean {
        return tokenStorage.getAccessToken() != null
    }

    fun isLoggedInSync(): Boolean {
        return tokenStorage.getAccessTokenSync() != null
    }

    suspend fun getAccessToken(): String? {
        return tokenStorage.getAccessToken()
    }

    suspend fun getRefreshToken(): String? {
        return tokenStorage.getRefreshToken()
    }

    suspend fun clearSession() {
        tokenStorage.clear()
    }

    suspend fun logout(): Boolean {
        var backendSuccess = false
        val refreshToken = tokenStorage.getRefreshToken()

        if (!refreshToken.isNullOrBlank()) {
            backendSuccess = try {
                val response = authApi.logout(LogoutRequest(refreshToken))
                response.isSuccessful
            } catch (_: Exception) {
                false
            }
        }

        tokenStorage.clear()
        SessionEventBus.emit(SessionEvent.LogoutSuccess)
        return backendSuccess
    }

    fun clearSessionSync() {
        tokenStorage.clearSync()
    }
}
