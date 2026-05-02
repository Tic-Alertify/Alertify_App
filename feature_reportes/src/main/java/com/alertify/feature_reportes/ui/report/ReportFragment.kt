package com.alertify.feature_reportes.ui.report

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.alertify.feature_reportes.R
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.databinding.FragmentReportBinding
import com.alertify.feature_reportes.utils.Resource
import com.alertify.feature_reportes.utils.SharedMapDrawer
import com.alertify.feature_reportes.viewmodel.ReportViewModel
import com.alertify.feature_reportes.viewmodel.MapViewModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import android.location.Location
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class ReportFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private val reportViewModel: ReportViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()
    private lateinit var mMap: GoogleMap
    private var draggableMarker: Marker? = null
    private var userActualLocation: LatLng? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) centerMapOnUser()
        else Toast.makeText(requireContext(), "Se requiere ubicación para reportar", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        setupUI()
        setupObservers()
        setupKeyboardActions()
    }

    private fun setupKeyboardActions() {
        binding.etDescription.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                hideKeyboard()
                binding.etDescription.clearFocus()
                true
            } else false
        }
        // Cerrar al tocar fuera del contenedor principal
        binding.root.setOnClickListener { 
            hideKeyboard() 
            binding.etDescription.clearFocus()
        }
    }

    private fun setupUI() {
        binding.btnSendReport.setOnClickListener {
            sendValidatedReport()
        }
        binding.fabMyLocation.setOnClickListener {
            centerMapOnUser()
        }
    }

    private fun setupObservers() {
        // Estado del envío del reporte
        reportViewModel.reportStatus.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> toggleLoading(true)
                is Resource.Success -> {
                    toggleLoading(false)
                    showFeedback("¡Reporte enviado exitosamente!", false)
                    binding.root.postDelayed({
                        if (isAdded) findNavController().popBackStack()
                    }, 2500)
                }
                is Resource.Error -> {
                    toggleLoading(false)
                    showFeedback(resource.message ?: "Error al enviar", true)
                }
            }
        }

        // ✅ CORRECCIÓN: Observamos allMarkersToDraw para ver todo el contexto de la ciudad
        mapViewModel.allMarkersToDraw.observe(viewLifecycleOwner) { reports ->
            if (::mMap.isInitialized) {
                SharedMapDrawer.drawMarkers(mMap, reports, ConfigManager.currentUserId, requireContext())
            }
        }

        // Observamos el Heatmap para ver las zonas calientes mientras reportamos
        mapViewModel.heatmapPoints.observe(viewLifecycleOwner) { resource ->
            if (resource is Resource.Success && ::mMap.isInitialized) {
                SharedMapDrawer.drawHeatmap(mMap, resource.data ?: emptyList())
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
        } catch (e: Exception) { }

        centerMapOnUser()

        // ✅ Pasamos el UserId para que cargue mis reportes antiguos también
        mapViewModel.loadAllMapData(ConfigManager.currentUserId)

        mMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) { hideKeyboard() }
            override fun onMarkerDrag(marker: Marker) {}
            override fun onMarkerDragEnd(marker: Marker) { validateMarkerDistance(marker) }
        })
        
        mMap.setOnMapClickListener {
            hideKeyboard()
            binding.etDescription.clearFocus()
        }
    }

    private fun validateMarkerDistance(marker: Marker) {
        userActualLocation?.let { start ->
            val distance = SphericalUtil.computeDistanceBetween(start, marker.position)
            if (distance > 10.0) {
                showFeedback("Máximo 10 metros de tu ubicación", true)
                marker.position = start // Regresa al centro
            }
        }
    }

    private fun centerMapOnUser() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            getUserLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val client = LocationServices.getFusedLocationProviderClient(requireActivity())
        client.lastLocation.addOnSuccessListener { loc: Location? ->
            loc?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                userActualLocation = latLng
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))

                // Marcador azul cian para el reporte ACTUAL que se está creando
                draggableMarker?.remove()
                draggableMarker = mMap.addMarker(MarkerOptions()
                    .position(latLng)
                    .draggable(true)
                    .title("Mueve el marcador al sitio exacto")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))

                // Círculo de validación
                mMap.addCircle(CircleOptions()
                    .center(latLng)
                    .radius(10.0)
                    .strokeColor(Color.BLUE)
                    .fillColor(0x220000FF.toInt()))
            }
        }
    }

    private fun sendValidatedReport() {
        val desc = binding.etDescription.text.toString().trim()

        val typeId = when (binding.chipGroupIncidentType.checkedChipId) {
            R.id.chipRobo -> 1
            R.id.chipAsalto -> 2
            R.id.chipSospechoso -> 3
            else -> -1
        }

        if (typeId == -1) {
            showFeedback("Selecciona la categoría del incidente", true)
            return
        }

        // ✅ VALIDACIONES PROFESIONALES
        if (desc.isEmpty()) {
            binding.etDescription.error = "Describe lo que sucede"
            return
        }

        if (desc.length > 100) {
            binding.etDescription.error = "Máximo 100 caracteres"
            return
        }

        reportViewModel.sendReport(
            userId = ConfigManager.currentUserId,
            typeId = typeId,
            desc = desc,
            lat = draggableMarker?.position?.latitude ?: 0.0,
            lon = draggableMarker?.position?.longitude ?: 0.0
        )
    }

    private fun toggleLoading(isLoading: Boolean) {
        binding.btnSendReport.isEnabled = !isLoading
        binding.btnSendReport.text = if (isLoading) "Enviando..." else "Enviar Reporte"
    }

    private fun showFeedback(msg: String, isError: Boolean) {
        binding.cardFeedback.visibility = View.VISIBLE
        binding.tvFeedbackMessage.text = msg
        binding.tvFeedbackMessage.setTextColor(Color.WHITE) // Siempre blanco por legibilidad

        if (isError) {
            binding.cardFeedback.setCardBackgroundColor(Color.parseColor("#D32F2F")) // Rojo material
        } else {
            binding.cardFeedback.setCardBackgroundColor(Color.parseColor("#213A66")) // Azul corporativo (Éxito/Neutral)
        }

        binding.cardFeedback.postDelayed({
            if (_binding != null) binding.cardFeedback.visibility = View.GONE
        }, 3500)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
        _binding = null
    }
}