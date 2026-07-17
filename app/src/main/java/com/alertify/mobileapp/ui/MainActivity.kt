package com.alertify.mobileapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.alertify.core.session.SessionEvent
import com.alertify.core.session.SessionEventBus
import com.alertify.core.storage.AuthSessionManager
import com.alertify.feature_identidad.navigation.LoginNavigator
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.data.api.ReportApiService
import com.alertify.feature_reportes.data.model.EnsureUserRequest
import com.alertify.feature_reportes.data.model.UpdateFcmTokenRequest
import com.alertify.feature_reportes.data.model.UpdateLocationRequest
import com.alertify.feature_ruteo.viewmodel.MapViewModel
import com.alertify.mobileapp.AlertifyMessagingService
import com.alertify.mobileapp.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), LoginNavigator {
    @Inject
    lateinit var reportApiService: ReportApiService

    @Inject
    lateinit var authSessionManager: AuthSessionManager

    /**
     * MapViewModel compartido con DashboardFragment.
     * Se usa para pasar la coordenada del incidente al mapa cuando el
     * usuario toca una notificación push.
     * En Activity se usa viewModels() (en Fragment sería activityViewModels()).
     */
    private val mapViewModel: MapViewModel by viewModels()

    // Lanzador de permiso POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d(TAG, "Permiso POST_NOTIFICATIONS concedido")
                registerFcmTokenInBackend()
            } else {
                Log.w(TAG, "Permiso POST_NOTIFICATIONS denegado — push desactivados")
                Toast.makeText(
                    this,
                    "Activa las notificaciones para recibir alertas de seguridad",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionEventBus.events.collect { event ->
                    handleSessionEvent(event)
                }
            }
        }

        syncAuthenticatedUserFromSession()

        //  Manejar intent de notificación (usuario tocó una alerta push)
        handleAlertIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Sprint 4: Enviar ubicación y token al backend cada vez que la app es visible
        // Esto es la estrategia eficiente: sin polling en background, solo al abrir la app.
        // El backend usa la ubicación almacenada para STDistance en tiempo de alerta.
        syncDeviceWithBackend()
    }

    override fun onResume() {
        super.onResume()
        // Sprint 4: Re-sincronizar al volver al primer plano (ej: después de otra app)
        syncDeviceWithBackend()
    }

    override fun onLoginSuccess() {
        syncAuthenticatedUserFromSession()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController
        if (navController.currentDestination?.id != R.id.dashboardHostFragment) {
            navController.navigate(R.id.action_loginFragment_to_dashboardHostFragment)
        }
    }

    // ─── Sprint 4: Sincronización con el backend ──────────────────────────────

    /**
     * Orquesta el envío de token FCM y ubicación GPS al backend.
     * Se ejecuta en onStart() y onResume() para mantener datos frescos.
     */
    private fun syncDeviceWithBackend() {
        val userId = ConfigManager.currentUserId
        if (userId <= 0) return // No hay usuario autenticado aún

        requestNotificationPermissionIfNeeded()
        updateLocationInBackend(userId)
    }

    private fun syncAuthenticatedUserFromSession() {
        lifecycleScope.launch {
            val userId = authSessionManager.getCurrentUserId() ?: authSessionManager.getCurrentUserIdSync()
            ConfigManager.setCurrentUserId(userId)

            if (userId != null && userId > 0) {
                ensureUserExistsInReports(userId)
            }
        }
    }

    private fun ensureUserExistsInReports(userId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = reportApiService.ensureUser(EnsureUserRequest(userId))
                if (response.isSuccessful) {
                    Log.d(TAG, "Usuario sincronizado en reportes: $userId")
                } else {
                    Log.w(TAG, "No se pudo asegurar el usuario en reportes. Código: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo sincronizar el usuario en reportes; se usará el id local", e)
            }
        }
    }

    /**
     * Solicita el permiso POST_NOTIFICATIONS en Android 13+.
     * Si ya está concedido, registra el token FCM directamente.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permiso ya concedido → registrar token
                    registerFcmTokenInBackend()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // El usuario ya denegó antes → mostrar explicación
                    Toast.makeText(
                        this,
                        "Necesitamos permiso de notificaciones para alertarte de incidentes cercanos",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Primera vez que se pide el permiso
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android < 13: el permiso no es necesario en runtime
            registerFcmTokenInBackend()
        }
    }

    /**
     * Obtiene el token FCM actual y lo envía al backend.
     * Firebase rota tokens automáticamente; onNewToken() en AlertifyMessagingService
     * lo actualiza cuando hay rotación, pero aquí garantizamos la sincronización al abrir la app.
     */
    private fun registerFcmTokenInBackend() {
        val userId = ConfigManager.currentUserId
        if (userId <= 0) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                reportApiService.updateFcmToken(userId, UpdateFcmTokenRequest(token))
                Log.d(TAG, "Token FCM sincronizado con backend (userId=$userId)")
                Log.d(TAG, "Token FCM: $token")
            } catch (e: Exception) {
                Log.e(TAG, "Error sincronizando token FCM", e)
            }
        }
    }

    /**
     * Obtiene la última ubicación GPS conocida y la envía al backend.
     *
     * Estrategia de eficiencia:
     *   - Usa lastLocation (sin iniciar nueva sesión GPS → consume mínima batería)
     *   - Si no hay lastLocation (GPS nunca activado), omite el envío silenciosamente
     *   - El backend guardará esta ubicación en USUARIOS.UbicacionActual (geography)
     *   - Cuando se valide un incidente, STDistance comparará esa coordenada
     */
    private fun updateLocationInBackend(userId: Int) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permiso de ubicación no concedido — ubicación no enviada al backend")
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)

        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location == null) {
                    Log.w(TAG, "Ubicación GPS no disponible aún")
                    return@addOnSuccessListener
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        reportApiService.updateLocation(
                            userId,
                            UpdateLocationRequest(location.latitude, location.longitude)
                        )
                        Log.d(TAG, "Ubicación enviada: [${location.latitude}, ${location.longitude}]")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enviando ubicación al backend", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error obteniendo ubicación GPS", e)
            }
    }

    /**
     * Sprint 4: Maneja el Intent cuando el usuario toca una notificación de alerta.
     *
     * Flujo:
     *   1. Extrae lat/lon y reportId del Intent (puestos por AlertifyMessagingService)
     *   2. Escribe la coordenada en MapViewModel (compartido con DashboardFragment)
     *   3. Si no está en el dashboard, navega allí (el Fragment observará y centrará el mapa)
     */
    private fun handleAlertIntent(intent: android.content.Intent?) {
        intent ?: return
        val lat      = intent.getDoubleExtra(AlertifyMessagingService.EXTRA_ALERT_LATITUDE, 0.0)
        val lon      = intent.getDoubleExtra(AlertifyMessagingService.EXTRA_ALERT_LONGITUDE, 0.0)
        val reportId = intent.getIntExtra(AlertifyMessagingService.EXTRA_REPORT_ID, 0)

        if (reportId <= 0 || lat == 0.0 || lon == 0.0) return

        Log.d(TAG, "Alerta push: reporte #$reportId en [$lat, $lon]")

        // 1. Publicar la coordenada en el ViewModel compartido.
        //    DashboardFragment observa alertLocation y centra el mapa al recibirla.
        mapViewModel.setAlertLocation(lat, lon)

        // 2. Navegar al dashboard si no está ya visible
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment
        val navController = navHostFragment?.navController ?: return

        if (navController.currentDestination?.id != R.id.dashboardHostFragment) {
            navController.navigate(R.id.action_loginFragment_to_dashboardHostFragment)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun handleSessionEvent(event: SessionEvent) {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        when (event) {
            is SessionEvent.SessionExpired -> {
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_LONG).show()
                    navController.navigate(R.id.action_dashboardHostFragment_to_loginFragment)
                }
            }

            is SessionEvent.LogoutSuccess -> {
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
                    navController.navigate(R.id.action_dashboardHostFragment_to_loginFragment)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
