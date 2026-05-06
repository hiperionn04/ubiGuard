package pt.fct.unl.sim.ubiguard

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    protected fun ativarSliderComponent(drawerLayout: DrawerLayout, iconeMenu: ImageView) {

        val menuDashboard = findViewById<TextView>(R.id.menuDashboard)
        val menuPerfil = findViewById<TextView>(R.id.menuPerfil)
        val menuAlarmes = findViewById<TextView>(R.id.menuAlarmes)
        val menuSensores = findViewById<TextView>(R.id.menuSensores)
        val menuLogs = findViewById<TextView>(R.id.menuLogs)
        val menuLogout = findViewById<TextView>(R.id.menuLogout)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // 1. Abrir a gaveta
        iconeMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        if (user != null) {
            FirebaseDatabase.getInstance().reference.child("users").child(user.uid).child("account_type").get()
                .addOnSuccessListener { snapshot ->
                    val accountType = snapshot.getValue(String::class.java) ?: "User"

                    if (accountType == "Installer") {
                        menuDashboard.visibility = View.VISIBLE
                        menuSensores.visibility = View.VISIBLE
                        menuLogs.visibility = View.VISIBLE

                        // O Installer ao clicar em Alarmes vai para a vista global!
                        menuAlarmes.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is InstallerAlarmsActivity) {
                                startActivity(Intent(this, InstallerAlarmsActivity::class.java))
                            }
                        }

                        // O Installer ao clicar em Dashboard vai para o menu das 2 teclas gigantes
                        menuDashboard.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is MainActivity) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                                finish()
                            }
                        }

                        menuSensores.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is InstallerSensorsActivity) {
                                startActivity(Intent(this, InstallerSensorsActivity::class.java))
                            }
                        }

                        menuLogs.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is InstallerLogsActivity) {
                                startActivity(Intent(this, InstallerLogsActivity::class.java))
                            }
                        }

                    } else {
                        menuDashboard.visibility = View.GONE
                        menuSensores.visibility = View.GONE
                        menuLogs.visibility = View.GONE

                        val acaoIrParaMain = View.OnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is MainActivity) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                                finish()
                            }
                        }
                        menuAlarmes.setOnClickListener(acaoIrParaMain)
                    }
                }
        }

        // ==========================================
        // LÓGICA DE CLIQUES DO MENU
        // ==========================================

        // Ir para o Dashboard / Alarmes (Ambos apontam para a MainActivity)
        val acaoIrParaMain = View.OnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (this !is MainActivity) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
        }

        menuDashboard.setOnClickListener(acaoIrParaMain)
        menuAlarmes.setOnClickListener(acaoIrParaMain)

        // Ir para o Perfil
        menuPerfil.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (this !is ProfileActivity) {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }

        // Menus provisórios do Installer
        menuSensores.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Em breve: Sensores", Toast.LENGTH_SHORT).show()
        }

        menuLogs.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Em breve: Logs", Toast.LENGTH_SHORT).show()
        }

        // Logout Universal
        menuLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    fun isExpired(expiryDateStr: String?): Boolean {
        if (expiryDateStr == null) return false // Se for Criança, nunca expira

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return try {
            val expiryDate = sdf.parse(expiryDateStr)
            // Se a data de expiração for ANTES (before) de AGORA (Date()), então expirou
            expiryDate?.before(Date()) ?: false
        } catch (e: Exception) {
            false
        }
    }

}