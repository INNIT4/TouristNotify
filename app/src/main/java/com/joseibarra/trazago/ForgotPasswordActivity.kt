package com.joseibarra.trazago

import android.os.Bundle
import android.util.Patterns
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.trazago.R
import com.joseibarra.trazago.databinding.ActivityForgotPasswordBinding
import com.joseibarra.trazago.ui.BaseActivity

class ForgotPasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.forgotPasswordRoot, applyTop = true, applyBottom = true)

        auth = FirebaseAuth.getInstance()

        binding.backButton.setOnClickListener { finish() }
        binding.backToLoginTextView.setOnClickListener { finish() }
        binding.resetPasswordButton.setOnClickListener { sendPasswordResetEmail() }

        // Limpia el error en cuanto el usuario teclea
        binding.emailEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.emailInputLayout.error = null
            }
        })
    }

    private fun sendPasswordResetEmail() {
        val email = binding.emailEditText.text?.toString()?.trim().orEmpty()

        if (email.isBlank()) {
            binding.emailInputLayout.error = getString(R.string.enter_email_please)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Ingresa un correo válido"
            return
        }

        binding.resetPasswordButton.isEnabled = false
        binding.resetPasswordButton.text = getString(R.string.forgot_sending)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                // Por seguridad, mostramos confirmación tanto si el correo existe como si no.
                showConfirmation(email, task.isSuccessful)
            }
    }

    private fun showConfirmation(email: String, success: Boolean) {
        binding.resetPasswordButton.isEnabled = true
        binding.resetPasswordButton.text = getString(R.string.forgot_send_link)

        val message = if (success) {
            getString(R.string.reset_email_sent) + "\n\n$email"
        } else {
            getString(R.string.reset_email_fallback)
        }

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Aceptar") { finish() }
            .show()
    }
}
