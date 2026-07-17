package com.alertify.feature_reportes.config

import com.alertify.core.BuildConfig

/**
 * Global configuration manager for API endpoints, WebSocket URLs, and geographic boundaries.
 * Uses BuildConfig variants for debug/release configuration switching.
 */
object ConfigManager {
    /** Backend API base URL. For Android emulator, uses host machine IP (192.168.100.35). */
    val BASE_URL: String = "http://192.168.100.35:3000/"

    /** WebSocket server URL for real-time incident updates. */
    const val SOCKET_URL = "http://192.168.100.35:3000"

    // Geographic boundaries for client-side quick validation against Quito coverage area.
    // Prevents unnecessary backend calls for out-of-bounds reports.
    const val QUITO_LAT_NORTH = -0.015
    const val QUITO_LAT_SOUTH = -0.350
    const val QUITO_LON_WEST = -78.600
    const val QUITO_LON_EAST = -78.350

    /**
     * User identifier for API requests.
     * Se sincroniza automáticamente después del login desde la sesión persistida.
     * Se mantuvo el valor anterior comentado como referencia histórica.
     */
    // var currentUserId: Int = 1
    var currentUserId: Int = 0
        private set

    fun setCurrentUserId(userId: Int?) {
        currentUserId = userId?.takeIf { it > 0 } ?: 0
    }

    fun clearCurrentUserId() {
        currentUserId = 0
    }
}