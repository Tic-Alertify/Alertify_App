package com.alertify.feature_reportes.data.model

import com.google.gson.annotations.SerializedName

// ─── Sprint 4: Modelos para Notificaciones Push y Actualización de Dispositivo ───

/**
 * Payload para registrar/actualizar el token FCM del dispositivo.
 * Llamar a PATCH /users/:id/fcm-token
 */
data class UpdateFcmTokenRequest(
    @SerializedName("fcmToken") val fcmToken: String?
)

/**
 * Payload para actualizar la ubicación GPS del usuario en el backend.
 * Llamar a PATCH /users/:id/location al abrir la app (onStart/onResume).
 *
 * El backend usa esta ubicación para la consulta STDistance (T13):
 * si el usuario está dentro del radio de 1km de un incidente validado,
 * Firebase le enviará un push automáticamente.
 */
data class UpdateLocationRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

/**
 * Datos que vienen en el payload FCM cuando hay una alerta de seguridad.
 * Se extraen del RemoteMessage.data en AlertifyMessagingService.
 */
data class SecurityAlertData(
    val reportId: Int,
    val incidentTypeId: Int,
    val latitude: Double,
    val longitude: Double,
    val trustScore: Double
)
