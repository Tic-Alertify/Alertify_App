package com.alertify.core.storage

interface TokenStorage {
    suspend fun saveAccessToken(token: String)
    suspend fun getAccessToken(): String?

    suspend fun saveRefreshToken(token: String)
    suspend fun getRefreshToken(): String?

    fun getAccessTokenSync(): String?
    fun saveAccessTokenSync(token: String)

    fun getRefreshTokenSync(): String?
    fun saveRefreshTokenSync(token: String)

    suspend fun clear()
    fun clearSync()
}
