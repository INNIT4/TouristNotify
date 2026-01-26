package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
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

        binding.forgotPasswordTextView.setOnClickListener {
            navigateToForgotPassword()
        }

        binding.registerTextView.setOnClickListener {
            navigateToRegister()
        }

        // Botón "Continuar como invitado" - SIEMPRE VISIBLE
        binding.skipButton.visibility = View.VISIBLE
        binding.skipButton.setOnClickListener {
            enableGuestModeAndNavigate()
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
                        // SEGURIDAD: Mensaje de error genérico para no revelar información
                        Toast.makeText(
                            baseContext,
                            "Error de autenticación. Verifica tus credenciales",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Log detallado solo en modo debug
                        if (BuildConfig.DEBUG) {
                            Log.e("LoginActivity", "Auth error: ${task.exception?.message}")
                        }
                    }
                }
        } else {
            Toast.makeText(this, "Por favor, rellena todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToForgotPassword() {
        val intent = Intent(this, ForgotPasswordActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToMenu() {
        // Desactivar modo invitado si se hace login
        AuthManager.disableGuestMode(this)

        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
        finish() // Cierra LoginActivity para que el usuario no pueda volver atrás
    }

    private fun enableGuestModeAndNavigate() {
        // Activar modo invitado
        AuthManager.enableGuestMode(this)

        Toast.makeText(
            this,
            "Modo invitado activado. Algunas funciones estarán limitadas.",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
        finish()
    }
}