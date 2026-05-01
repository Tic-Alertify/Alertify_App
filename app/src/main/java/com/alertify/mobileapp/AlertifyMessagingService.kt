package com.alertify.mobileapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.data.api.ReportApiService
import com.alertify.feature_reportes.data.model.SecurityAlertData
import com.alertify.feature_reportes.data.model.UpdateFcmTokenRequest
import com.alertify.mobileapp.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlertifyMessagingService — T15: Receptor de Notificaciones Push FCM
 *
 * Ubicación: módulo :app (no en feature_reportes) porque:
 *   - Es un componente de sistema Android (Service), pertenece a la app shell
 *   - Necesita acceso al contexto global y a la Activity principal
 *   - El módulo :app ya tiene la dependencia firebase-messaging-ktx
 *
 * Flujo que maneja:
 *   1. Backend valida un reporte (CS >= 0.60)
 *   2. Backend consulta STDistance → usuarios en 1km
 *   3. Firebase entrega push a este Service
 *   4. onMessageReceived() → notificación visual + sonora en el dispositivo
 *
 * Canal de notificación: "alertify_alerts" — IMPORTANCE_HIGH
 *   → Genera sonido, vibración y aparece como heads-up notification
 */
@AndroidEntryPoint
class AlertifyMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var reportApiService: ReportApiService

    // ─── Ciclo de vida FCM ────────────────────────────────────────────────────

    /**
     * Se ejecuta cuando Firebase genera o rota el token FCM del dispositivo.
     * Registra el nuevo token en el backend para mantenerlo actualizado.
     *
     * Cuándo ocurre:
     *   - Primera instalación de la app
     *   - Firebase invalida el token anterior (automático, cada varios meses)
     *   - El usuario borra datos de la app
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM recibido: ${token.take(20)}...")

        val userId = ConfigManager.currentUserId
        if (userId <= 0) {
            Log.w(TAG, "No hay usuario autenticado, token FCM no registrado en backend")
            return
        }

        // Enviar al backend en corrutina (no bloquea el hilo principal)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reportApiService.updateFcmToken(userId, UpdateFcmTokenRequest(token))
                Log.d(TAG, "Token FCM registrado en backend para usuario $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando token FCM en backend", e)
            }
        }
    }

    /**
     * Se ejecuta cuando llega una notificación push mientras la app está en primer plano.
     *
     * Nota: Si la app está en background/cerrada y el mensaje tiene "notification" payload,
     * Android la muestra automáticamente en la bandeja. Este método solo es necesario
     * para el primer plano y para procesar el payload "data" en todos los estados.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push recibido: from=${message.from}, data=${message.data}")

        val data = message.data

        // Solo procesamos alertas de seguridad de Alertify
        if (data["type"] != "SECURITY_ALERT") {
            Log.d(TAG, "Tipo de mensaje no reconocido: ${data["type"]}")
            return
        }

        // Extraer datos del incidente
        val alertData = try {
            SecurityAlertData(
                reportId      = data["reportId"]?.toInt() ?: 0,
                incidentTypeId = data["incidentTypeId"]?.toInt() ?: 0,
                latitude      = data["latitude"]?.toDouble() ?: 0.0,
                longitude     = data["longitude"]?.toDouble() ?: 0.0,
                trustScore    = data["trustScore"]?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando datos de la alerta FCM", e)
            return
        }

        // Obtener título y cuerpo (vienen del backend en el payload "notification")
        val title = message.notification?.title ?: "⚠️ Alerta de Seguridad"
        val body  = message.notification?.body  ?: "Incidente validado cerca de tu ubicación"

        showSecurityAlertNotification(title, body, alertData)
    }

    // ─── Construcción de la Notificación ─────────────────────────────────────

    /**
     * Muestra una notificación heads-up con sonido y vibración.
     *
     * Al tocarla, abre MainActivity pasando las coordenadas del incidente
     * para que el mapa se centre automáticamente en la zona de riesgo.
     */
    private fun showSecurityAlertNotification(
        title: String,
        body: String,
        alertData: SecurityAlertData
    ) {
        // Asegurar que el canal de notificación existe (requerido en Android 8+)
        createNotificationChannelIfNeeded()

        // Intent que se dispara al tocar la notificación → abre el mapa en el incidente
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Pasar coordenadas para que MainActivity centre el mapa
            putExtra(EXTRA_ALERT_LATITUDE,  alertData.latitude)
            putExtra(EXTRA_ALERT_LONGITUDE, alertData.longitude)
            putExtra(EXTRA_REPORT_ID,       alertData.reportId)
            putExtra(EXTRA_INCIDENT_TYPE_ID, alertData.incidentTypeId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            alertData.reportId, // requestCode único por reporte
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Usar ic_notification si tienes uno
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)                // Se descarta al tocarla
            .setContentIntent(pendingIntent)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            // Color de acento rojo para alertas de seguridad
            .setColor(0xFFE53935.toInt())
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                alertData.reportId, // ID único — evita apilar duplicados del mismo reporte
                notification
            )
            Log.d(TAG, "Notificación mostrada para reporte #${alertData.reportId}")
        } catch (e: SecurityException) {
            // El usuario no otorgó permiso POST_NOTIFICATIONS (Android 13+)
            Log.w(TAG, "Permiso de notificaciones no concedido: ${e.message}")
        }
    }

    /**
     * Crea el canal "alertify_alerts" si no existe.
     * Los canales son persistentes: solo se crea una vez aunque se llame varias veces.
     *
     * IMPORTANTE: El channelId debe coincidir con el configurado en el backend
     * (notifications.service.ts → channelId: 'alertify_alerts')
     */
    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Seguridad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de incidentes validados cercanos a tu ubicación"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                )
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AlertifyFCM"

        /** ID del canal — debe coincidir con backend notifications.service.ts */
        const val CHANNEL_ID = "alertify_alerts"

        /** Extras para el Intent al tocar la notificación */
        const val EXTRA_ALERT_LATITUDE   = "alert_latitude"
        const val EXTRA_ALERT_LONGITUDE  = "alert_longitude"
        const val EXTRA_REPORT_ID        = "report_id"
        const val EXTRA_INCIDENT_TYPE_ID = "incident_type_id"
    }
}
