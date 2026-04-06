package com.alertify.feature_ruteo.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.alertify.feature_ruteo.R

class PlacesAdapter(
    private val clickListener: (AutocompletePrediction) -> Unit
) : RecyclerView.Adapter<PlacesAdapter.PlaceViewHolder>() {

    private var predictions: List<AutocompletePrediction> = listOf()

    // Función para actualizar la lista cuando el usuario escribe
    fun submitList(newPredictions: List<AutocompletePrediction>) {
        predictions = newPredictions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        // Usamos el layout con el prefijo "ruteo_"
        val view = LayoutInflater.from(parent.context).inflate(R.layout.ruteo_item_lugar_busqueda, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val prediction = predictions[position]
        // Muestra el texto completo sugerido por Google
        holder.tvName.text = prediction.getFullText(null).toString()

        holder.itemView.setOnClickListener { clickListener(prediction) }
    }

    override fun getItemCount() = predictions.size

    class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_nombre_lugar)
    }
}