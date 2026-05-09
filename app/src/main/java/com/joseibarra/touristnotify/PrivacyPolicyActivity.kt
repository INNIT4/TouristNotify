package com.joseibarra.touristnotify

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.joseibarra.touristnotify.databinding.ActivityPrivacyPolicyBinding

/**
 * Pantalla de Política de Privacidad y Aviso de Privacidad Simplificado.
 *
 * Cumplimiento: GDPR Art. 13/14, LFPDPPP Art. 17, Google Play Data Safety.
 *
 * TODO (acción humana): Reemplazar el contenido placeholder por el texto legal
 * definitivo aprobado por un abogado. Ver `.claude/audit/compliance-auditor.md`
 * sección "Documentos a crear" para los puntos obligatorios.
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyPolicyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.privacy_policy_title)

        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
