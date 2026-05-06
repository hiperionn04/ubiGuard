package pt.fct.unl.sim.ubiguard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt

class SensorAdapter(private val sensorList: List<SensorItem>) : RecyclerView.Adapter<SensorAdapter.SensorViewHolder>() {

    class SensorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSensorName: TextView = itemView.findViewById(R.id.tvSensorName)
        val viewSensorStatus: View = itemView.findViewById(R.id.viewSensorStatus)
        val tvSensorStatus: TextView = itemView.findViewById(R.id.tvSensorStatus)
        val tvSensorRead: TextView = itemView.findViewById(R.id.tvSensorRead)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sensor, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = sensorList[position]

        holder.tvSensorName.text = sensor.name.replaceFirstChar { it.uppercase() }

        if (sensor.isActivated) {
            holder.tvSensorStatus.text = "Ativo"
            holder.tvSensorStatus.setTextColor("#00D0FF".toColorInt()) // Ciano
            holder.viewSensorStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                "#00D0FF".toColorInt())
        } else {
            holder.tvSensorStatus.text = "Inativo"
            holder.tvSensorStatus.setTextColor("#FF3B30".toColorInt()) // Vermelho
            holder.viewSensorStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                "#FF3B30".toColorInt())
        }

        if (sensor.lastRead != null) {
            holder.tvSensorRead.visibility = View.VISIBLE
            holder.tvSensorRead.text = "Última leitura: ${sensor.lastRead}"
        } else {
            holder.tvSensorRead.visibility = View.GONE
        }
    }

    override fun getItemCount() = sensorList.size
}