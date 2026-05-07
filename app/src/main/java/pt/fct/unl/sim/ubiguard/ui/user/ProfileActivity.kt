package pt.fct.unl.sim.ubiguard.ui.user

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity

// 1. Herda de BaseActivity
class ProfileActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        val tvProfileName = findViewById<TextView>(R.id.tvProfileName)
        val tvProfileEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvProfileType = findViewById<TextView>(R.id.tvProfileType)

        val layoutContent = findViewById<LinearLayout>(R.id.layoutProfileContent)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarProfile)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.error_session), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userId = currentUser.uid
        val email = currentUser.email

        tvProfileEmail.text = email ?: getString(R.string.error_no_email)

        database.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: getString(R.string.general_unknown)
                    val accountType = snapshot.child("account_type").getValue(String::class.java) ?: "User"

                    tvProfileName.text = name
                    tvProfileType.text = accountType

                    progressBar.visibility = View.GONE
                    layoutContent.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this, getString(R.string.error_profile_notfound), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.error_loading_profile), Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}