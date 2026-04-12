package com.alertify.feature_reportes.ui.history

import android.transition.TransitionManager
import android.view.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.alertify.feature_reportes.R
import com.alertify.feature_reportes.data.model.ReportResponse
import com.alertify.feature_reportes.databinding.ItemReportBinding
import java.text.SimpleDateFormat
import java.util.*

class ReportHistoryAdapter(private var reports: List<ReportResponse>) :
    RecyclerView.Adapter<ReportHistoryAdapter.ReportViewHolder>() {

    class ReportViewHolder(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ReportViewHolder(ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        val context = holder.itemView.context

        holder.binding.apply {
            tvTitle.text = report.incidentType
            tvDescription.text = report.description
            tvDate.text = formatReportDate(report.createdAt)

            // Expansión
            tvDescription.maxLines = 2
            root.setOnClickListener {
                TransitionManager.beginDelayedTransition(root as ViewGroup)
                if (tvDescription.maxLines == 2) {
                    tvDescription.maxLines = Int.MAX_VALUE
                    ivExpandChevron.rotation = 180f
                } else {
                    tvDescription.maxLines = 2
                    ivExpandChevron.rotation = 0f
                }
            }

            val (statusText, colorRes) = when (report.status) {
                1 -> "VALIDADO" to R.color.status_validado
                0 -> "PENDIENTE" to R.color.status_pendiente
                else -> "RECHAZADO" to R.color.status_rechazado
            }
            tvStatusBadge.text = statusText
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(context, colorRes))
            imgIcon.setImageResource(if(report.incidentType?.lowercase() == "robo") R.drawable.ic_robbery else R.drawable.ic_alert)
        }
    }

    private fun formatReportDate(rawDate: String?): String {
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = sdfIn.parse(rawDate ?: "")
            val sdfOut = SimpleDateFormat("d MMM - HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
            date?.let { sdfOut.format(it) } ?: "Fecha N/A"
        } catch (e: Exception) { "Formato error" }
    }

    override fun getItemCount() = reports.size
    fun updateData(new: List<ReportResponse>) { reports = new; notifyDataSetChanged() }
}