package pt.fct.unl.sim.ubiguard.ui.dashboard

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import android.os.Build
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.ui.alarm.UserAlarmDetailsActivity
import pt.fct.unl.sim.ubiguard.adapters.AlarmAdapter
import pt.fct.unl.sim.ubiguard.models.Alarm
import pt.fct.unl.sim.ubiguard.ui.auth.LoginActivity
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity

class MainActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val alarmList = mutableListOf<Alarm>()
    private lateinit var alarmAdapter: AlarmAdapter
    private var globalAlarmsListener: ValueEventListener? = null
    private var userAlarmsRef: DatabaseReference? = null
    private var globalAlarmsRef: DatabaseReference? = null
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userId = currentUser.uid

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        database.child("users").child(userId).child("account_type").get().addOnSuccessListener { snapshot ->
            val accountType = snapshot.getValue(String::class.java) ?: "User"
            setupDashboard(accountType, userId)
        }.addOnFailureListener {
            Toast.makeText(this, getString(R.string.error_loading_profile), Toast.LENGTH_SHORT).show()
            setupDashboard("User", userId)
        }

        startMonitorService()

        checkInitialPermissions()
    }

    private fun setupDashboard(accountType: String, userId: String) {
        val layoutUserDashboard = findViewById<RelativeLayout>(R.id.layoutUserDashboard)
        val layoutInstallerDashboard = findViewById<LinearLayout>(R.id.layoutInstallerDashboard)

        if (accountType == "Installer") {
            layoutUserDashboard.visibility = View.GONE
            layoutInstallerDashboard.visibility = View.VISIBLE

            val btnActivator = findViewById<AppCompatButton>(R.id.btnActivatorAlarm)
            val btnAssociate = findViewById<AppCompatButton>(R.id.btnAssociateAlarm)


            btnActivator.setOnClickListener { showActivatorAlarmDialog() }
            btnAssociate.setOnClickListener { showAssociateAlarmDialog() }

        } else {
            layoutUserDashboard.visibility = View.VISIBLE
            layoutInstallerDashboard.visibility = View.GONE

            val layoutEmptyState = findViewById<LinearLayout>(R.id.layoutEmptyState)
            val rvAlarms = findViewById<RecyclerView>(R.id.rvAlarms)

            rvAlarms.layoutManager = LinearLayoutManager(this)

            alarmAdapter = AlarmAdapter(alarmList) { alarmeClicado ->
                val intent = Intent(this, UserAlarmDetailsActivity::class.java)
                intent.putExtra("ALARM_ID", alarmeClicado.id)
                startActivity(intent)
            }
            rvAlarms.adapter = alarmAdapter

            userAlarmsRef = database.child("users").child(userId).child("alarms")
            globalAlarmsRef = database.child("alarms")

            userAlarmsRef?.addValueEventListener(object : ValueEventListener {
                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(userSnapshot: DataSnapshot) {
                    val myAlarmIds = mutableSetOf<String>()

                    for (child in userSnapshot.children) {
                        val alarmId = child.key ?: continue

                        val expiry = child.child("expiry").getValue(String::class.java)

                        if (isExpired(expiry)) {
                            child.ref.removeValue()

                            database.child("alarms").child(alarmId).child("access_list").child(userId).removeValue()
                        } else {
                            myAlarmIds.add(alarmId)
                        }
                    }

                    if (myAlarmIds.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                        rvAlarms.visibility = View.GONE
                        alarmList.clear()
                        alarmAdapter.notifyDataSetChanged()
                        return
                    }

                    layoutEmptyState.visibility = View.GONE
                    rvAlarms.visibility = View.VISIBLE

                    globalAlarmsListener?.let { globalAlarmsRef?.removeEventListener(it) }

                    globalAlarmsListener = globalAlarmsRef?.addValueEventListener(object :
                        ValueEventListener {
                        override fun onDataChange(alarmsSnapshot: DataSnapshot) {
                            alarmList.clear()
                            for (alarmData in alarmsSnapshot.children) {
                                val alarmId = alarmData.key ?: continue
                                if (myAlarmIds.contains(alarmId)) {
                                    val alarm = alarmData.getValue(Alarm::class.java)
                                    if (alarm != null) {
                                        alarmList.add(alarm.copy(id = alarmId))
                                    }
                                }
                            }
                            alarmAdapter.notifyDataSetChanged()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_loading_permissions), Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showActivatorAlarmDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val inputId = EditText(this)
        inputId.hint = getString(R.string.placeholder_alarm_id)

        val inputName = EditText(this)
        inputName.hint = getString(R.string.placeholder_name)

        val inputLocation = EditText(this)
        inputLocation.hint = getString(R.string.placeholder_location)

        layout.addView(inputId)
        layout.addView(inputName)
        layout.addView(inputLocation)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alarm_configurate_activate))
            .setView(layout)
            .setPositiveButton(getString(R.string.alarm_activate)) { _, _ ->
                val alarmId = inputId.text.toString().trim()
                val name = inputName.text.toString().trim()
                val location = inputLocation.text.toString().trim()

                if (alarmId.isNotEmpty() && name.isNotEmpty() && location.isNotEmpty()) {
                    val updates = mapOf<String, Any>(
                        "name" to name,
                        "location" to location,
                        "status" to "Desarmado",
                        "activated" to true
                    )

                    database.child("alarms").child(alarmId).updateChildren(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(R.string.alarm_activate_success), Toast.LENGTH_SHORT).show()
                            saveLocationFirebase(alarmId)
                        }
                        .addOnFailureListener { Toast.makeText(this, getString(R.string.alarm_activate_error), Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this, getString(R.string.general_fields), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.general_cancel), null)
            .show()
    }

    private fun showAssociateAlarmDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val inputId = EditText(this)
        inputId.hint = getString(R.string.placeholder_alarm_id)

        val inputEmail = EditText(this)
        inputEmail.hint = getString(R.string.placeholder_client_email)
        inputEmail.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        layout.addView(inputId)
        layout.addView(inputEmail)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.general_associate_alarme))
            .setView(layout)
            .setPositiveButton(R.string.general_associate) { _, _ ->
                val alarmId = inputId.text.toString().trim()
                val email = inputEmail.text.toString().trim()

                if (alarmId.isNotEmpty() && email.isNotEmpty()) {
                    database.child("users").orderByChild("email").equalTo(email)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.exists()) {
                                    for (userSnap in snapshot.children) {
                                        val targetUserId = userSnap.key ?: continue
                                        database.child("users").child(targetUserId).child("alarms").child(alarmId).setValue(true)
                                        database.child("alarms").child(alarmId).child("ownerId").setValue(targetUserId)
                                            .addOnSuccessListener {
                                                Toast.makeText(this@MainActivity, getString(R.string.alarm_created_associated), Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, getString(R.string.general_user_notfound), Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
            .setNegativeButton(getString(R.string.general_cancel), null)
            .show()
    }

    private fun startMonitorService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val serviceIntent = Intent(this, pt.fct.unl.sim.ubiguard.services.AlarmMonitorService::class.java)

        super.startForegroundService(serviceIntent)
    }

    private fun checkInitialPermissions() {
        val missingPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            missingPermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (missingPermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        } else {
            checkBackgroundPermission()
        }
    }

    private fun checkBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                AlertDialog.Builder(this)
                    .setTitle("Wi-Fi")
                    .setMessage("For UbiGuard to automatically disarm the alarm when you arrive home, we need you to set location access to 'Allow all the time'.")
                    .setPositiveButton("Configure") { _, _ ->
                        androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101)
                    }
                    .setNegativeButton("Not now", null)
                    .setCancelable(false)
                    .show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveLocationFirebase(alarmId: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            android.util.Log.d("UBIGUARD_SETUP", "A obter localização do Instalador...")

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude

                    android.util.Log.d("UBIGUARD_SETUP", "Localização apanhada: Lat $lat | Lng $lng")

                    database.child("alarms").child(alarmId).child("network").child("lat").setValue(lat)
                    database.child("alarms").child(alarmId).child("network").child("lng").setValue(lng)

                } else {
                    android.util.Log.e("UBIGUARD_SETUP", "Erro: GPS devolveu Null.")
                }
            }
        } else {
            Toast.makeText(this, "Permissão de GPS necessária para gravar a casa!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            checkInitialPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        globalAlarmsListener?.let { globalAlarmsRef?.removeEventListener(it) }
    }

    override fun onResume() {
        super.onResume()
        checkEmergencyNetwork()
    }

    private fun checkEmergencyNetwork() {
        try {
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wifiManager.connectionInfo
            val ssidAtual = info.ssid.replace("\"", "")

            if (ssidAtual == "UbiGuard_Emergencia") {
                Toast.makeText(this, getString(R.string.toast_emergency_wifi), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("UBIGUARD", "Erro ao ler Wi-Fi: ${e.message}")
        }
    }

}