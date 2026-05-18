package pt.fct.unl.sim.ubiguard.ui.alarm

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
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
import pt.fct.unl.sim.ubiguard.adapters.AlarmAdapter
import pt.fct.unl.sim.ubiguard.models.Alarm
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity

class InstallerAlarmsActivity : BaseActivity() {

    private lateinit var database: DatabaseReference

    private val fullAlarmList = mutableListOf<Alarm>()

    private val displayedAlarmList = mutableListOf<Alarm>()

    private lateinit var alarmAdapter: AlarmAdapter
    private var alarmsListener: ValueEventListener? = null

    private lateinit var etSearch: EditText
    private lateinit var rgStatusFilter: RadioGroup
    private lateinit var tvEmptyState: TextView
    private lateinit var rvAllAlarms: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_installer_alarms)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        database = FirebaseDatabase.getInstance(UbiGuardApp.DATABASE_URL).reference
        rvAllAlarms = findViewById(R.id.rvAllAlarms)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarAlarms)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        etSearch = findViewById(R.id.etSearch)
        rgStatusFilter = findViewById(R.id.rgStatusFilter)

        rvAllAlarms.layoutManager = LinearLayoutManager(this)

        alarmAdapter = AlarmAdapter(displayedAlarmList) { alarmClicked ->
            val intent = Intent(this, AlarmDetailsActivity::class.java)
            intent.putExtra("ALARM_ID", alarmClicked.id)
            startActivity(intent)
        }
        rvAllAlarms.adapter = alarmAdapter


        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }
        })

        rgStatusFilter.setOnCheckedChangeListener { _, _ ->
            applyFilters()
        }

        alarmsListener = database.child("alarms").addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE
                fullAlarmList.clear()

                for (alarmData in snapshot.children) {
                    val alarmId = alarmData.key ?: continue
                    val alarm = alarmData.getValue(Alarm::class.java)
                    if (alarm != null) {
                        fullAlarmList.add(alarm.copy(id = alarmId))
                    }
                }

                applyFilters()
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@InstallerAlarmsActivity, getString(R.string.error_loading_alarm), Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Apply filters on searching
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilters() {
        val searchText = etSearch.text.toString().trim().lowercase()
        val armedFilter = rgStatusFilter.checkedRadioButtonId == R.id.rbArmado
        val disarmedFilter = rgStatusFilter.checkedRadioButtonId == R.id.rbDesarmado

        displayedAlarmList.clear()

        for (alarm in fullAlarmList) {

            val alarmName = alarm.name
            val alarmLocation = alarm.location

            val textFilter = alarmName.lowercase().contains(searchText) ||
                    alarmLocation.lowercase().contains(searchText)

            val stateFilter = when {
                armedFilter -> alarm.status == "Armado"
                disarmedFilter -> alarm.status == "Desarmado"
                else -> true
            }

            if (textFilter && stateFilter) {
                displayedAlarmList.add(alarm)
            }
        }

        alarmAdapter.notifyDataSetChanged()

        if (displayedAlarmList.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvAllAlarms.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvAllAlarms.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmsListener?.let { database.child("alarms").removeEventListener(it) }
    }
}