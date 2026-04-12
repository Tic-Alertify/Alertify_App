package com.alertify.feature_reportes.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertify.feature_reportes.data.repository.ReportRepository
import com.alertify.feature_reportes.ui.heatmap.HeatmapUiState
import com.alertify.feature_reportes.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HeatmapViewModel @Inject constructor(
    private val repository: ReportRepository
) : ViewModel() {

    // Única fuente de verdad para la UI
    private val _uiState = MutableLiveData<HeatmapUiState>(HeatmapUiState())
    val uiState: LiveData<HeatmapUiState> = _uiState

    fun fetchMapData() {
        Log.d(TAG, "Iniciando fetchMapData")

        // Iniciar carga
        updateState { it.copy(isLoading = true, error = null) }
        repository.initSocket()

        // --- BLOQUE A: Carga de Heatmap (HTTP) [Data para visualización térmica]
        viewModelScope.launch {
            repository.getHeatmapData(days = 365).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        Log.d(TAG, "Heatmap cargado: ${resource.data.size} puntos")
                        updateState { it.copy(points = resource.data, isLoading = false) }
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Error heatmap: ${resource.message}")
                        updateState { it.copy(error = resource.message, isLoading = false) }
                    }
                    is Resource.Loading -> updateState { it.copy(isLoading = true) }
                }
            }
        }

        // --- BLOQUE B: Carga de Reportes Recientes (HTTP) [Últimos 7 días]
        viewModelScope.launch {
            repository.getValidatedReports().collect { resource ->
                if (resource is Resource.Success) {
                    val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                    val filtered = resource.data.filter {
                        parseDate(it.createdAt) > weekAgo
                    }
                    Log.d(TAG, "Reportes recientes cargados: ${filtered.size}")
                    updateState { it.copy(recentReports = filtered) }
                } else if (resource is Resource.Error) {
                    Log.e(TAG, "Error reportes: ${resource.message}")
                }
            }
        }

        // --- BLOQUE C: Escucha en Tiempo Real (WebSocket) [Nuevos reportes mientras app está abierta]
        viewModelScope.launch {
            repository.listenToLiveReports().collect { liveReport ->
                val state = _uiState.value ?: HeatmapUiState()
                val currentReports = state.recentReports

                // Blindaje contra duplicados (evita parpadeo de marcador en mapa)
                if (liveReport.id != -1 && currentReports.none { it.id == liveReport.id }) {
                    val updatedList = listOf(liveReport) + currentReports
                    Log.d(TAG, "Nuevo reporte en vivo: ${liveReport.id}")
                    updateState { it.copy(recentReports = updatedList) }
                }
            }
        }
    }

    /**
     * Limpia recursos cuando el ViewModel se destruye
     * Se llama automáticamente cuando el Fragment se destruye
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "Limpiando ViewModel")
        repository.closeSocket()
    }

    /**
     * Actualiza el estado de forma thread-safe
     * Usa postValue para actualizar desde cualquier thread
     */
    private fun updateState(transform: (HeatmapUiState) -> HeatmapUiState) {
        val currentState = _uiState.value ?: HeatmapUiState()
        _uiState.postValue(transform(currentState))
    }

    /**
     * Parsea fechas ISO 8601 del backend (ej: "2024-01-15T10:30:00.000Z")
     * Retorna milisegundos desde epoch para comparar
     */
    private fun parseDate(dateStr: String?): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(dateStr ?: "")?.time ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Error parseando fecha: $dateStr", e)
            0L
        }
    }

    companion object {
        private const val TAG = "HeatmapViewModel"
    }
}