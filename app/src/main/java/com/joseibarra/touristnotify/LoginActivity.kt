package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.touristnotify.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.loginButton.setOnClickListener {
            signInUser()
        }

        binding.registerTextView.setOnClickListener {
            registerUser()
        }

        // El botón "Saltar" ahora funciona como atajo de desarrollador al menú
        binding.skipButton.setOnClickListener {
            navigateToMenu()
        }
    }

    private fun signInUser() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (email.isNotBlank() && password.isNotBlank()) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        navigateToMenu()
                    } else {
                        Toast.makeText(baseContext, "Fallo en la autenticación: ${task.exception?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            Toast.makeText(this, "Por favor, rellena todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerUser() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (email.isNotBlank() && password.isNotBlank()) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                        navigateToMenu()
                    } else {
                        Toast.makeText(baseContext, "Fallo en el registro: ${task.exception?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            Toast.makeText(this, "Por favor, rellena todos los campos para registrarte", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMenu() {
        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
        finish() // Cierra LoginActivity para que el usuario no pueda volver atrás
    }
}