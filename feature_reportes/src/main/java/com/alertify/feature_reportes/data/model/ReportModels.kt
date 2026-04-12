package com.alertify.feature_reportes.data.model

import com.google.gson.annotations.SerializedName

// 1. Sincronizado para el POST (Reporte nuevo)
data class ReportRequest(
    @SerializedName("userId") val userId: Int,
    @SerializedName("incidentTypeId") val incidentTypeId: Int,
    @SerializedName("description") val description: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)
// 2. Sincronizado con el GET /reports/validated (Logs de 6006 bytes)
data class ReportResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("description") val description: String,
    @SerializedName("incidentType") val incidentType: String,
    @SerializedName("trustScore") val trustScore: Double,
    @SerializedName("estado") val status: Int,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("createdAt") val createdAt: String?,
    // Nota: El backend debe enviar 'idUsuario' para que esto no sea 0
    @SerializedName("idUsuario") val userId: Int = 0
)

// 3. Sincronizado con el GET /reports/heatmap/data (Logs de 83613 bytes)
data class HeatmapResponse(
    @SerializedName("points") val points: List<HeatmapPoint>
)

data class HeatmapPoint(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("intensity") val intensity: Float
)

