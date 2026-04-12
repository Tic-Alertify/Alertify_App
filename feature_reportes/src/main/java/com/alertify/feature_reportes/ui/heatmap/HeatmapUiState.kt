package com.alertify.feature_reportes.ui.heatmap

import com.alertify.feature_reportes.data.model.HeatmapPoint
import com.alertify.feature_reportes.data.model.ReportResponse

data class HeatmapUiState(
    val isLoading: Boolean = false,
    val points: List<HeatmapPoint> = emptyList(),
    val recentReports: List<ReportResponse> = emptyList(),
    val error: String? = null
)