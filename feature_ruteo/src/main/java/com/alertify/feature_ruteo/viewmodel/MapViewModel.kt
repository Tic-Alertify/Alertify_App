package com.alertify.feature_ruteo.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.alertify.feature_ruteo.data.repository.RuteoRepository
import com.alertify.core.utils.Constants
import com.alertify.core.utils.GeoUtils

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: RuteoRepository
) : ViewModel() {


    // 1. Configuración inicial del mapa (Quito centro)
    val quitoLocation = LatLng(-0.210313, -78.488884)
    val defaultZoom = 14f

    // 2. Coordenadas de origen y destino
    private val _coordenadaOrigen = MutableStateFlow<LatLng?>(null)
    val coordenadaOrigen: StateFlow<LatLng?> = _coordenadaOrigen.asStateFlow()

    private val _coordenadaDestino = MutableStateFlow<LatLng?>(null)
    val coordenadaDestino: StateFlow<LatLng?> = _coordenadaDestino.asStateFlow()

    // 3. Estado de carga
    private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute.asStateFlow()

    // 4. Geometría de la ruta calculada
    private val _rutaPolyline = MutableStateFlow<String?>(null)
    val rutaPolyline: StateFlow<String?> = _rutaPolyline.asStateFlow()

    // 5. Mensaje de error para mostrar en la UI (null = sin error)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Limpia el último error una vez que la UI lo consumió */
    fun clearError() {
        _errorMessage.value = null
    }

    // ─── Sprint 4: Navegación desde Notificación Push ─────────────────────────

    /**
     * Coordenada del incidente recibido por push.
     * MainActivity la escribe cuando el usuario toca una notificación de alerta.
     * DashboardFragment la observa para centrar el mapa automáticamente.
     *
     * Se usa null como "ya consumido" — el Fragment llama consumeAlertLocation()
     * tras centrar el mapa para que no se re-centre en cada recomposición.
     */
    private val _alertLocation = MutableStateFlow<LatLng?>(null)
    val alertLocation: StateFlow<LatLng?> = _alertLocation.asStateFlow()

    // Coordenada en vivo del usuario durante el viaje
    private val _currentTrackingLocation = MutableStateFlow<LatLng?>(null)
    val currentTrackingLocation: StateFlow<LatLng?> = _currentTrackingLocation.asStateFlow()

    // Estado para saber si el viaje/rastreo está encendido
    private val _isNavigationMode = MutableStateFlow(false)
    val isNavigationMode: StateFlow<Boolean> = _isNavigationMode.asStateFlow()

    // Rotación del usuario (Heading)
    private val _origenHeading = MutableStateFlow<Float?>(null)
    val origenHeading: StateFlow<Float?> = _origenHeading.asStateFlow()

    fun updateTrackingLocation(latLng: LatLng) {
        _currentTrackingLocation.value = latLng
    }

    fun setNavigationMode(active: Boolean) {
        _isNavigationMode.value = active
    }

    fun updateHeading(heading: Float) {
        _origenHeading.value = heading
    }


    /** Llamar desde MainActivity cuando llega un intent de alerta push */
    fun setAlertLocation(lat: Double, lon: Double) {
        _alertLocation.value = LatLng(lat, lon)
    }

    /** Llamar desde DashboardFragment tras centrar el mapa (consume el evento) */
    fun consumeAlertLocation() {
        _alertLocation.value = null
    }

    /** Resetea el destino para volver al flujo de búsqueda */
    fun clearDestino() {
        _coordenadaDestino.value = null
        _rutaPolyline.value = null
    }

    // ─── T-04: Validación de coordenadas dentro del área de Pichincha ───────

    fun setOrigen(latLng: LatLng) {
        // Usamos Constants desde el :core
        if (!GeoUtils.estaDentroDelPoligono(latLng, Constants.PICHINCHA_POLYGON)) {
            _errorMessage.value = "El punto de origen está fuera del área de cobertura (Pichincha)"
            Log.w("MapViewModel", "Origen fuera de Pichincha: $latLng")
            return
        }
        _coordenadaOrigen.value = latLng
    }

    fun setDestino(latLng: LatLng) {
        //  Usamos MapConstants desde el :core
        if (!GeoUtils.estaDentroDelPoligono(latLng, Constants.PICHINCHA_POLYGON)) {
            _errorMessage.value = "El destino está fuera del área de cobertura (Pichincha)"
            Log.w("MapViewModel", "Destino fuera de Pichincha: $latLng")
            return
        }
        _coordenadaDestino.value = latLng
    }

    // ─── Solicitud de ruta al backend ───────────────────────────────────────

    fun solicitarRutaSegura() {
        val origen = _coordenadaOrigen.value
        val destino = _coordenadaDestino.value

        Log.d("MapViewModel", "solicitarRutaSegura() — origen=$origen | destino=$destino")

        if (origen == null || destino == null) {
            _errorMessage.value = "Debes definir un origen y un destino válidos"
            Log.w("MapViewModel", "Solicitud cancelada: origen o destino nulos")
            return
        }

        Log.d("MapViewModel", "Enviando petición → (${origen.latitude}, ${origen.longitude}) → (${destino.latitude}, ${destino.longitude})")
        _isLoadingRoute.value = true

        viewModelScope.launch {
            try {

                val resultado = repository.obtenerRutaSegura(
                    origenLat  = origen.latitude,
                    origenLng  = origen.longitude,
                    destinoLat = destino.latitude,
                    destinoLng = destino.longitude
                )

                if (resultado.isSuccess) {
                    val ruteoData = resultado.getOrNull()!!
                    Log.d("MapViewModel", "Ruta OK — Tiempo: ${ruteoData.tiempoEstimado}, Riesgo: ${ruteoData.nivelRiesgo}")
                    _rutaPolyline.value = ruteoData.rutaGeometria
                } else {
                    val excepcion = resultado.exceptionOrNull()
                    _errorMessage.value = "Error al calcular la ruta. Intenta de nuevo."
                    Log.e("MapViewModel", "Fallo en el repositorio: ${excepcion?.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error inesperado en la aplicación."
                Log.e("MapViewModel", "Excepción de corrutina: ${e.message}")
            } finally {
                _isLoadingRoute.value = false
            }
        }
    }
}