package com.alertify.mobileapp.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alertify.feature_reportes.utils.SharedMapDrawer
import com.alertify.feature_ruteo.viewmodel.MapViewModel as RuteoViewModel
import com.alertify.feature_reportes.viewmodel.HeatmapViewModel
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.mobileapp.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard), OnMapReadyCallback {

    private lateinit var layoutLoading: View
    private lateinit var googleMap: GoogleMap

    // Hilt nos da los ViewModels necesarios para Ruteo y Reportes
    private val ruteoViewModel: RuteoViewModel by activityViewModels()
    private val heatmapViewModel: HeatmapViewModel by activityViewModels()

    private var origenMarker: Marker? = null
    private var destinoMarker: Marker? = null
    private var trackingUserMarker: Marker? = null
    private val rutaPolylines = mutableListOf<Polyline>()

    private val routeBaseColor = "#1E88E5".toColorInt()
    private val routeWidth = 16f

    private var rutaPuntos: List<LatLng> = emptyList()
    private var lastRouteIndex = 0
    private val routeProgressToleranceMeters = 60.0
    private val routeProgressMinIndexStep = 1
    private val routeProgressFallbackMaxDistanceMeters = 120f
    private val routeProgressSearchWindow = 300

    private var routeAnimator: ValueAnimator? = null
    private val routeFadeDurationMs = 350L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutLoading = view.findViewById(R.id.layout_loading)

        // Al poner un listener vacío, esta vista "se traga" todos los toques y no deja que pasen al mapa ni al buscador.
        layoutLoading.setOnClickListener {
            // Intencionalmente vacío. ¡No hacer nada!
        }

        // Inicializamos el mapa
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = false
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(QUITO_LOCATION, 12f))

        // Aplicar estilo de mapa oscuro
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), com.alertify.feature_reportes.R.raw.map_style)
            )
            if (!success) Log.e("DashboardFragment", "No se pudo aplicar el estilo de mapa")
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error aplicando estilo de mapa: ${e.message}")
        }

        // Observadores unificados
        observarIncidentesDelEquipo()
        observarRuteoEAnimacion()
        observarEstadoDeCarga()
    }

    private fun observarIncidentesDelEquipo() {
        // Cargar todos los datos del mapa via HeatmapViewModel (heatmap + marcadores + WebSocket)
        heatmapViewModel.fetchMapData()

        heatmapViewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (!::googleMap.isInitialized) return@observe

            // Actualizar contador de reportes globales
            val tvReports = view?.findViewById<TextView>(R.id.tv_total_reports)
            tvReports?.text = "Reportes aprobados globales: ${state.totalGlobalReports}"

            // Dibujar heatmap
            if (state.points.isNotEmpty()) {
                SharedMapDrawer.drawHeatmap(googleMap, state.points)
            }

            // Dibujar marcadores de incidentes recientes
            if (state.recentReports.isNotEmpty()) {
                SharedMapDrawer.drawMarkers(
                    googleMap,
                    state.recentReports,
                    ConfigManager.currentUserId,
                    requireContext()
                )
            }

            // Mostrar errores
            state.error?.let {
                Toast.makeText(requireContext(), "⚠️ $it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observarEstadoDeCarga() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.isLoadingRoute.collect { isLoading ->
                    layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun observarRuteoEAnimacion() {
        // 1. Escuchar Origen
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.coordenadaOrigen.collect { latLng ->
                    latLng ?: return@collect
                    if (!::googleMap.isInitialized) return@collect
                    origenMarker?.remove()

                    val icon = vectorToBitmapDescriptor(requireContext(), com.alertify.feature_ruteo.R.drawable.ruteo_ic_marker_start)
                    origenMarker = googleMap.addMarker(MarkerOptions().position(latLng).title("Mi ubicación").icon(icon))
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        }

        // 2. Escuchar Destino
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.coordenadaDestino.collect { latLng ->
                    latLng ?: return@collect
                    if (!::googleMap.isInitialized) return@collect
                    destinoMarker?.remove()

                    val icon = vectorToBitmapDescriptor(requireContext(), com.alertify.feature_ruteo.R.drawable.ruteo_ic_marker_destination)
                    destinoMarker = googleMap.addMarker(MarkerOptions().position(latLng).title("Destino").icon(icon))
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        }

        // 3. Escuchar la Ruta Calculada
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.rutaPolyline.collect { encodedPolyline ->
                    if (!::googleMap.isInitialized) return@collect

                    if (encodedPolyline.isNullOrBlank()) {
                        clearRutaPolyline()
                        return@collect
                    }

                    try {
                        val puntos = PolyUtil.decode(encodedPolyline)
                        if (puntos.isEmpty()) {
                            clearRutaPolyline()
                            return@collect
                        }

                        rutaPuntos = puntos
                        lastRouteIndex = 0

                        val (newPolylines, boundsBuilder) = buildRoutePolylines(puntos)
                        val bounds = boundsBuilder.build()
                        val padding = 150

                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                        replaceRutaPolyline(newPolylines)
                    } catch (ex: Exception) {
                        Log.e("DashboardFragment", "Error al dibujar ruta", ex)
                        Toast.makeText(requireContext(), "No se pudo dibujar la ruta.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 4. Escuchar si estamos en Modo Navegación
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.isNavigationMode.collect { active ->
                    if (!::googleMap.isInitialized) return@collect
                    if (!active) {
                        // Si se apaga la navegación, removemos el marcador de coche azul
                        trackingUserMarker?.remove()
                        trackingUserMarker = null
                    }
                }
            }
        }

        // 5. Escuchar Cambios de Ubicación GPS en Vivo (Animación de Coche Azul)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.currentTrackingLocation.collect { liveLatLng ->
                    liveLatLng ?: return@collect
                    if (!::googleMap.isInitialized) return@collect

                    // 🔥 CRÍTICO: Solo dibujar el coche y centrar si estamos navegando
                    if (!ruteoViewModel.isNavigationMode.value) return@collect

                    if (trackingUserMarker == null) {
                        // Crear el marcador del coche azul por primera vez
                        val icon = vectorToBitmapDescriptor(requireContext(), com.alertify.feature_ruteo.R.drawable.ruteo_ic_nav_user)
                        trackingUserMarker = googleMap.addMarker(
                            MarkerOptions()
                                .position(liveLatLng)
                                .anchor(0.5f, 0.5f)
                                .icon(icon)
                                .flat(true) // Permite rotarlo suavemente
                                .title("Navegando...")
                        )
                    } else {
                        // Animar la transición de la posición anterior a la nueva
                        animateMarker(trackingUserMarker!!, liveLatLng)
                    }

                    // Centrar la cámara suavemente en el coche
                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(liveLatLng))
                    updateRouteProgress(liveLatLng)
                }
            }
        }

        // 6. Escuchar Rotación (Heading) para orientar el coche
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.origenHeading.collect { heading ->
                    heading ?: return@collect
                    if (!::googleMap.isInitialized) return@collect
                    trackingUserMarker?.rotation = heading
                }
            }
        }

        // 7. Escuchar LocationTracker para actualizar el ViewModel de Ruteo
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.alertify.feature_ruteo.location.LocationTracker.currentLocation.collect { location ->
                    location ?: return@collect
                    val latLng = LatLng(location.latitude, location.longitude)
                    ruteoViewModel.updateTrackingLocation(latLng)
                    if (location.hasBearing()) {
                        ruteoViewModel.updateHeading(location.bearing)
                    }
                }
            }
        }

        // 8. Sprint 4: Navegar al incidente cuando el usuario toca una notificación push
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.alertLocation.collect { alertLatLng ->
                    alertLatLng ?: return@collect
                    if (!::googleMap.isInitialized) return@collect

                    // Centrar el mapa con zoom alto para ver el incidente
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(alertLatLng, 17f)
                    )

                    // Marcador rojo de alerta de seguridad
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(alertLatLng)
                            .title("⚠️ Alerta de Seguridad")
                            .snippet("Incidente validado cerca de tu ubicación")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )?.showInfoWindow()

                    // Consumir el evento para que no se repita al rotar pantalla
                    ruteoViewModel.consumeAlertLocation()

                    Log.d("DashboardFragment", "Mapa centrado en alerta push: $alertLatLng")
                }
            }
        }
    }

    private fun animateMarker(marker: Marker, toPosition: LatLng) {
        val startPosition = marker.position
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val start = android.os.SystemClock.uptimeMillis()
        val duration = 1000 // 1 segundo de animación

        val interpolator = android.view.animation.LinearInterpolator()

        handler.post(object : Runnable {
            override fun run() {
                val elapsed = android.os.SystemClock.uptimeMillis() - start
                val t = elapsed.toFloat() / duration
                val v = interpolator.getInterpolation(t)

                val lat = (toPosition.latitude - startPosition.latitude) * v + startPosition.latitude
                val lng = (toPosition.longitude - startPosition.longitude) * v + startPosition.longitude
                marker.position = LatLng(lat, lng)

                if (t < 1.0) {
                    // Sigue animando
                    handler.postDelayed(this, 16)
                }
            }
        })
    }

    private fun vectorToBitmapDescriptor(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clearRutaPolyline() {
        routeAnimator?.cancel()
        routeAnimator = null
        rutaPolylines.forEach { it.remove() }
        rutaPolylines.clear()
        rutaPuntos = emptyList()
        lastRouteIndex = 0
    }

    private fun buildRoutePolylines(puntos: List<LatLng>): Pair<List<Polyline>, LatLngBounds.Builder> {
        val boundsBuilder = LatLngBounds.Builder()
        val newPolylines = mutableListOf<Polyline>()

        val maxPointsPerPolyline = 9500
        var index = 0

        while (index < puntos.size) {
            val endExclusive = minOf(index + maxPointsPerPolyline, puntos.size)
            val segment = ArrayList<LatLng>()

            if (index != 0) segment.add(puntos[index - 1])
            segment.addAll(puntos.subList(index, endExclusive))

            val polyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(segment)
                    .width(routeWidth)
                    .color(colorWithAlpha(routeBaseColor, 0))
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .jointType(JointType.ROUND)
                    .geodesic(true)
                    .zIndex(2f)
            )
            newPolylines.add(polyline)

            for (punto in segment) boundsBuilder.include(punto)
            index = endExclusive
        }

        return newPolylines to boundsBuilder
    }

    private fun replaceRutaPolyline(newPolylines: List<Polyline>) {
        val oldPolylines = rutaPolylines.toList()
        routeAnimator?.cancel()

        if (oldPolylines.isEmpty()) {
            newPolylines.forEach { it.color = colorWithAlpha(routeBaseColor, 255) }
            rutaPolylines.clear()
            rutaPolylines.addAll(newPolylines)
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = routeFadeDurationMs
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val newAlpha = (fraction * 255).toInt()
                val oldAlpha = ((1f - fraction) * 255).toInt()

                newPolylines.forEach { it.color = colorWithAlpha(routeBaseColor, newAlpha) }
                oldPolylines.forEach { it.color = colorWithAlpha(routeBaseColor, oldAlpha) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    oldPolylines.forEach { it.remove() }
                }

                override fun onAnimationCancel(animation: Animator) {
                    oldPolylines.forEach { it.remove() }
                }
            })
        }

        routeAnimator = animator
        rutaPolylines.clear()
        rutaPolylines.addAll(newPolylines)
        animator.start()
    }

    private fun setRutaPolylineInstant(newPolylines: List<Polyline>) {
        routeAnimator?.cancel()
        rutaPolylines.forEach { it.remove() }
        rutaPolylines.clear()
        newPolylines.forEach { it.color = colorWithAlpha(routeBaseColor, 255) }
        rutaPolylines.addAll(newPolylines)
    }

    private fun updateRouteProgress(currentLatLng: LatLng) {
        if (rutaPuntos.size < 2) return
        if (!::googleMap.isInitialized) return

        var idx = PolyUtil.locationIndexOnPath(currentLatLng, rutaPuntos, false, routeProgressToleranceMeters)

        if (idx < 0) {
            idx = PolyUtil.locationIndexOnPath(currentLatLng, rutaPuntos, false, routeProgressToleranceMeters * 2)
        }

        if (idx < 0) {
            val (closestIndex, closestDistance) = findClosestRouteIndex(currentLatLng)
            if (closestIndex < 0 || closestDistance > routeProgressFallbackMaxDistanceMeters) return
            idx = closestIndex
        }

        if (idx <= lastRouteIndex) return
        if (idx - lastRouteIndex < routeProgressMinIndexStep && idx < rutaPuntos.lastIndex) return

        val remaining = rutaPuntos.subList(idx, rutaPuntos.size)
        if (remaining.size < 2) {
            clearRutaPolyline()
            return
        }

        val (newPolylines, _) = buildRoutePolylines(remaining)
        setRutaPolylineInstant(newPolylines)
        rutaPuntos = remaining
        lastRouteIndex = 0
    }

    private fun findClosestRouteIndex(currentLatLng: LatLng): Pair<Int, Float> {
        if (rutaPuntos.isEmpty()) return -1 to Float.MAX_VALUE

        val start = (lastRouteIndex - 10).coerceAtLeast(0)
        val end = (lastRouteIndex + routeProgressSearchWindow).coerceAtMost(rutaPuntos.lastIndex)

        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE

        for (i in start..end) {
            val distance = distanceMetersBetween(currentLatLng, rutaPuntos[i])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }

        return bestIndex to bestDistance
    }

    private fun distanceMetersBetween(a: LatLng, b: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0]
    }

    private fun colorWithAlpha(baseColor: Int, alpha: Int): Int {
        val safeAlpha = alpha.coerceIn(0, 255)
        return (baseColor and 0x00FFFFFF) or (safeAlpha shl 24)
    }

    companion object {
        private val QUITO_LOCATION = LatLng(-0.210313, -78.488884)
    }
}
