package pt.fct.unl.sim.ubiguard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt

class AlarmAdapter(
    private val alarmList: List<Alarm>,
    private val onAlarmClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvAlarmName)
        val tvLocation: TextView = itemView.findViewById(R.id.tvAlarmLocation)
        val tvStatus: TextView = itemView.findViewById(R.id.tvAlarmStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm_widget, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarmList[position]
        holder.tvName.text = alarm.name
        holder.tvLocation.text = alarm.location

        if (alarm.status.lowercase() == "armado") {
            holder.tvStatus.text = "ARMADO"
            holder.tvStatus.setTextColor("#00D0FF".toColorInt())
        } else {
            holder.tvStatus.text = "DESARMADO"
            holder.tvStatus.setTextColor("#FF3B30".toColorInt())
        }

        holder.itemView.setOnClickListener {
            onAlarmClick(alarm)
        }
    }

    override fun getItemCount(): Int {
        return alarmList.size
    }
}