package com.alertify.feature_reportes.data.network

import com.alertify.feature_reportes.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AlertifyApiService {

    @POST("reports")
    suspend fun createReport(@Body request: ReportRequest): Response<Map<String, Any>>

    @GET("reports/validated")
    suspend fun getValidatedReports(): Response<List<ReportResponse>>

    @GET("reports/user/{userId}")
    suspend fun getUserReports(@Path("userId") userId: Int): Response<List<ReportResponse>>

    @GET("reports/heatmap/data")
    suspend fun getHeatmapData(@Query("daysBack") daysBack: Int = 30): Response<HeatmapResponse>

    @GET("reports/heatmap/points")
    suspend fun getHeatmapPoints(@Query("daysBack") daysBack: Int = 30): Response<HeatmapResponse>
}