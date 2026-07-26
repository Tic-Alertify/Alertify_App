package com.alertify.feature_reportes.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.MapStyleOptions
import com.alertify.feature_reportes.R

object MapStyleManager {
    private const val PREF_NAME = "map_style_prefs"
    private const val KEY_IS_DARK_MODE = "is_dark_mode"
    private const val DEFAULT_DARK_MODE = true

    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isDarkMode(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_DARK_MODE, DEFAULT_DARK_MODE)
    }

    fun toggleMapStyle(context: Context, map: GoogleMap) {
        val isDark = isDarkMode(context)
        val newIsDark = !isDark
        
        // Guardar preferencia
        getPreferences(context).edit().putBoolean(KEY_IS_DARK_MODE, newIsDark).apply()
        
        // Aplicar estilo
        applyMapStyle(context, map, newIsDark)
    }

    fun applyMapStyle(context: Context, map: GoogleMap, isDarkMode: Boolean) {
        try {
            val styleResourceId = if (isDarkMode) R.raw.map_style else R.raw.map_style_default
            val style = MapStyleOptions.loadRawResourceStyle(context, styleResourceId)
            map.setMapStyle(style)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initMapStyle(context: Context, map: GoogleMap) {
        applyMapStyle(context, map, isDarkMode(context))
    }
}
