package com.alertify.feature_reportes.ui.history

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.alertify.feature_reportes.R
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.databinding.FragmentHistoryBinding
import com.alertify.feature_reportes.utils.Resource
import com.alertify.feature_reportes.utils.SharedMapDrawer
import com.alertify.feature_reportes.viewmodel.MapViewModel
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoryFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private val viewModel: MapViewModel by viewModels()
    private lateinit var historyAdapter: ReportHistoryAdapter
    private var isListView = false
    private var mTileOverlay: TileOverlay? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        setupUI()
        setupRecyclerView()
        setupObservers()
    }

    private fun setupUI() {
        binding.btnToggleView.setOnClickListener { toggleView() }
    }

    private fun toggleView() {
        isListView = !isListView
        binding.listContainer.visibility = if (isListView) View.VISIBLE else View.GONE
        binding.mapContainer.visibility = if (isListView) View.GONE else View.VISIBLE
        binding.btnToggleView.text = if (isListView) "🗺️ Ver en Mapa" else "📋 Ver en Lista"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupMapStyle()
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-0.1806, -78.4678), 12f))

        // ✅ Trigger único: Carga heatmap, reportes de ciudad y los tuyos
        viewModel.loadAllMapData(ConfigManager.currentUserId)
    }

    private fun setupObservers() {
        // 1. ✅ OBSERVADOR DE LA LISTA (Solo tus reportes)
        // Usamos la lista limpia que viene del ViewModel para el RecyclerView
        viewModel.userReportsList.observe(viewLifecycleOwner) { reports ->
            binding.progressBar.visibility = View.GONE
            historyAdapter.updateData(reports)
        }

        // 2. ✅ OBSERVADOR DEL MAPA (Todo combinado)
        // Este se encarga de dibujar tus puntos azules y los rojos de los demás
        viewModel.allMarkersToDraw.observe(viewLifecycleOwner) { reports ->
            if (::mMap.isInitialized) {
                // No borramos todo el mapa para no quitar el heatmap, drawMarkers ya gestiona sus pins
                SharedMapDrawer.drawMarkers(mMap, reports, ConfigManager.currentUserId, requireContext())
            }
        }

        // 3. ✅ OBSERVADOR DEL HEATMAP
        viewModel.heatmapPoints.observe(viewLifecycleOwner) { resource ->
            // Aquí si manejamos el recurso para ver el estado de carga del mapa de calor
            if (resource is com.alertify.feature_reportes.utils.Resource.Success) {
                if (::mMap.isInitialized) {
                    mTileOverlay?.remove()
                    mTileOverlay = SharedMapDrawer.drawHeatmap(mMap, resource.data ?: emptyList())
                }
            }
        }
    }

    private fun setupMapStyle() {
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
        } catch (e: Exception) {
            Log.e("MAP_DEBUG", "Error de estilo: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = ReportHistoryAdapter(emptyList())
        binding.rvHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}