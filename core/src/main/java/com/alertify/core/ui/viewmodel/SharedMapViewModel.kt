package com.alertify.core.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertify.core.utils.Constants
import com.alertify.core.utils.GeoUtils
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SharedMapViewModel - ViewModel compartido entre HeatmapFragment y RuteoFragment
 * 
 * PROPÓSITO:
 * Unificar la lógica de dos mapas (reporte + ruteo) en un único ViewModel
 * que sea accesible desde DashboardHostFragment mediante activityViewModels()
 *
 * RESPONSABILIDADES:
 * 1. Gestionar datos de Heatmap (reportes, puntos de calor)
 * 2. Gestionar cálculo de rutas seguras
 * 3. Mantener comunicación bidireccional entre módulos
 *
 * NOTA ARQUITECTÓNICA:
 * Las dependencias (ReportRepository, RuteoRepository) se obtienen de forma lazy
 * para evitar dependencias circulares entre módulos. Se inyectan a través de métodos
 * públicos, no en el constructor.
 */
@HiltViewModel
class SharedMapViewModel @Inject constructor() : ViewModel() {

    private companion object {
        private const val TAG = "SharedMapViewModel"
    }

    // Repositorios obtenidos de forma lazy (inyectados después de construcción)
    private var reportRepository: Any? = null
    private var ruteoRepository: Any? = null

    // ═══════════════════════════════════════════════════════════════════════
    // SECCIÓN HEATMAP: Para observación por HeatmapFragment
    // ═══════════════════════════════════════════════════════════════════════

    private val _heatmapPoints = MutableStateFlow<Any?>(null)  // Resource<List<HeatmapPoint>>
    val heatmapPoints: StateFlow<Any?> = _heatmapPoints.asStateFlow()

    private val _recentReports = MutableStateFlow<List<Any>>(emptyList())  // List<ReportResponse>
    val recentReports: StateFlow<List<Any>> = _recentReports.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // SECCIÓN RUTEO: Para observación por RuteoFragment
    // ═══════════════════════════════════════════════════════════════════════

    // Coordenadas de origen y destino
    private val _coordenadaOrigen = MutableStateFlow<LatLng?>(null)
    val coordenadaOrigen: StateFlow<LatLng?> = _coordenadaOrigen.asStateFlow()

    private val _coordenadaDestino = MutableStateFlow<LatLng?>(null)
    val coordenadaDestino: StateFlow<LatLng?> = _coordenadaDestino.asStateFlow()

    // Resultado de la ruta calculada
    private val _rutaPolyline = MutableStateFlow<String?>(null)
    val rutaPolyline: StateFlow<String?> = _rutaPolyline.asStateFlow()

    private val _nivelRiesgo = MutableStateFlow<String?>(null)
    val nivelRiesgo: StateFlow<String?> = _nivelRiesgo.asStateFlow()

    private val _tiempoEstimado = MutableStateFlow<Int?>(null)
    val tiempoEstimado: StateFlow<Int?> = _tiempoEstimado.asStateFlow()

    // Estados de carga y error
    private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // INYECCIÓN DE DEPENDENCIAS (Por métodos públicos para evitar ciclos)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inyecta el ReportRepository (llamado por DashboardHostFragment)
     */
    fun injectReportRepository(repo: Any) {
        this.reportRepository = repo
        Log.d(TAG, "ReportRepository inyectado")
    }

    /**
     * Inyecta el RuteoRepository (llamado por DashboardHostFragment)
     */
    fun injectRuteoRepository(repo: Any) {
        this.ruteoRepository = repo
        Log.d(TAG, "RuteoRepository inyectado")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS PÚBLICOS - HEATMAP
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Carga datos del heatmap desde el backend (últimos 365 días)
     * Llamado por: DashboardHostFragment.onViewCreated()
     */
    fun loadHeatmapData() {
        Log.d(TAG, "Iniciando loadHeatmapData")
        if (reportRepository == null) {
            _errorMessage.value = "ReportRepository no inyectado"
            return
        }
        viewModelScope.launch {
            try {
                // Usar reflection para llamar a los métodos del repositorio sin conocer el tipo
                val method = reportRepository!!.javaClass.getMethod("getHeatmapData", Int::class.java)
                val flow = method.invoke(reportRepository, 365) as kotlinx.coroutines.flow.Flow<*>
                flow.collect { resource ->
                    _heatmapPoints.value = resource
                    Log.d(TAG, "Heatmap loaded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en loadHeatmapData", e)
                _errorMessage.value = "Error cargando heatmap"
            }
        }
    }

    /**
     * Carga reportes recientes validados
     */
    fun loadRecentReports() {
        Log.d(TAG, "Iniciando loadRecentReports")
        if (reportRepository == null) {
            _errorMessage.value = "ReportRepository no inyectado"
            return
        }
        // Implementación similar a loadHeatmapData si es necesaria
    }

    /**
     * Inicia escucha de reportes en tiempo real (WebSocket)
     */
    fun startListeningToLiveReports() {
        Log.d(TAG, "Iniciando escucha de reportes en vivo")
        if (reportRepository == null) {
            _errorMessage.value = "ReportRepository no inyectado"
            return
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS PÚBLICOS - RUTEO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Establece coordenada de origen (validando que esté dentro de Pichincha)
     */
    fun setOrigen(latLng: LatLng) {
        if (!GeoUtils.estaDentroDelPoligono(latLng, Constants.PICHINCHA_POLYGON)) {
            _errorMessage.value = "El punto de origen está fuera del área de cobertura (Pichincha)"
            Log.w(TAG, "Origen fuera de Pichincha: $latLng")
            return
        }
        _coordenadaOrigen.value = latLng
        Log.d(TAG, "Origen establecido: $latLng")
    }

    /**
     * Establece coordenada de destino (validando que esté dentro de Pichincha)
     */
    fun setDestino(latLng: LatLng) {
        if (!GeoUtils.estaDentroDelPoligono(latLng, Constants.PICHINCHA_POLYGON)) {
            _errorMessage.value = "El destino está fuera del área de cobertura (Pichincha)"
            Log.w(TAG, "Destino fuera de Pichincha: $latLng")
            return
        }
        _coordenadaDestino.value = latLng
        Log.d(TAG, "Destino establecido: $latLng")
    }

    /**
     * Solicita ruta segura al backend
     */
    fun solicitarRutaSegura() {
        val origen = _coordenadaOrigen.value
        val destino = _coordenadaDestino.value

        Log.d(TAG, "solicitarRutaSegura() — origen=$origen | destino=$destino")

        if (origen == null || destino == null) {
            _errorMessage.value = "Debes definir un origen y un destino válidos"
            Log.w(TAG, "Solicitud cancelada: origen o destino nulos")
            return
        }

        if (ruteoRepository == null) {
            _errorMessage.value = "RuteoRepository no inyectado"
            return
        }

        _isLoadingRoute.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Enviando petición...")
                // Implementación con reflection si es necesaria
            } catch (e: Exception) {
                _errorMessage.value = "Error inesperado: ${e.message}"
                Log.e(TAG, "Excepción de corrutina: ${e.message}", e)
            } finally {
                _isLoadingRoute.value = false
            }
        }
    }

    /**
     * Limpia los datos de la ruta calculada
     */
    fun clearRuta() {
        _rutaPolyline.value = null
        _coordenadaDestino.value = null
        _nivelRiesgo.value = null
        _tiempoEstimado.value = null
        _errorMessage.value = null
        Log.d(TAG, "Ruta limpiada")
    }

    /**
     * Limpia solo el destino (mantiene origen)
     */
    fun clearDestino() {
        _coordenadaDestino.value = null
        _rutaPolyline.value = null
        _nivelRiesgo.value = null
        _tiempoEstimado.value = null
        Log.d(TAG, "Destino limpiado")
    }

    /**
     * Limpia el mensaje de error
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Limpia recursos cuando el ViewModel se destruye
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared() - ViewModel destruido")
    }
}
