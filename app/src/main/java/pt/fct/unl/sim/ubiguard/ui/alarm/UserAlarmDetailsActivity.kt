package pt.fct.unl.sim.ubiguard.ui.alarm

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import pt.fct.unl.sim.ubiguard.UbiGuardApp
import com.google.firebase.database.ValueEventListener
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.ui.user.UserLogsActivity
import pt.fct.unl.sim.ubiguard.adapters.AccessAdapter
import pt.fct.unl.sim.ubiguard.models.AccessItem
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity
import java.util.Calendar
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

class UserAlarmDetailsActivity : BaseActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var alarmId: String? = null
    private var currentStatus: String = "Desarmado"
    private lateinit var rvAccessList: RecyclerView
    private val accessList = mutableListOf<AccessItem>()
    private lateinit var accessAdapter: AccessAdapter
    private var accessListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_alarm_details)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance(UbiGuardApp.DATABASE_URL).reference
        alarmId = intent.getStringExtra("ALARM_ID")

        if (alarmId == null) {
            Toast.makeText(this, getString(R.string.error_alarm_not_specified), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        rvAccessList = findViewById(R.id.rvAccessList)
        rvAccessList.layoutManager = LinearLayoutManager(this)

        accessAdapter = AccessAdapter(accessList) { clickAccess ->
            removeAccess(clickAccess)
        }
        rvAccessList.adapter = accessAdapter

        loadAlarmDetails()

        findViewById<Button>(R.id.btnToggleAlarm).setOnClickListener { toggleAlarmStatus() }
        findViewById<TextView>(R.id.btnAddGuest).setOnClickListener { showAddGuestDialog() }

        findViewById<Button>(R.id.btnUserLogs).setOnClickListener {
            val intent = Intent(this, UserLogsActivity::class.java)
            intent.putExtra("ALARM_ID", alarmId)
            startActivity(intent)
        }

        findViewById<AppCompatButton>(R.id.btnChangePin).setOnClickListener {
            showDialogChangePin()
        }
    }

    private fun showDialogChangePin() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_pin, null)
        val etNewPin = dialogView.findViewById<EditText>(R.id.etNewPin)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialogView.findViewById<TextView>(R.id.btnCancelPin).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnConfirmPin).setOnClickListener {
            val newPin = etNewPin.text.toString().trim()

            if (newPin.length >= 4) {
                database.child("alarms").child(alarmId!!).child("pin").setValue(newPin)
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.msg_pin_updated), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
            } else {
                etNewPin.error = getString(R.string.msg_invalid_pin)
            }
        }

        dialog.show()
    }

    private fun loadAlarmDetails() {
        val currentUserUid = auth.currentUser?.uid ?: return

        database.child("alarms").child(alarmId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val name = snapshot.child("name").getValue(String::class.java) ?: getString(R.string.general_unknown)
                val address = snapshot.child("location").getValue(String::class.java) ?: getString(R.string.general_unknown_location)
                val status = snapshot.child("status").getValue(String::class.java) ?: getString(R.string.general_unknown_status)
                val ownerId = snapshot.child("ownerId").getValue(String::class.java)

                val isFired = snapshot.child("isFired").getValue(Boolean::class.java) ?: false

                currentStatus = status
                findViewById<TextView>(R.id.tvUserDetName).text = name
                findViewById<TextView>(R.id.tvUserDetAddress).text = address

                val tvStatus = findViewById<TextView>(R.id.tvUserDetStatus)
                val btnToggle = findViewById<Button>(R.id.btnToggleAlarm)

                tvStatus.text = status.uppercase()
                if (status == "Armado") {
                    tvStatus.setTextColor("#00D0FF".toColorInt())
                    btnToggle.text = getString(R.string.alarm_disarm)
                } else {
                    tvStatus.setTextColor("#7A8B99".toColorInt())
                    btnToggle.text = getString(R.string.alarm_arm)
                }

                val tvEmergency = findViewById<TextView>(R.id.tvUserDetEmergency)
                if (isFired) {
                    tvEmergency.text = getString(R.string.status_fired)
                    tvEmergency.setTextColor("#FF3B30".toColorInt()) // Vermelho
                } else {
                    tvEmergency.text = getString(R.string.status_safe)
                    tvEmergency.setTextColor("#00D0FF".toColorInt()) // Azul (ou podes usar verde "#34C759")
                }

                if (ownerId == currentUserUid) {
                    findViewById<View>(R.id.layoutOwnerOnly).visibility = View.VISIBLE
                    loadAccessList()
                } else {
                    findViewById<View>(R.id.layoutOwnerOnly).visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadAccessList() {

        if(accessListener != null) return

        accessListener = database.child("alarms").child(alarmId!!).child("access_list")
            .addValueEventListener(object : ValueEventListener {

                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(snapshot: DataSnapshot) {
                    accessList.clear()
                    for (child in snapshot.children) {
                        val expiry = child.child("expiry").getValue(String::class.java)

                        if (isExpired(expiry)) {
                            val targetUid = child.key!!
                            child.ref.removeValue()
                            database.child("users").child(targetUid).child("alarms").child(alarmId!!).removeValue()
                        } else {
                            val targetUid = child.key ?: continue
                            val email = child.child("email").getValue(String::class.java) ?: "..."
                            val isChild = child.child("isChild").getValue(Boolean::class.java) ?: false
                            accessList.add(AccessItem(targetUid, email, isChild, expiry))
                        }
                    }
                    accessAdapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun removeAccess(access: AccessItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvConfirmMessage)

        tvMessage.text = getString(R.string.alarm_remove_access_confirm, access.email)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialogView.findViewById<TextView>(R.id.btnCancelConfirm).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnConfirmAction).setOnClickListener {
            val targetUid = access.uid
            val aid = alarmId!!

            database.child("users").child(targetUid).child("alarms").child(aid).removeValue()
            database.child("alarms").child(aid).child("access_list").child(targetUid).removeValue()
                .addOnSuccessListener { Toast.makeText(this, getString(R.string.general_access_removed), Toast.LENGTH_SHORT).show() }

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toggleAlarmStatus() {
        val newStatus = if (currentStatus == "Armado") "Desarmado" else "Armado"

        val updates = mutableMapOf<String, Any>(
            "status" to newStatus
        )

        if (newStatus == "Desarmado") {
            updates["isFired"] = false
        }

        database.child("alarms").child(alarmId!!).updateChildren(updates)
    }

    @SuppressLint("DefaultLocale", "UseKtx")
    private fun showAddGuestDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_guest, null)
        val etEmail = dialogView.findViewById<EditText>(R.id.etGuestEmail)
        val rgAccessType = dialogView.findViewById<RadioGroup>(R.id.rgAccessType)
        val layoutExpiry = dialogView.findViewById<LinearLayout>(R.id.layoutExpiry)
        val btnPickDateTime = dialogView.findViewById<Button>(R.id.btnPickDateTime)

        var selectedExpiryDate: String? = null

        rgAccessType.setOnCheckedChangeListener { _, checkedId ->
            layoutExpiry.visibility = if (checkedId == R.id.rbGuest) View.VISIBLE else View.GONE
        }

        btnPickDateTime.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, R.style.CustomPickerTheme, { _, year, month, dayOfMonth ->
                TimePickerDialog(this, R.style.CustomPickerTheme, { _, hourOfDay, minute ->
                    selectedExpiryDate = String.format(
                        "%02d/%02d/%04d %02d:%02d",
                        dayOfMonth,
                        month + 1,
                        year,
                        hourOfDay,
                        minute
                    )
                    btnPickDateTime.text = selectedExpiryDate
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialogView.findViewById<TextView>(R.id.btnCancelAdd).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnConfirmAdd).setOnClickListener {
            val emailTarget = etEmail.text.toString().trim()
            val isChild = rgAccessType.checkedRadioButtonId == R.id.rbChild

            if (emailTarget.isEmpty()) {
                Toast.makeText(this, getString(R.string.general_force_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isChild && selectedExpiryDate == null) {
                Toast.makeText(this, getString(R.string.general_force_pickdate), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            associateUser(emailTarget, isChild, selectedExpiryDate)
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Associate User to the Alarm
     */
    private fun associateUser(emailTarget: String, isChild: Boolean, expiryDate: String?) {
        database.child("users").orderByChild("email").equalTo(emailTarget).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val targetUid = snapshot.children.first().key!!

                    val accessData = mapOf(
                        "email" to emailTarget,
                        "isChild" to isChild,
                        "expiry" to if(isChild) null else expiryDate
                    )

                    database.child("users").child(targetUid).child("alarms").child(alarmId!!).setValue(accessData)
                    database.child("alarms").child(alarmId!!).child("access_list").child(targetUid).setValue(accessData)
                        .addOnSuccessListener { Toast.makeText(this@UserAlarmDetailsActivity, getString(R.string.general_access_granted), Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this@UserAlarmDetailsActivity, getString(R.string.error_email), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        accessListener?.let { database.child("alarms").child(alarmId!!).child("access_list").removeEventListener(it) }
    }
}