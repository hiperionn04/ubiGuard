package pt.fct.unl.sim.ubiguard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val alarmList = mutableListOf<Alarm>()
    private lateinit var alarmAdapter: AlarmAdapter

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

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        val layoutEmptyState = findViewById<LinearLayout>(R.id.layoutEmptyState)
        val rvAlarms = findViewById<RecyclerView>(R.id.rvAlarms)

        val menuPerfil = findViewById<TextView>(R.id.menuPerfil)
        val menuAlarmes = findViewById<TextView>(R.id.menuAlarmes)
        val menuLogout = findViewById<TextView>(R.id.menuLogout)

        rvAlarms.layoutManager = LinearLayoutManager(this)
        alarmAdapter = AlarmAdapter(alarmList) { alarmeClicado ->
            Toast.makeText(this, "A abrir definições de: ${alarmeClicado.name}", Toast.LENGTH_SHORT).show()
        }
        rvAlarms.adapter = alarmAdapter

        val userId = currentUser.uid
        database.child("users").child(userId).child("alarms")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    alarmList.clear()

                    if (snapshot.exists() && snapshot.hasChildren()) {
                        for (alarmSnapshot in snapshot.children) {
                            val alarm = alarmSnapshot.getValue(Alarm::class.java)
                            if (alarm != null) {
                                val alarmWithId = alarm.copy(id = alarmSnapshot.key ?: "")
                                alarmList.add(alarmWithId)
                            }
                        }
                        layoutEmptyState.visibility = View.GONE
                        rvAlarms.visibility = View.VISIBLE
                    } else {
                        layoutEmptyState.visibility = View.VISIBLE
                        rvAlarms.visibility = View.GONE
                    }

                    alarmAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Erro a carregar alarmes.", Toast.LENGTH_SHORT).show()
                }
            })

        ivMenuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        menuPerfil.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Em breve: O Meu Perfil", Toast.LENGTH_SHORT).show()
        }

        menuAlarmes.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        menuLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}