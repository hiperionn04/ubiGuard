package pt.fct.unl.sim.ubiguard

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class UserAlarmDetailsActivity : BaseActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var alarmId: String? = null
    private var currentStatus: String = "Desarmado"

    // VARIÁVEIS DA LISTA DE ACESSOS
    private lateinit var rvAccessList: RecyclerView
    private val accessList = mutableListOf<AccessItem>()
    private lateinit var accessAdapter: AccessAdapter
    private var accessListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_alarm_details)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        alarmId = intent.getStringExtra("ALARM_ID")

        if (alarmId == null) {
            Toast.makeText(this, "Erro: Alarme não especificado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        // Configurar a Lista de Acessos
        rvAccessList = findViewById(R.id.rvAccessList)
        rvAccessList.layoutManager = LinearLayoutManager(this)

        // Se clicar no lixo, chama a função para remover acesso
        accessAdapter = AccessAdapter(accessList) { acessoClicado ->
            removerAcesso(acessoClicado)
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
    }

    private fun loadAlarmDetails() {
        val currentUserUid = auth.currentUser?.uid ?: return

        database.child("alarms").child(alarmId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val name = snapshot.child("name").getValue(String::class.java) ?: "Desconhecido"
                val address = snapshot.child("location").getValue(String::class.java) ?: "Sem Morada"
                val status = snapshot.child("status").getValue(String::class.java) ?: "Desarmado"
                val ownerId = snapshot.child("ownerId").getValue(String::class.java)

                currentStatus = status
                findViewById<TextView>(R.id.tvUserDetName).text = name
                findViewById<TextView>(R.id.tvUserDetAddress).text = address

                val tvStatus = findViewById<TextView>(R.id.tvUserDetStatus)
                tvStatus.text = status.uppercase()

                if (status == "Armado") {
                    tvStatus.setTextColor(Color.parseColor("#00D0FF"))
                    findViewById<Button>(R.id.btnToggleAlarm).text = "DESARMAR ALARME"
                } else {
                    tvStatus.setTextColor(Color.parseColor("#FF3B30"))
                    findViewById<Button>(R.id.btnToggleAlarm).text = "ARMAR ALARME"
                }

                // CONTROLO: Só o dono vê a parte dos Logs e Acessos!
                if (ownerId == currentUserUid) {
                    findViewById<View>(R.id.layoutOwnerOnly).visibility = View.VISIBLE
                    carregarListaDeAcessos()
                } else {
                    findViewById<View>(R.id.layoutOwnerOnly).visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun carregarListaDeAcessos() {

        if(accessListener != null) return

        accessListener = database.child("alarms").child(alarmId!!).child("access_list")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    accessList.clear()
                    for (child in snapshot.children) {
                        val expiry = child.child("expiry").getValue(String::class.java)

                        if (isExpired(expiry)) {
                            // Se o dono abrir a página e vir alguém expirado, a app limpa a DB
                            val targetUid = child.key!!
                            // Limpa no alarme
                            child.ref.removeValue()
                            // Limpa no utilizador (para garantir)
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

    private fun removerAcesso(acesso: AccessItem) {
        // 1. Carregar o nosso design escuro
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvConfirmMessage)

        // Colocar a mensagem com o email certo
        tvMessage.text = "Tens a certeza que queres remover o acesso de ${acesso.email}?"

        // 2. Criar o alerta sem usar os botões nativos do Android
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 3. Tornar o fundo branco nativo do Android invisível!
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // 4. Lógica do botão CANCELAR
        dialogView.findViewById<TextView>(R.id.btnCancelConfirm).setOnClickListener {
            dialog.dismiss()
        }

        // 5. Lógica do botão REMOVER (A Vermelho!)
        dialogView.findViewById<TextView>(R.id.btnConfirmAction).setOnClickListener {
            val targetUid = acesso.uid
            val aid = alarmId!!

            // Apaga da base de dados
            database.child("users").child(targetUid).child("alarms").child(aid).removeValue()
            database.child("alarms").child(aid).child("access_list").child(targetUid).removeValue()
                .addOnSuccessListener { Toast.makeText(this, "Acesso removido com sucesso", Toast.LENGTH_SHORT).show() }

            dialog.dismiss() // Fecha o popup
        }

        dialog.show()
    }

    private fun toggleAlarmStatus() {
        val newStatus = if (currentStatus == "Armado") "Desarmado" else "Armado"
        database.child("alarms").child(alarmId!!).child("status").setValue(newStatus)
    }

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

        // ==========================================
        // O SEGREDO DO CALENDÁRIO AZUL: R.style.CustomPickerTheme
        // ==========================================
        btnPickDateTime.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, R.style.CustomPickerTheme, { _, year, month, dayOfMonth ->
                TimePickerDialog(this, R.style.CustomPickerTheme, { _, hourOfDay, minute ->
                    selectedExpiryDate = String.format("%02d/%02d/%04d %02d:%02d", dayOfMonth, month + 1, year, hourOfDay, minute)
                    btnPickDateTime.text = selectedExpiryDate
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.btnCancelAdd).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnConfirmAdd).setOnClickListener {
            val emailTarget = etEmail.text.toString().trim()
            val isChild = rgAccessType.checkedRadioButtonId == R.id.rbChild

            if (emailTarget.isEmpty()) {
                Toast.makeText(this, "Email obrigatório.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isChild && selectedExpiryDate == null) {
                Toast.makeText(this, "Tens de escolher uma data limite para o Convidado.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            associarUtilizador(emailTarget, isChild, selectedExpiryDate)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun associarUtilizador(emailTarget: String, isChild: Boolean, expiryDate: String?) {
        database.child("users").orderByChild("email").equalTo(emailTarget).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val targetUid = snapshot.children.first().key!!

                    // AGORA GUARDAMOS O EMAIL AQUI PARA SER FÁCIL DE LER NA LISTA!
                    val accessData = mapOf(
                        "email" to emailTarget,
                        "isChild" to isChild,
                        "expiry" to if(isChild) null else expiryDate
                    )

                    database.child("users").child(targetUid).child("alarms").child(alarmId!!).setValue(accessData)
                    database.child("alarms").child(alarmId!!).child("access_list").child(targetUid).setValue(accessData)
                        .addOnSuccessListener { Toast.makeText(this@UserAlarmDetailsActivity, "Acesso concedido com sucesso!", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this@UserAlarmDetailsActivity, "Não existe nenhuma conta com este email.", Toast.LENGTH_SHORT).show()
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