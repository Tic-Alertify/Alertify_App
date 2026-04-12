package com.alertify.feature_reportes.viewmodel

import androidx.lifecycle.*
import com.alertify.feature_reportes.data.model.ReportRequest
import com.alertify.feature_reportes.data.repository.ReportRepository
import com.alertify.feature_reportes.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ReportViewModel - Gestiona creación de nuevos reportes
 *
 * RESPONSABILIDADES:
 * - Validar datos del formulario
 * - Orquestar envío HTTP POST /reports
 * - Actualizar estado UI con resultado
 *
 * EXPOSICIÓN:
 * - reportStatus: LiveData<Resource<Map<String, Any>>> (carga/éxito/error)
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: ReportRepository
) : ViewModel() {

    // Estado del envío para la UI
    private val _reportStatus = MutableLiveData<Resource<Map<String, Any>>>()
    val reportStatus: LiveData<Resource<Map<String, Any>>> = _reportStatus

    fun sendReport(userId: Int, typeId: Int, desc: String, lat: Double, lon: Double) {
        val request = ReportRequest(
            userId = userId,
            incidentTypeId = typeId,
            description = desc,
            latitude = lat,
            longitude = lon
        )

        viewModelScope.launch {
            repository.createReport(request).collect {
                _reportStatus.value = it
            }
        }
    }
}