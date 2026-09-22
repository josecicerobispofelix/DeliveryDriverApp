package br.com.entregador.lucro.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.entregador.lucro.R
import br.com.entregador.lucro.domain.model.DeliveryHistoryRecord
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import java.util.Locale

class HistoryAdapter(
    private var records: List<DeliveryHistoryRecord> = emptyList()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    fun submitList(newList: List<DeliveryHistoryRecord>) {
        records = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPlatform: TextView = itemView.findViewById(R.id.tvHistoryPlatform)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvHistoryDateTime)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvHistoryStatusBadge)
        private val tvNetProfit: TextView = itemView.findViewById(R.id.tvHistoryNetProfit)
        private val tvGrossValue: TextView = itemView.findViewById(R.id.tvHistoryGrossValue)
        private val tvDistanceTime: TextView = itemView.findViewById(R.id.tvHistoryDistanceTime)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvHistoryDestination)

        fun bind(record: DeliveryHistoryRecord) {
            tvPlatform.text = record.platform.uppercase()
            when (record.platform.uppercase()) {
                "IFOOD" -> tvPlatform.setBackgroundColor(Color.parseColor("#EA1D2C"))
                "UBER" -> tvPlatform.setBackgroundColor(Color.parseColor("#0F172A"))
                "99" -> tvPlatform.setBackgroundColor(Color.parseColor("#FF8000"))
                else -> tvPlatform.setBackgroundColor(Color.parseColor("#4B5563"))
            }

            tvDateTime.text = "${record.dateStr} • ${record.timeStr}"

            when (record.trafficLightStatus) {
                TrafficLightStatus.GREEN -> {
                    tvStatusBadge.text = "🟢 Boa"
                    tvStatusBadge.setTextColor(Color.parseColor("#00E676"))
                    tvNetProfit.setTextColor(Color.parseColor("#00E676"))
                }
                TrafficLightStatus.YELLOW -> {
                    tvStatusBadge.text = "🟡 Atenção"
                    tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                    tvNetProfit.setTextColor(Color.parseColor("#F59E0B"))
                }
                TrafficLightStatus.RED -> {
                    tvStatusBadge.text = if (record.wasAutoRejected) "🔴 Recusada (Auto)" else "🔴 Recusada"
                    tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                    tvNetProfit.setTextColor(Color.parseColor("#EF4444"))
                }
            }

            tvNetProfit.text = String.format(Locale.getDefault(), "%s R$ %.2f", if (record.netProfit >= 0) "+" else "", record.netProfit)
            tvGrossValue.text = String.format(Locale.getDefault(), "(Bruto: R$ %.2f)", record.grossValue)
            tvDistanceTime.text = String.format(Locale.getDefault(), "%.1f km • %d min", record.totalDistanceKm, record.estimatedTimeMinutes)

            tvDestination.visibility = View.GONE
        }
    }
}
