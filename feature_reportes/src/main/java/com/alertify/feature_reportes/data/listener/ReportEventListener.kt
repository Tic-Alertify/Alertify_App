package com.alertify.feature_reportes.data.listener

import android.util.Log
import com.alertify.feature_reportes.data.model.ReportResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportEventListener @Inject constructor(
    private val gson: Gson
) {

    fun transformEvents(jsonFlow: Flow<JSONObject>): Flow<ReportResponse> {
        return jsonFlow.map { json ->
            try {
                gson.fromJson(json.toString(), ReportResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializando reporte: ${e.message}", e)

                ReportResponse(
                    id = -1,
                    description = "Error parsing report",
                    incidentType = "ERROR",
                    trustScore = 0.0,
                    status = -1,
                    latitude = 0.0,
                    longitude = 0.0,
                    createdAt = null,
                    userId = 0
                )
            }
        }
    }

    companion object {
        private const val TAG = "ReportEventListener"
    }
}
