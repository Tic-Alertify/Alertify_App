package com.alertify.feature_reportes.utils

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.alertify.feature_reportes.R
import com.alertify.feature_reportes.data.model.ReportResponse
import com.alertify.feature_reportes.data.model.HeatmapPoint
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

object SharedMapDrawer {
    private val currentMarkers = mutableMapOf<Int, Marker>()

    private fun getCustomMarkerBitmap(context: Context, resourceId: Int, isMine: Boolean): BitmapDescriptor {
        val size = 130
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = if (isMine) Color.parseColor("#2196F3") else Color.parseColor("#E53935")
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Borde blanco eliminado para mantener el pin limpio y con fondo transparente respecto al mapa
        val iconDrawable = ContextCompat.getDrawable(context, resourceId)
        iconDrawable?.let {
            val margin = 32
            it.setBounds(margin, margin, size - margin, size - margin)
            it.draw(canvas)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun setupCustomInfoWindow(mMap: GoogleMap, context: Context) {
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null // Usa el marco por defecto pero contenido custom

            override fun getInfoContents(marker: Marker): View {
                val view = LayoutInflater.from(context).inflate(R.layout.layout_info_window, null)
                view.findViewById<TextView>(R.id.tvInfoTitle).text = marker.title
                view.findViewById<TextView>(R.id.tvInfoSnippet).text = marker.snippet
                return view
            }
        })
    }

    fun drawMarkers(mMap: GoogleMap, reports: List<ReportResponse>, currentUserId: Int, context: Context) {
        setupCustomInfoWindow(mMap, context)
        val reportIds = reports.map { it.id }.toSet()
        currentMarkers.keys.filter { it !in reportIds }.forEach { id ->
            currentMarkers[id]?.remove()
            currentMarkers.remove(id)
        }

        reports.forEach { report ->
            if (report.latitude != 0.0 && report.longitude != 0.0 && !currentMarkers.containsKey(report.id)) {
                val isMine = report.userId == currentUserId
                val iconRes = when (report.incidentType?.lowercase()) {
                    "robo" -> R.drawable.ic_robbery
                    "asalto" -> R.drawable.ic_assault
                    else -> R.drawable.ic_alert
                }

                val marker = mMap.addMarker(MarkerOptions()
                    .position(LatLng(report.latitude, report.longitude))
                    .title("${if(isMine) "Mi Reporte: " else ""}${report.incidentType}")
                    .snippet(report.description)
                    .icon(getCustomMarkerBitmap(context, iconRes, isMine))
                    .anchor(0.5f, 0.5f)
                )
                marker?.let { currentMarkers[report.id] = it }
            }
        }
    }

    fun drawHeatmap(mMap: GoogleMap, points: List<HeatmapPoint>): TileOverlay? {
        if (points.isEmpty()) return null
        val weightedList = points.map { WeightedLatLng(LatLng(it.latitude, it.longitude), it.intensity.toDouble()) }
        val gradient = Gradient(
            intArrayOf(Color.GREEN, Color.YELLOW, Color.rgb(255, 152, 0), Color.RED),
            floatArrayOf(0.2f, 0.5f, 0.8f, 1.0f)
        )
        val provider = HeatmapTileProvider.Builder().weightedData(weightedList).gradient(gradient).radius(50).build()
        return mMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
    }
}