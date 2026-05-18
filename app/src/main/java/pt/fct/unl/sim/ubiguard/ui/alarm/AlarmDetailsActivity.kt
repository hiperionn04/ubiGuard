package pt.fct.unl.sim.ubiguard.ui.alarm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import pt.fct.unl.sim.ubiguard.UbiGuardApp
import com.google.firebase.database.ValueEventListener
import pt.fct.unl.sim.ubiguard.ui.alarm.InstallerLogsActivity
import pt.fct.unl.sim.ubiguard.ui.alarm.InstallerSensorsActivity
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity
import androidx.core.graphics.toColorInt

class AlarmDetailsActivity : BaseActivity() {

    private lateinit var database: DatabaseReference
    private var alarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_details)

        // Config for slider
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        // Receive the alarm id from the page that we come
        alarmId = intent.getStringExtra("ALARM_ID")
        if (alarmId == null) {
            finish()
            return
        }

        database = FirebaseDatabase.getInstance(UbiGuardApp.DATABASE_URL).reference

        loadAlarmData()
        findOwnerData()

        // Navigation buttons
        findViewById<AppCompatButton>(R.id.btnGoToSensors).setOnClickListener {
            val intent = Intent(this, InstallerSensorsActivity::class.java)
            intent.putExtra("ALARM_ID_PREFILL", alarmId)
            startActivity(intent)
        }

        findViewById<AppCompatButton>(R.id.btnGoToLogs).setOnClickListener {
            val intent = Intent(this, InstallerLogsActivity::class.java)
            intent.putExtra("ALARM_ID_PREFILL", alarmId)
            startActivity(intent)
        }
    }

    /**
     * Loads the data for the alarm id
     */
    private fun loadAlarmData() {
        database.child("alarms").child(alarmId!!).addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: getString(R.string.general_unknown)
                    val location = snapshot.child("location").getValue(String::class.java) ?: getString((R.string.general_unknown_location))
                    val status = snapshot.child("status").getValue(String::class.java) ?: getString(R.string.general_unknown_status)

                    findViewById<TextView>(R.id.tvAlarmTitle).text = name
                    findViewById<TextView>(R.id.tvDetLocation).text = location

                    val tvStatus = findViewById<TextView>(R.id.tvDetStatus)
                    tvStatus.text = status.uppercase()

                    if (status == "Armado") tvStatus.setTextColor("#00D0FF".toColorInt())
                    else tvStatus.setTextColor("#FF3B30".toColorInt())
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Search the owner from the alarm.
     */
    private fun findOwnerData() {
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
                findViewById<TextView>(R.id.tvOwnerName).text = getString(R.string.general_no_owner)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}