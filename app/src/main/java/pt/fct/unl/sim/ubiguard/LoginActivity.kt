package pt.fct.unl.sim.ubiguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLoginOwner = findViewById<Button>(R.id.btnLoginOwner)

        val etPin = findViewById<EditText>(R.id.etPin)
        val btnLoginPin = findViewById<Button>(R.id.btnLoginPin)

        btnLoginOwner.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Bem-vindo, Owner!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Erro: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Preenche o email e password", Toast.LENGTH_SHORT).show()
            }
        }

        btnLoginPin.setOnClickListener {
            val pin = etPin.text.toString()

            if (pin.isNotEmpty()) {
                /*
                 * Futuramente, vamos ligar isto à Firebase Realtime Database
                 * para verificar se o PIN existe e se o temporizador (Guest) ainda é válido.
                 * Por agora, usamos um PIN falso de teste:
                 */
                if (pin == "1234") {
                    Toast.makeText(this, "Acesso Concedido (Guest)", Toast.LENGTH_SHORT).show()
                    // val intent = Intent(this, GuestActivity::class.java)
                    // startActivity(intent)
                } else {
                    Toast.makeText(this, "PIN Incorreto", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Insere um PIN válido", Toast.LENGTH_SHORT).show()
            }
        }
    }
}