package com.alertify.feature_reportes.ui.heatmap

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.alertify.feature_reportes.R
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.databinding.FragmentHeatmapBinding
import com.alertify.feature_reportes.utils.SharedMapDrawer
import com.alertify.feature_reportes.viewmodel.HeatmapViewModel
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.TileOverlay
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HeatmapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHeatmapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private val viewModel: HeatmapViewModel by viewModels()
    private var mTileOverlay: TileOverlay? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHeatmapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupMapStyle()

        // Configuración inicial de cámara en Quito [cite: 502, 503]
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-0.1806, -78.4678), 12f))

        // Iniciamos la observación del Estado Único
        setupObservers()
        viewModel.fetchMapData()
    }

    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->

            // 1. Manejo de Loading (Asegúrate de tener el ID en el XML)
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            // 2. Dibujar Heatmap - Eliminamos la cita del código lógico
            if (state.points.isNotEmpty()) {
                mTileOverlay?.remove()
                mTileOverlay = SharedMapDrawer.drawHeatmap(mMap, state.points)
            }

            // 3. Dibujar Marcadores - Agregamos el context requerido
            if (state.recentReports.isNotEmpty()) {
                SharedMapDrawer.drawMarkers(
                    mMap,
                    state.recentReports,
                    ConfigManager.currentUserId,
                    requireContext()
                )
            }

            // 4. Manejo de Errores
            state.error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun setupMapStyle() {
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
            )
            if (!success) Log.e("MAP_DEBUG", "No se pudo aplicar el JSON de estilo")
        } catch (e: Exception) {
            Log.e("MAP_DEBUG", "Error de estilo: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}