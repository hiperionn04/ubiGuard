package pt.fct.unl.sim.ubiguard.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.models.SensorItem

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

        val context = holder.itemView.context

        if (sensor.isActivated) {
            holder.tvSensorStatus.text = context.getString(R.string.general_active)
            holder.tvSensorStatus.setTextColor("#00D0FF".toColorInt())
            holder.viewSensorStatus.backgroundTintList = ColorStateList.valueOf(
                "#00D0FF".toColorInt())
        } else {
            holder.tvSensorStatus.text = context.getString(R.string.general_inactive)
            holder.tvSensorStatus.setTextColor("#FF3B30".toColorInt())
            holder.viewSensorStatus.backgroundTintList = ColorStateList.valueOf(
                "#FF3B30".toColorInt())
        }

        if (sensor.lastRead != null) {
            holder.tvSensorRead.visibility = View.VISIBLE
            holder.tvSensorRead.text = context.getString(R.string.last_read, sensor.lastRead)
        } else {
            holder.tvSensorRead.visibility = View.GONE
        }
    }

    override fun getItemCount() = sensorList.size
}