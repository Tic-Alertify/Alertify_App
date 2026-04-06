package com.alertify.feature_ruteo.data.repository

import com.alertify.feature_ruteo.data.api.RuteoApiService
import com.alertify.feature_ruteo.data.model.RuteoRequest
import com.alertify.feature_ruteo.data.model.RuteoResponse
import javax.inject.Inject

// @Inject constructor para que Hilt le entregue el RuteoApiService listo para usar
class RuteoRepository @Inject constructor(
    private val apiService: RuteoApiService
) {
    suspend fun obtenerRutaSegura(
        origenLat: Double,
        origenLng: Double,
        destinoLat: Double,
        destinoLng: Double
    ): Result<RuteoResponse> {
        return try {
            val request = RuteoRequest(origenLat, origenLng, destinoLat, destinoLng)

            // 🔥 Usamos el apiService inyectado en lugar de RetrofitClient
            val response = apiService.calcularRutaSegura(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de red: ${e.message}"))
        }
    }
}