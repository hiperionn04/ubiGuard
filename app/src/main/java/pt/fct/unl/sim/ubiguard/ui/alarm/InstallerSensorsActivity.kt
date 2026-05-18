package pt.fct.unl.sim.ubiguard.ui.alarm

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import pt.fct.unl.sim.ubiguard.UbiGuardApp
import com.google.firebase.database.ValueEventListener
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.adapters.SensorAdapter
import pt.fct.unl.sim.ubiguard.models.SensorItem
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity
import kotlin.collections.get

class InstallerSensorsActivity : BaseActivity() {

    private val sensorList = mutableListOf<SensorItem>()
    private lateinit var sensorAdapter: SensorAdapter
    private var sensorListener: ValueEventListener? = null
    private var currentAlarmRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_installer_sensors)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        val etAlarmId = findViewById<EditText>(R.id.etAlarmId)
        val btnSearch = findViewById<AppCompatButton>(R.id.btnSearchSensors)
        val rvSensors = findViewById<RecyclerView>(R.id.rvSensors)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarSensors)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        rvSensors.layoutManager = LinearLayoutManager(this)
        sensorAdapter = SensorAdapter(sensorList)
        rvSensors.adapter = sensorAdapter

        val database = FirebaseDatabase.getInstance(UbiGuardApp.DATABASE_URL).reference

        btnSearch.setOnClickListener {
            val alarmId = etAlarmId.text.toString().trim()

            if (alarmId.isEmpty()) {
                Toast.makeText(this, getString(R.string.general_insert_id), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            rvSensors.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
            progressBar.visibility = View.VISIBLE

            sensorListener?.let { currentAlarmRef?.removeEventListener(it) }

            currentAlarmRef = database.child("alarms").child(alarmId).child("sensors")

            sensorListener = currentAlarmRef!!.addValueEventListener(object : ValueEventListener {

                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE

                    sensorList.clear()

                    if (!snapshot.exists()) {
                        tvEmptyState.text = getString(R.string.alarm_no_sensors)
                        tvEmptyState.visibility = View.VISIBLE
                        sensorAdapter.notifyDataSetChanged()
                        return
                    }

                    for (child in snapshot.children) {
                        val sensorName = child.key ?: continue
                        val value = child.value

                        if (value is Boolean) {
                            sensorList.add(SensorItem(sensorName, value, null))
                        } else if (value is Map<*, *>) {
                            val isActivated = value["activated"] as? Boolean ?: false
                            val lastRead = value["last_read"]?.toString()
                            sensorList.add(SensorItem(sensorName, isActivated, lastRead))
                        }
                    }

                    if (sensorList.isEmpty()) {
                        tvEmptyState.text = getString(R.string.general_no_data)
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        tvEmptyState.visibility = View.GONE
                        rvSensors.visibility = View.VISIBLE
                    }

                    sensorAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@InstallerSensorsActivity, getString(R.string.error_loading_sensors), Toast.LENGTH_SHORT).show()
                }
            })
        }
        val prefillId = intent.getStringExtra("ALARM_ID_PREFILL")
        if (prefillId != null) {
            etAlarmId.setText(prefillId)
            btnSearch.performClick()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorListener?.let { currentAlarmRef?.removeEventListener(it) }
    }
}