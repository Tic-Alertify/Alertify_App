package com.alertify.core.storage

interface TokenStorage {
    suspend fun saveAccessToken(token: String)
    suspend fun getAccessToken(): String?

    suspend fun saveRefreshToken(token: String)
    suspend fun getRefreshToken(): String?

    suspend fun saveCurrentUserId(userId: Int)
    suspend fun getCurrentUserId(): Int?

    fun getAccessTokenSync(): String?
    fun saveAccessTokenSync(token: String)

    fun getRefreshTokenSync(): String?
    fun saveRefreshTokenSync(token: String)

    fun getCurrentUserIdSync(): Int?
    fun saveCurrentUserIdSync(userId: Int)

    suspend fun clear()
    fun clearSync()
}
