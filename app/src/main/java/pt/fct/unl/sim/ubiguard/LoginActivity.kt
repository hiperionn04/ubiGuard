package pt.fct.unl.sim.ubiguard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Se já tiver sessão, vai direto para o Dashboard e ignora o resto
        if (auth.currentUser != null) {
            goToDashboard()
            return
        }

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<AppCompatButton>(R.id.btnLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        // NOVO: Referência à ProgressBar
        val progressBar = findViewById<ProgressBar>(R.id.progressBarLogin)

        tvGoToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Preenche o email e a password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // NOVO: Mostrar o loading e desativar o botão para evitar spam
            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false
            btnLogin.alpha = 0.5f

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Bem-vindo!", Toast.LENGTH_SHORT).show()
                        // Nota: Deixamos a rodinha a girar enquanto transita de ecrã (UX mais fluida)
                        goToDashboard()
                    } else {
                        // NOVO: Ocorreu um erro. Esconder o loading e reativar o botão!
                        progressBar.visibility = View.GONE
                        btnLogin.isEnabled = true
                        btnLogin.alpha = 1.0f

                        Toast.makeText(this, "Erro ao entrar: Dados incorretos.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun goToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}