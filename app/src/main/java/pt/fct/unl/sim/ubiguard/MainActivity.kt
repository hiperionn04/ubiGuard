package pt.fct.unl.sim.ubiguard

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
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val alarmList = mutableListOf<Alarm>()
    private lateinit var alarmAdapter: AlarmAdapter

    private var globalAlarmsListener: ValueEventListener? = null
    private var userAlarmsRef: DatabaseReference? = null
    private var globalAlarmsRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

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
            Toast.makeText(this, "Erro ao carregar perfil.", Toast.LENGTH_SHORT).show()
            setupDashboard("User", userId)
        }
    }

    private fun setupDashboard(accountType: String, userId: String) {
        val layoutUserDashboard = findViewById<RelativeLayout>(R.id.layoutUserDashboard)
        val layoutInstallerDashboard = findViewById<LinearLayout>(R.id.layoutInstallerDashboard)

        if (accountType == "Installer") {
            layoutUserDashboard.visibility = View.GONE
            layoutInstallerDashboard.visibility = View.VISIBLE

            val btnActivar = findViewById<AppCompatButton>(R.id.btnActivarAlarme)
            val btnAssociar = findViewById<AppCompatButton>(R.id.btnAssociarAlarme)

            btnActivar.setOnClickListener { showActivarAlarmeDialog() }
            btnAssociar.setOnClickListener { showAssociarAlarmeDialog() }

        } else {
            layoutUserDashboard.visibility = View.VISIBLE
            layoutInstallerDashboard.visibility = View.GONE

            val layoutEmptyState = findViewById<LinearLayout>(R.id.layoutEmptyState)
            val rvAlarms = findViewById<RecyclerView>(R.id.rvAlarms)

            rvAlarms.layoutManager = LinearLayoutManager(this)

            // ==========================================
            // MUDANÇA 1: CLIQUE PARA A NOVA PÁGINA DO USER
            // ==========================================
            alarmAdapter = AlarmAdapter(alarmList) { alarmeClicado ->
                val intent = Intent(this, UserAlarmDetailsActivity::class.java)
                intent.putExtra("ALARM_ID", alarmeClicado.id)
                startActivity(intent)
            }
            rvAlarms.adapter = alarmAdapter

            userAlarmsRef = database.child("users").child(userId).child("alarms")
            globalAlarmsRef = database.child("alarms")

            userAlarmsRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(userSnapshot: DataSnapshot) {
                    val myAlarmIds = mutableSetOf<String>()

                    for (child in userSnapshot.children) {
                        val alarmId = child.key ?: continue

                        // Lemos a data de validade que guardámos (se existir)
                        val expiry = child.child("expiry").getValue(String::class.java)

                        if (isExpired(expiry)) {
                            // 1. LIMPEZA: O tempo passou! Apagamos da conta deste utilizador
                            child.ref.removeValue()

                            // 2. Apagamos também da lista do Alarme para o Dono não ter lixo
                            database.child("alarms").child(alarmId).child("access_list").child(userId).removeValue()
                        } else {
                            // VÁLIDO: O tempo ainda não passou (ou é Criança/Dono e não tem limite)
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

                    globalAlarmsListener = globalAlarmsRef?.addValueEventListener(object : ValueEventListener {
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
                    Toast.makeText(this@MainActivity, "Erro a carregar permissões.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showActivarAlarmeDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val inputId = EditText(this)
        inputId.hint = "ID do Alarme (Criado pelo ESP32)"

        val inputName = EditText(this)
        inputName.hint = "Nome (ex: Casa de Férias)"

        val inputLocation = EditText(this)
        inputLocation.hint = "Morada (ex: Rua Direita, 123)"

        layout.addView(inputId)
        layout.addView(inputName)
        layout.addView(inputLocation)

        AlertDialog.Builder(this)
            .setTitle("Configurar e Activar Alarme")
            .setView(layout)
            .setPositiveButton("Activar") { dialog, _ ->
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
                        .addOnSuccessListener { Toast.makeText(this, "Alarme ativado com sucesso!", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { Toast.makeText(this, "Erro ao ativar alarme.", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this, "Preenche todos os campos.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAssociarAlarmeDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val inputId = EditText(this)
        inputId.hint = "ID do Alarme"

        val inputEmail = EditText(this)
        inputEmail.hint = "Email do Cliente"
        inputEmail.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        layout.addView(inputId)
        layout.addView(inputEmail)

        AlertDialog.Builder(this)
            .setTitle("Associar Alarme")
            .setView(layout)
            .setPositiveButton("Associar") { dialog, _ ->
                val alarmId = inputId.text.toString().trim()
                val email = inputEmail.text.toString().trim()

                if (alarmId.isNotEmpty() && email.isNotEmpty()) {
                    // Pesquisa o utilizador pelo email
                    database.child("users").orderByChild("email").equalTo(email)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.exists()) {
                                    // A Firebase devolve uma lista de resultados (mesmo que seja só 1), iteramos
                                    for (userSnap in snapshot.children) {
                                        val targetUserId = userSnap.key ?: continue

                                        // ==========================================
                                        // MUDANÇA 2: DEFINIR PERMISSÃO E O OWNER_ID
                                        // ==========================================

                                        // 1. Dar permissão de acesso ao utilizador
                                        database.child("users").child(targetUserId).child("alarms").child(alarmId).setValue(true)

                                        // 2. Definir este utilizador como o DONO do alarme na pasta global
                                        database.child("alarms").child(alarmId).child("ownerId").setValue(targetUserId)
                                            .addOnSuccessListener {
                                                Toast.makeText(this@MainActivity, "Alarme associado e dono definido!", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Utilizador não encontrado.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        globalAlarmsListener?.let { globalAlarmsRef?.removeEventListener(it) }
    }
}