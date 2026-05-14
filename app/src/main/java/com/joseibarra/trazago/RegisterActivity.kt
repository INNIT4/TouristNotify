package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.trazago.R
import com.joseibarra.trazago.databinding.ActivityRegisterBinding
import com.joseibarra.trazago.ui.BaseActivity

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = false)

        auth = FirebaseAuth.getInstance()

        binding.registerButton.setOnClickListener {
            registerUser()
        }

        binding.loginTextView.setOnClickListener {
            navigateToLogin()
        }

        val raw = getString(R.string.register_privacy_notice)
        binding.privacyPolicyLink.text = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.privacyPolicyLink.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
    }

    private fun registerUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        // Validar que los campos no estén vacíos
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar formato de email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.invalid_email), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar longitud mínima de contraseña
        if (password.length < 6) {
            Toast.makeText(this, getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar que las contraseñas coincidan
        if (password != confirmPassword) {
            Toast.makeText(this, getString(R.string.passwords_dont_match), Toast.LENGTH_SHORT).show()
            return
        }

        // Validar complejidad de la contraseña (al menos una letra y un número)
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            Toast.makeText(this, getString(R.string.password_complexity), Toast.LENGTH_LONG).show()
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
                        .setTitle(getString(R.string.account_created_title))
                        .setMessage(getString(R.string.account_created_message, email))
                        .setPositiveButton(getString(R.string.understood)) { _, _ -> navigateToMenu() }
                        .setCancelable(false)
                        .show()
                } else {
                    // SEGURIDAD: Mensaje de error genérico
                    Toast.makeText(
                        baseContext,
                        getString(R.string.error_register_generic),
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