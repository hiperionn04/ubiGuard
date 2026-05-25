package pt.fct.unl.sim.ubiguard.ui.base

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
import pt.fct.unl.sim.ubiguard.ui.alarm.InstallerAlarmsActivity
import pt.fct.unl.sim.ubiguard.ui.alarm.InstallerLogsActivity
import pt.fct.unl.sim.ubiguard.ui.alarm.InstallerSensorsActivity
import pt.fct.unl.sim.ubiguard.ui.auth.LoginActivity
import pt.fct.unl.sim.ubiguard.ui.dashboard.MainActivity
import pt.fct.unl.sim.ubiguard.ui.user.ProfileActivity
import pt.fct.unl.sim.ubiguard.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    protected fun ativarSliderComponent(drawerLayout: DrawerLayout, iconeMenu: ImageView) {

        val menuDashboard = findViewById<TextView>(R.id.menuDashboard)
        val menuProfile = findViewById<TextView>(R.id.menuProfile)
        val menuAlarms = findViewById<TextView>(R.id.menuAlarms)
        val menuSensors = findViewById<TextView>(R.id.menuSensors)
        val menuCamera = findViewById<TextView>(R.id.menuCamera)
        val menuLogs = findViewById<TextView>(R.id.menuLogs)
        val menuLogout = findViewById<TextView>(R.id.menuLogout)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        iconeMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        if (user != null) {
            FirebaseDatabase.getInstance().reference.child("users").child(user.uid).child("account_type").get()
                .addOnSuccessListener { snapshot ->
                    val accountType = snapshot.getValue(String::class.java) ?: "User"

                    if (accountType == "Installer") {
                        menuDashboard.visibility = View.VISIBLE
                        menuSensors.visibility = View.VISIBLE
                        menuLogs.visibility = View.VISIBLE

                        menuAlarms.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is InstallerAlarmsActivity) {
                                startActivity(Intent(this, InstallerAlarmsActivity::class.java))
                            }
                        }

                        menuDashboard.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is MainActivity) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                                finish()
                            }
                        }

                        menuSensors.setOnClickListener {
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
                        menuSensors.visibility = View.GONE
                        menuLogs.visibility = View.GONE

                        val goToMain = View.OnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            if (this !is MainActivity) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                                finish()
                            }
                        }
                        menuAlarms.setOnClickListener(goToMain)

                        menuCamera.setOnClickListener {
                            startActivity(Intent(this,
                                pt.fct.unl.sim.ubiguard.ui.camera.ViewerActivity::class.java))
                            drawerLayout.closeDrawers()
                        }
                    }
                }
        }
        
        val goToMain = View.OnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (this !is MainActivity) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
        }

        menuDashboard.setOnClickListener(goToMain)
        menuAlarms.setOnClickListener(goToMain)

        menuProfile.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (this !is ProfileActivity) {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }

        menuLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    fun isExpired(expiryDateStr: String?): Boolean {
        if (expiryDateStr == null) return false

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return try {
            val expiryDate = sdf.parse(expiryDateStr)
            expiryDate?.before(Date()) ?: false
        } catch (e: Exception) {
            false
        }
    }
}
