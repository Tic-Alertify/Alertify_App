package com.alertify.core.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsTokenStorage @Inject constructor(
    @ApplicationContext context: Context
) : TokenStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun saveAccessToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveRefreshToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveCurrentUserId(userId: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_CURRENT_USER_ID, userId).apply()
    }

    override suspend fun getCurrentUserId(): Int? = withContext(Dispatchers.IO) {
        val userId = prefs.getInt(KEY_CURRENT_USER_ID, -1)
        userId.takeIf { it > 0 }
    }

    override fun getAccessTokenSync(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override fun saveAccessTokenSync(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override fun getRefreshTokenSync(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override fun saveRefreshTokenSync(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override fun getCurrentUserIdSync(): Int? {
        val userId = prefs.getInt(KEY_CURRENT_USER_ID, -1)
        return userId.takeIf { it > 0 }
    }

    override fun saveCurrentUserIdSync(userId: Int) {
        prefs.edit().putInt(KEY_CURRENT_USER_ID, userId).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_CURRENT_USER_ID)
            .apply()
    }

    override fun clearSync() {
        prefs.edit().remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_CURRENT_USER_ID)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "alertify_auth_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_CURRENT_USER_ID = "current_user_id"
    }
}
