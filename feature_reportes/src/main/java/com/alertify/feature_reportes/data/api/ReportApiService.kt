package com.alertify.feature_reportes.data.api

import com.alertify.feature_reportes.data.model.HeatmapResponse
import com.alertify.feature_reportes.data.model.ReportRequest
import com.alertify.feature_reportes.data.model.ReportResponse
import com.alertify.feature_reportes.data.model.UpdateFcmTokenRequest
import com.alertify.feature_reportes.data.model.UpdateLocationRequest
import retrofit2.Response
import retrofit2.http.*

interface ReportApiService {

    @POST("reports")
    suspend fun createReport(@Body request: ReportRequest): Response<Map<String, Any>>

    @GET("reports/validated")
    suspend fun getValidatedReports(): Response<List<ReportResponse>>

    @GET("reports/heatmap/data")
    suspend fun getHeatmapData(@Query("days") days: Int): Response<HeatmapResponse>

    @GET("reports/user/{userId}")
    suspend fun getUserReports(@Path("userId") userId: Int): Response<List<ReportResponse>>

    // ─── Sprint 4: Endpoints de Notificaciones Push ────────────────────────────

    /**
     * Registra el token FCM del dispositivo en el backend.
     * Llamar en: FirebaseMessagingService.onNewToken() + MainActivity.onStart()
     */
    @PATCH("users/{id}/fcm-token")
    suspend fun updateFcmToken(
        @Path("id") userId: Int,
        @Body body: UpdateFcmTokenRequest
    ): Response<Unit>

    /**
     * Actualiza la última ubicación GPS conocida del usuario.
     * El backend usa STDistance con esta coordenada para decidir
     * a qué usuarios enviar la alerta push de un incidente validado.
     * Llamar en: MainActivity.onStart() y MainActivity.onResume()
     */
    @PATCH("users/{id}/location")
    suspend fun updateLocation(
        @Path("id") userId: Int,
        @Body body: UpdateLocationRequest
    ): Response<Unit>
}
