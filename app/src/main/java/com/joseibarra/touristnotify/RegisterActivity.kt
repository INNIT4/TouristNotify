package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.touristnotify.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.registerButton.setOnClickListener {
            registerUser()
        }

        binding.loginTextView.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun registerUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        // Validar que los campos no estén vacíos
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            Toast.makeText(this, getString(R.string.msg_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar formato de email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.msg_invalid_email), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar longitud mínima de contraseña
        if (password.length < 6) {
            Toast.makeText(this, getString(R.string.msg_password_min_6_chars), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar que las contraseñas coincidan
        if (password != confirmPassword) {
            Toast.makeText(this, getString(R.string.msg_passwords_no_match), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar complejidad de la contraseña (al menos una letra y un número)
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            Toast.makeText(this, getString(R.string.msg_password_needs_letter_digit), Toast.LENGTH_LONG).show()
            return
        }

        // Si todas las validaciones pasan, crear la cuenta
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Enviar correo de verificación
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (BuildConfig.DEBUG && !verifyTask.isSuccessful) {
                                Log.e("RegisterActivity", "sendEmailVerification error: ${verifyTask.exception?.message}")
                            }
                        }

                    // Informar al usuario y navegar al menú
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.title_account_created))
                        .setMessage(getString(R.string.msg_verify_email_sent, email))
                        .setPositiveButton(getString(R.string.btn_understood)) { _, _ -> navigateToMenu() }
                        .setCancelable(false)
                        .show()
                } else {
                    // SEGURIDAD: Mensaje de error genérico
                    Toast.makeText(
                        baseContext,
                        getString(R.string.error_registration_failed),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Log detallado solo en modo debug
                    if (BuildConfig.DEBUG) {
                        Log.e("RegisterActivity", "Register error: ${task.exception?.message}")
                    }
                }
            }
    }

    private fun navigateToMenu() {
        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}