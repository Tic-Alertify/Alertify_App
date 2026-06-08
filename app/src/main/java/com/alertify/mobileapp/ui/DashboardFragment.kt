package com.alertify.mobileapp.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.alertify.feature_reportes.viewmodel.MapViewModel as ReportesViewModel
import com.alertify.feature_reportes.utils.Resource
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
    private val reportesViewModel: ReportesViewModel by activityViewModels()

    private var origenMarker: Marker? = null
    private var destinoMarker: Marker? = null
    private var trackingUserMarker: Marker? = null
    private val rutaPolylines = mutableListOf<Polyline>()

    private val routeBaseColor = "#1E88E5".toColorInt()
    private val routeWidth = 16f

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

        // Observadores unificados
        observarIncidentesDelEquipo()
        observarRuteoEAnimacion()
        observarEstadoDeCarga()
    }

    private fun observarIncidentesDelEquipo() {
        // Cargar todos los datos del mapa (incidentes y heatmap)
        reportesViewModel.loadAllMapData(ConfigManager.currentUserId)

        // 1. Observar marcadores de incidentes
        reportesViewModel.allMarkersToDraw.observe(viewLifecycleOwner) { reports ->
            if (::googleMap.isInitialized) {
                // Dibujar incidentes usando el SharedMapDrawer oficial
                SharedMapDrawer.drawMarkers(
                    googleMap,
                    reports,
                    ConfigManager.currentUserId,
                    requireContext()
                )
            }
        }

        // 2. Observar puntos del heatmap
        reportesViewModel.heatmapPoints.observe(viewLifecycleOwner) { resource ->
            if (resource is Resource.Success && ::googleMap.isInitialized)
                // Dibujar heatmap usando el SharedMapDrawer oficial
                SharedMapDrawer.drawHeatmap(googleMap, resource.data ?: emptyList())
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

                    // Limpiar ruta anterior
                    rutaPolylines.forEach { it.remove() }
                    rutaPolylines.clear()

                    if (encodedPolyline.isNullOrBlank()) return@collect

                    try {
                        val puntos = PolyUtil.decode(encodedPolyline)
                        if (puntos.isEmpty()) return@collect

                        val boundsBuilder = LatLngBounds.Builder()
                        val maxPoints = 9500
                        var index = 0

                        while (index < puntos.size) {
                            val endExclusive = minOf(index + maxPoints, puntos.size)
                            val segment = ArrayList<LatLng>()
                            if (index != 0) segment.add(puntos[index - 1])
                            segment.addAll(puntos.subList(index, endExclusive))

                            val polyline = googleMap.addPolyline(
                                PolylineOptions().addAll(segment).width(routeWidth).color(routeBaseColor)
                                    .startCap(RoundCap()).endCap(RoundCap()).jointType(JointType.ROUND).geodesic(true).zIndex(2f)
                            )
                            rutaPolylines.add(polyline)
                            for (punto in segment) boundsBuilder.include(punto)
                            index = endExclusive
                        }

                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150))
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

    companion object {
        private val QUITO_LOCATION = LatLng(-0.210313, -78.488884)
    }
}
