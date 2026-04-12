package com.alertify.feature_reportes.viewmodel

import androidx.lifecycle.*
import com.alertify.feature_reportes.data.model.HeatmapPoint
import com.alertify.feature_reportes.data.model.ReportResponse
import com.alertify.feature_reportes.data.repository.ReportRepository
import com.alertify.feature_reportes.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * MapViewModel - Gestiona datos del mapa y marcadores
 *
 * RESPONSABILIDADES:
 * - Cargar puntos de heatmap (datos térmicos)
 * - Cargar reportes recientes de otros usuarios
 * - Cargar historial de reportes del usuario actual
 * - Combinar y fusionar datos para visualización
 *
 * EXPOSICIÓN:
 * - heatmapPoints: LiveData<Resource<List<HeatmapPoint>>>
 * - userReportsList: LiveData<List<ReportResponse>>
 * - allMarkersToDraw: MediatorLiveData (combina recientes + historial)
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: ReportRepository
) : ViewModel() {

    private val _heatmapPoints = MutableLiveData<Resource<List<HeatmapPoint>>>()
    val heatmapPoints: LiveData<Resource<List<HeatmapPoint>>> = _heatmapPoints

    private val _recentReports = MutableLiveData<List<ReportResponse>>()
    private val _userReports = MutableLiveData<List<ReportResponse>>()
    val userReportsList: LiveData<List<ReportResponse>> = _userReports

    // Combina reportes recientes + historial, elimina duplicados
    val allMarkersToDraw = MediatorLiveData<List<ReportResponse>>().apply {
        addSource(_recentReports) { value = (it + (_userReports.value ?: emptyList())).distinctBy { r -> r.id } }
        addSource(_userReports) { value = (it + (_recentReports.value ?: emptyList())).distinctBy { r -> r.id } }
    }

    fun loadAllMapData(userId: Int) {
        viewModelScope.launch {
            repository.getHeatmapData(365).collect { _heatmapPoints.value = it }
            repository.getValidatedReports().collect { resource ->
                if (resource is Resource.Success) {
                    val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                    _recentReports.value = resource.data.filter { parseIsoDate(it.createdAt) > weekAgo }
                }
            }
            loadUserHistory(userId)
        }
    }

    fun loadUserHistory(userId: Int) {
        viewModelScope.launch {
            repository.getReportsByUser(userId).collect { resource ->
                if (resource is Resource.Success) {
                    _userReports.value = resource.data ?: emptyList()
                }
            }
        }
    }

    private fun parseIsoDate(dateStr: String?): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(dateStr ?: "")?.time ?: 0
        } catch (e: Exception) { 0L }
    }
}