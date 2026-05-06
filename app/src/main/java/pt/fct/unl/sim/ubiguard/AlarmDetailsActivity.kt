package pt.fct.unl.sim.ubiguard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.database.*

class AlarmDetailsActivity : BaseActivity() {

    private lateinit var database: DatabaseReference
    private var alarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_details)

        // 1. Configurar Slider
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        // 2. Receber o ID do Alarme vindo da página anterior
        alarmId = intent.getStringExtra("ALARM_ID")
        if (alarmId == null) {
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference

        loadAlarmData()
        findOwnerData()

        // 3. Configurar botões de navegação
        findViewById<AppCompatButton>(R.id.btnGoToSensors).setOnClickListener {
            val intent = Intent(this, InstallerSensorsActivity::class.java)
            intent.putExtra("ALARM_ID_PREFILL", alarmId) // Podemos preencher o ID automaticamente!
            startActivity(intent)
        }

        findViewById<AppCompatButton>(R.id.btnGoToLogs).setOnClickListener {
            val intent = Intent(this, InstallerLogsActivity::class.java)
            intent.putExtra("ALARM_ID_PREFILL", alarmId)
            startActivity(intent)
        }
    }

    private fun loadAlarmData() {
        database.child("alarms").child(alarmId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "Alarme"
                    val location = snapshot.child("location").getValue(String::class.java) ?: "Sem morada"
                    val status = snapshot.child("status").getValue(String::class.java) ?: "Desconhecido"

                    findViewById<TextView>(R.id.tvAlarmTitle).text = name
                    findViewById<TextView>(R.id.tvDetLocation).text = location

                    val tvStatus = findViewById<TextView>(R.id.tvDetStatus)
                    tvStatus.text = status.uppercase()

                    // Mudar cor baseado no estado
                    if (status == "Armado") tvStatus.setTextColor(Color.parseColor("#00D0FF"))
                    else tvStatus.setTextColor(Color.parseColor("#FF3B30"))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun findOwnerData() {
        // Procurar na pasta de utilizadores quem tem este alarme associado
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnap in snapshot.children) {
                    if (userSnap.child("alarms").hasChild(alarmId!!)) {
                        val name = userSnap.child("name").getValue(String::class.java)
                        val email = userSnap.child("email").getValue(String::class.java)

                        findViewById<TextView>(R.id.tvOwnerName).text = name
                        findViewById<TextView>(R.id.tvOwnerEmail).text = email
                        return
                    }
                }
                findViewById<TextView>(R.id.tvOwnerName).text = "Sem proprietário"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}