package com.alertify.feature_reportes.data.api

import com.alertify.feature_reportes.data.model.HeatmapResponse
import com.alertify.feature_reportes.data.model.ReportRequest
import com.alertify.feature_reportes.data.model.ReportResponse
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
}
