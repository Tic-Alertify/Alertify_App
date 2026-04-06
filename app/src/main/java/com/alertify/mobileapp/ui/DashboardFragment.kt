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
import com.alertify.feature_ruteo.viewmodel.MapViewModel
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

    // Hilt nos da EXACTAMENTE el mismo ViewModel que está usando tu RuteoFragment
    private val ruteoViewModel: MapViewModel by activityViewModels()

    private var origenMarker: Marker? = null
    private var destinoMarker: Marker? = null
    private val rutaPolylines = mutableListOf<Polyline>()

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
        googleMap.uiSettings.isMapToolbarEnabled = false

        // Movemos la cámara a la posición inicial (Quito)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ruteoViewModel.quitoLocation, ruteoViewModel.defaultZoom))

        // Empezamos a escuchar lo que hace el usuario en el buscador
        observarRuteo()
    }

    private fun observarRuteo() {
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

        // 3. Escuchar la Ruta Calculada (¡Aquí dibujamos la línea!)
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
                                PolylineOptions().addAll(segment).width(16f).color("#1E88E5".toColorInt())
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ruteoViewModel.isLoadingRoute.collect { isLoading ->
                    if (isLoading) {
                        layoutLoading.visibility = View.VISIBLE
                    } else {
                        layoutLoading.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun vectorToBitmapDescriptor(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}