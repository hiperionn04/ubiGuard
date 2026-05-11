package pt.fct.unl.sim.ubiguard.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.models.AccessItem

class AccessAdapter(
    private val accessList: List<AccessItem>,
    private val onRemoveClick: (AccessItem) -> Unit
) : RecyclerView.Adapter<AccessAdapter.AccessViewHolder>() {

    class AccessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAccessEmail: TextView = itemView.findViewById(R.id.tvAccessEmail)
        val tvAccessDetails: TextView = itemView.findViewById(R.id.tvAccessDetails)
        val btnRemoveAccess: ImageView = itemView.findViewById(R.id.btnRemoveAccess)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccessViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_access, parent, false)
        return AccessViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccessViewHolder, position: Int) {
        val item = accessList[position]

        holder.tvAccessEmail.text = item.email

        val context = holder.itemView.context

        if (item.isChild) {
            holder.tvAccessDetails.text = context.getString(R.string.accesss_child)
        } else {
            holder.tvAccessDetails.text = context.getString(R.string.access_guest, item.expiry)
        }

        holder.btnRemoveAccess.setOnClickListener {
            onRemoveClick(item)
        }
    }

    override fun getItemCount() = accessList.size
}