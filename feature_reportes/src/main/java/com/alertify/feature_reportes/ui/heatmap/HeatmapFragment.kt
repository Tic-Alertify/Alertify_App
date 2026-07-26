package com.alertify.feature_reportes.ui.heatmap

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.alertify.core.ui.viewmodel.SharedMapViewModel
import com.alertify.feature_reportes.R
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.databinding.FragmentHeatmapBinding
import com.alertify.feature_reportes.utils.MapStyleManager
import com.alertify.feature_reportes.utils.SharedMapDrawer
import com.alertify.feature_reportes.viewmodel.HeatmapViewModel
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.TileOverlay
import com.google.maps.android.PolyUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HeatmapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHeatmapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private val heatmapViewModel: HeatmapViewModel by viewModels()
    private val sharedMapViewModel: SharedMapViewModel by activityViewModels()
    private var mTileOverlay: TileOverlay? = null
    
    // Estado de búsqueda de ruta
    private var isSelectingOrigen = false
    private var isMapClickActive = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHeatmapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        
        // Botón flotante para cambiar estilo del mapa
        binding.fabToggleMapStyle.setOnClickListener {
            MapStyleManager.toggleMapStyle(requireContext(), mMap)
            updateMapStyleIcon()
        }
        
        // Configurar listeners de ruteo
        setupRuteoListeners()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupMapStyle()

        // Configuración inicial de cámara en Quito
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-0.1806, -78.4678), 12f))

        // Listener para clicks en el mapa (seleccionar origen/destino)
        mMap.setOnMapClickListener { latLng ->
            if (isMapClickActive) {
                if (isSelectingOrigen) {
                    sharedMapViewModel.setOrigen(latLng)
                    binding.tvOrigen.text = "📍 ${latLng.latitude.format(4)}, ${latLng.longitude.format(4)}"
                    isSelectingOrigen = false
                } else {
                    sharedMapViewModel.setDestino(latLng)
                    binding.etDestino.setText("📌 ${latLng.latitude.format(4)}, ${latLng.longitude.format(4)}")
                }
                isMapClickActive = false
            }
        }

        // Iniciamos la observación del Estado Único
        setupObservers()
        heatmapViewModel.fetchMapData()
    }

    private fun setupRuteoListeners() {
        // Botón para solicitar ruta
        binding.btnSolicitarRuta.setOnClickListener {
            if (binding.etDestino.text.isNullOrEmpty()) {
                showError("Por favor, ingresa un destino")
                return@setOnClickListener
            }
            sharedMapViewModel.solicitarRutaSegura()
        }

        // Listener para cambiar origen (long press en map)
        binding.tvOrigen.setOnClickListener {
            Toast.makeText(context, "Toquea el mapa para establecer origen", Toast.LENGTH_SHORT).show()
            isSelectingOrigen = true
            isMapClickActive = true
        }

        // Limpiar input de destino
        binding.ivClearText.setOnClickListener {
            binding.etDestino.text.clear()
            sharedMapViewModel.clearDestino()
        }

        // Observar cambios en los campos de destino
        binding.etDestino.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etDestino.text.isNotEmpty()) {
                val destino = binding.etDestino.text.toString()
                // Aquí podrías integrar autocompletado con Places API
                Log.d("HeatmapFragment", "Destino ingresado: $destino")
            }
        }
    }

    private fun setupObservers() {
        // Observar datos del heatmap
        heatmapViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            
            // Actualizar contador global de reportes aprobados
            if (!state.isLoading) {
                binding.tvTotalReports.text = "Reportes aprobados globales: ${state.totalGlobalReports}"
            }

            if (state.points.isNotEmpty()) {
                mTileOverlay?.remove()
                mTileOverlay = SharedMapDrawer.drawHeatmap(mMap, state.points)
            }

            if (state.recentReports.isNotEmpty()) {
                SharedMapDrawer.drawMarkers(
                    mMap,
                    state.recentReports,
                    ConfigManager.currentUserId,
                    requireContext()
                )
            }

            state.error?.let {
                showError(it)
            }
        }

        // Observar cambios de error en SharedMapViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            sharedMapViewModel.errorMessage.collect { errorMsg ->
                errorMsg?.let { showError(it) }
            }
        }

        // Observar polyline de ruta
        viewLifecycleOwner.lifecycleScope.launch {
            sharedMapViewModel.rutaPolyline.collect { polyline ->
                if (!polyline.isNullOrEmpty()) {
                    drawRutaEnMapa(polyline)
                }
            }
        }

        // Observar nivel de riesgo
        viewLifecycleOwner.lifecycleScope.launch {
            sharedMapViewModel.nivelRiesgo.collect { nivelRiesgo ->
                nivelRiesgo?.let { Log.d("HeatmapFragment", "Nivel de riesgo: $it") }
            }
        }

        // Observar tiempo estimado
        viewLifecycleOwner.lifecycleScope.launch {
            sharedMapViewModel.tiempoEstimado.collect { tiempo ->
                tiempo?.let { 
                    Toast.makeText(context, "Tiempo estimado: $it minutos", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observar si está cargando
        viewLifecycleOwner.lifecycleScope.launch {
            sharedMapViewModel.isLoadingRoute.collect { isLoading ->
                if (_binding != null) {
                    binding.btnSolicitarRuta.isEnabled = !isLoading
                    binding.btnSolicitarRuta.text = 
                        if (isLoading) "Calculando..." else "Solicitar Ruta Segura"
                }
            }
        }
    }

    private fun drawRutaEnMapa(polylineString: String) {
        try {
            val points = PolyUtil.decode(polylineString)
            if (points.isNotEmpty()) {
                mMap.clear()
                
                val polylineOptions = PolylineOptions()
                    .addAll(points)
                    .color(android.graphics.Color.GREEN)
                    .width(8f)
                    .geodesic(true)
                
                mMap.addPolyline(polylineOptions)
                
                // Centrar cámara en la ruta
                val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder()
                points.forEach { bounds.include(it) }
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                
                Toast.makeText(context, "✅ Ruta calculada exitosamente", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("HeatmapFragment", "Error dibujando ruta: ${e.message}")
            showError("Error al mostrar la ruta")
        }
    }

    private fun showError(message: String) {
        if (_binding == null) return
        val errorView = binding.tvErrorMessage
        errorView.visibility = View.VISIBLE
        errorView.text = "⚠️ $message"
        errorView.postDelayed({
            if (_binding != null) {
                errorView.visibility = View.GONE
            }
        }, 3000)
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun setupMapStyle() {
        MapStyleManager.initMapStyle(requireContext(), mMap)
        updateMapStyleIcon()
    }

    private fun updateMapStyleIcon() {
        val isDark = MapStyleManager.isDarkMode(requireContext())
        binding.fabToggleMapStyle.setImageResource(
            if (isDark) R.drawable.ic_brightness_light else R.drawable.ic_brightness_dark
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}