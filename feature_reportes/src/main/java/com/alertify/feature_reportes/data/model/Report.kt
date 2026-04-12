package com.alertify.feature_reportes.data.model

import com.google.gson.annotations.SerializedName

data class Report(
    @SerializedName("id") val id: Int,
    @SerializedName("idTipoIncidente") val typeId: Int,
    @SerializedName("descripcion") val description: String,
    @SerializedName("latitud") val latitude: Double,
    @SerializedName("longitud") val longitude: Double,
    @SerializedName("fechaHoraOcurrencia") val timestamp: String,
    @SerializedName("estado") val status: Int, // 0: Pendiente, 1: Validado [cite: 564]
    @SerializedName("puntajeConfianza") val confidence: Double
)