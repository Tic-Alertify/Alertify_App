package com.alertify.core.network.dto

import com.google.gson.annotations.SerializedName

data class ErrorResponse(
    @SerializedName("statusCode") val statusCode: Int? = null,
    @SerializedName("message") val message: Any? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("details") val details: Any? = null
) {
    fun getDisplayMessage(): String {
        return when (message) {
            is String -> message
            is List<*> -> message.filterIsInstance<String>().joinToString("\n")
            else -> error ?: "Error desconocido"
        }
    }
}
