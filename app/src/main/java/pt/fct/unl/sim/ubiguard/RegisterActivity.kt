package pt.fct.unl.sim.ubiguard

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val etRegName = findViewById<EditText>(R.id.etRegName)
        val etRegEmail = findViewById<EditText>(R.id.etRegEmail)
        val etRegAddress = findViewById<EditText>(R.id.etRegAddress)
        val etRegPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegisterSubmit = findViewById<AppCompatButton>(R.id.btnRegisterSubmit)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        val progressBar = findViewById<ProgressBar>(R.id.progressBarRegister)

        tvGoToLogin.setOnClickListener {
            finish()
        }

        btnRegisterSubmit.setOnClickListener {
            val email = etRegEmail.text.toString().trim()
            val password = etRegPassword.text.toString().trim()
            val name = etRegName.text.toString().trim()
            val address = etRegAddress.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Por favor, preenche todos os campos.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "A password deve ter pelo menos 6 caracteres.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // NOVO: Mostrar a rodinha de loading e desativar o botão para evitar spam
            progressBar.visibility = View.VISIBLE
            btnRegisterSubmit.isEnabled = false
            btnRegisterSubmit.alpha = 0.5f // Fica meio transparente para parecer desativado

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {

                        val user = auth.currentUser
                        val userId = user?.uid

                        if (userId != null) {
                            val userMap = hashMapOf(
                                "name" to name,
                                "email" to email,
                                "address" to address,
                                "account_type" to "User"
                            )

                            database.child("users").child(userId).setValue(userMap)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                                    // Aqui não precisamos de reativar o botão porque vamos fechar o ecrã a seguir
                                    finish()
                                }
                                .addOnFailureListener {
                                    user.delete().addOnCompleteListener {
                                        etRegName.text.clear()
                                        etRegEmail.text.clear()
                                        etRegAddress.text.clear()
                                        etRegPassword.text.clear()

                                        Toast.makeText(this, "Um erro ocorreu a criar a conta. Tenta novamente.", Toast.LENGTH_LONG).show()

                                        // NOVO: Esconder a rodinha e reativar o botão porque deu erro na BD
                                        progressBar.visibility = View.GONE
                                        btnRegisterSubmit.isEnabled = true
                                        btnRegisterSubmit.alpha = 1.0f
                                    }
                                }
                        }
                    } else {
                        Toast.makeText(this, "Erro: ${task.exception?.message}", Toast.LENGTH_LONG).show()

                        // NOVO: Esconder a rodinha e reativar o botão porque deu erro na Auth
                        progressBar.visibility = View.GONE
                        btnRegisterSubmit.isEnabled = true
                        btnRegisterSubmit.alpha = 1.0f
                    }
                }
        }
    }
}