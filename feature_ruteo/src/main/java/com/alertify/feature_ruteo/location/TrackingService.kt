package com.alertify.feature_ruteo.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.alertify.feature_ruteo.R

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val CHANNEL_ID = "TrackingServiceChannel"

    // Callback que se dispara cada vez que el GPS detecta movimiento
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            locationResult.lastLocation?.let { location ->
                Log.d("TrackingService", "Ubicación actualizada: ${location.latitude}, ${location.longitude}")
                // Enviamos la ubicación al Singleton para que la UI reaccione
                LocationTracker.updateLocation(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Iniciamos el servicio en primer plano con una notificación in-matable
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alertify: Ruteo Seguro")
            .setContentText("Rastreo satelital activado. Protegiendo tu viaje.")
            .setSmallIcon(R.drawable.ruteo_ic_nav_user) // El ícono del carrito azul
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Prioridad alta para que Android no apague el GPS
            .setOngoing(true) //  Evita que el usuario la borre deslizando
            .build()

        startForeground(1, notification)

        // 2. Configuramos el motor de GPS (Sintaxis Clásica)
        val locationRequest = LocationRequest.create().apply {
            interval = 3000
            fastestInterval = 2000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        // 3. Encendemos el rastreo
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Apagamos la antena GPS cuando cerramos la ruta
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No usamos Binding en este caso
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Monitoreo de Ruta",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}