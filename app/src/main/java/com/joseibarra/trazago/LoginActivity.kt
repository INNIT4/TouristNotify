package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.trazago.R
import com.joseibarra.trazago.databinding.ActivityLoginBinding
import com.joseibarra.trazago.ui.BaseActivity

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si se llega desde una feature bloqueada (invitado quiere iniciar sesión), mostrar siempre el login
        val returnAfterLogin = intent.getBooleanExtra("RETURN_AFTER_LOGIN", false)

        if (!returnAfterLogin) {
            // Si ya hay sesión activa (cuenta real o modo invitado), ir directo al menú
            val currentUser = auth.currentUser
            val isGuest = AuthManager.isGuestMode(this)
            if (currentUser != null || isGuest) {
                goToMenu()
                return
            }
        } else {
            // El usuario invitado quiere registrarse/iniciar sesión — desactivar modo invitado para que el login funcione
            AuthManager.disableGuestMode(this)
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.loginRoot, applyTop = true, applyBottom = true)

        animateLogoEntry()

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

    /** Entrada del logo con scale overshoot suave + leve fade. */
    private fun animateLogoEntry() {
        binding.appIcon.apply {
            scaleX = 0.7f
            scaleY = 0.7f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(520)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
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
                            getString(R.string.error_auth_generic),
                            Toast.LENGTH_SHORT
                        ).show()

                        // Log detallado solo en modo debug
                        if (BuildConfig.DEBUG) {
                            Log.e("LoginActivity", "Auth error: ${task.exception?.message}")
                        }
                    }
                }
        } else {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
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

    /**
     * Navega al menú después de un login exitoso con cuenta real.
     * Desactiva el modo invitado si estaba activo.
     */
    private fun navigateToMenu() {
        AuthManager.disableGuestMode(this)
        goToMenu()
    }

    /**
     * Ir al menú sin cambiar el estado de autenticación (para sesiones ya activas).
     */
    private fun goToMenu() {
        val intent = Intent(this, MenuActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun enableGuestModeAndNavigate() {
        // Cerrar cualquier sesión de Firebase activa antes de entrar como invitado
        auth.signOut()
        AuthManager.enableGuestMode(this)

        Toast.makeText(
            this,
            getString(R.string.guest_mode_activated),
            Toast.LENGTH_LONG
        ).show()

        goToMenu()
    }
}