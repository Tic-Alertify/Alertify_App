package com.alertify.feature_reportes.data.repository

import android.util.Log
import com.alertify.core.network.socket.SocketManager
import com.alertify.feature_reportes.data.api.ReportApiService
import com.alertify.feature_reportes.data.listener.ReportEventListener
import com.alertify.feature_reportes.data.model.HeatmapPoint
import com.alertify.feature_reportes.data.model.ReportRequest
import com.alertify.feature_reportes.data.model.ReportResponse
import com.alertify.feature_reportes.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val api: ReportApiService,
    private val eventListener: ReportEventListener,
    private val socketManager: SocketManager
) {

    /**
     * Obtiene datos para heatmap (últimos N días)
     */
    fun getHeatmapData(days: Int): Flow<Resource<List<HeatmapPoint>>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getHeatmapData(days)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.points ?: emptyList()))
            } else {
                emit(Resource.Error("Error del servidor: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo heatmap data", e)
            emit(Resource.Error("Fallo de red: ${e.message}"))
        }
    }

    /**
     * Obtiene reportes validados recientes
     */
    fun getValidatedReports(): Flow<Resource<List<ReportResponse>>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getValidatedReports()
            if (response.isSuccessful) {
                emit(Resource.Success(response.body() ?: emptyList()))
            } else {
                emit(Resource.Error("Error al obtener reportes"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo reportes validados", e)
            emit(Resource.Error(e.message ?: "Error desconocido"))
        }
    }

    /**
     * Crea un nuevo reporte
     */
    fun createReport(request: ReportRequest): Flow<Resource<Map<String, Any>>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.createReport(request)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body() ?: emptyMap<String, Any>()))
            } else {
                emit(Resource.Error("Error al reportar: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creando reporte", e)
            emit(Resource.Error("Verifica tu conexión a internet"))
        }
    }.catch { e ->
        Log.e(TAG, "Excepción en createReport", e)
        emit(Resource.Error("Error inesperado"))
    }


    /**
     * Escucha reportes en vivo desde Socket.io
     */
    fun listenToLiveReports(): Flow<ReportResponse> {
        return eventListener.transformEvents(socketManager.on("new-report"))
    }

    /**
     * Inicializa la conexión a Socket.io
     */
    fun initSocket() {
        socketManager.connect()
        Log.d(TAG, "Socket inicializado")
    }

    /**
     * Desconecta de Socket.io (cleanup)
     */
    fun closeSocket() {
        socketManager.disconnect()
        Log.d(TAG, "Socket desconectado")
    }

    /**
     * Obtiene reportes de un usuario específico
     */
    fun getReportsByUser(userId: Int): Flow<Resource<List<ReportResponse>>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getUserReports(userId)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body() ?: emptyList()))
            } else {
                emit(Resource.Error("Error obteniendo historial"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo reportes del usuario", e)
            emit(Resource.Error("Fallo de conexión: ${e.message}"))
        }
    }

    companion object {
        private const val TAG = "ReportRepository"
    }
}