package pt.fct.unl.sim.ubiguard

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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class InstallerSensorsActivity : BaseActivity() {

    private val sensorList = mutableListOf<SensorItem>()
    private lateinit var sensorAdapter: SensorAdapter

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

        val database = FirebaseDatabase.getInstance().reference

        btnSearch.setOnClickListener {
            val alarmId = etAlarmId.text.toString().trim()

            if (alarmId.isEmpty()) {
                Toast.makeText(this, "Por favor insere um ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Iniciar pesquisa
            rvSensors.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            sensorList.clear()

            // Vai diretamente à pasta: alarms -> [ID] -> sensors
            database.child("alarms").child(alarmId).child("sensors")
                .addListenerForSingleValueEvent(object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {
                        progressBar.visibility = View.GONE

                        if (!snapshot.exists()) {
                            tvEmptyState.text = "Nenhum sensor encontrado para este Alarme."
                            tvEmptyState.visibility = View.VISIBLE
                            return
                        }

                        // LER OS DADOS DINÂMICOS
                        for (child in snapshot.children) {
                            val sensorName = child.key ?: continue
                            val value = child.value

                            if (value is Boolean) {
                                // CASO 1: É apenas um booleano (Ex: keypad: true)
                                sensorList.add(SensorItem(sensorName, value, null))

                            } else if (value is Map<*, *>) {
                                // CASO 2: É um objeto com várias propriedades (Ex: Temperature)
                                val isActivated = value["activated"] as? Boolean ?: false
                                val lastRead = value["last_read"]?.toString()

                                sensorList.add(SensorItem(sensorName, isActivated, lastRead))
                            }
                        }

                        if (sensorList.isEmpty()) {
                            tvEmptyState.text = "Sem dados visíveis."
                            tvEmptyState.visibility = View.VISIBLE
                        } else {
                            rvSensors.visibility = View.VISIBLE
                            sensorAdapter.notifyDataSetChanged()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@InstallerSensorsActivity, "Erro ao procurar sensores.", Toast.LENGTH_SHORT).show()
                    }
                })
        }
        val prefillId = intent.getStringExtra("ALARM_ID_PREFILL")
        if (prefillId != null) {
            etAlarmId.setText(prefillId)
            btnSearch.performClick()
        }
    }
}