package com.joseibarra.touristnotify.wizard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.databinding.ActivityRouteWizardBinding
import com.joseibarra.touristnotify.model.GeneratedRoute
import com.joseibarra.touristnotify.result.RouteResultActivity

/**
 * Activity host del wizard multi-paso de generación de rutas.
 * Contiene un ViewPager2 con 4 pasos:
 *   1. ¿Quiénes y cuándo?
 *   2. ¿Qué te gusta?
 *   3. Restricciones
 *   4. Confirmación + generación
 *
 * El estado se preserva en [PreferencesViewModel] + SavedStateHandle.
 */
class RouteWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteWizardBinding
    val viewModel: PreferencesViewModel by viewModels()

    private val totalSteps = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Planifica tu ruta"

        setupViewPager()
        setupNavButtons()
        updateUI(0)
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = WizardPagerAdapter(this)
        // Deshabilitar swipe manual — el usuario usa los botones
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })
    }

    private fun setupNavButtons() {
        binding.btnBack.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) binding.viewPager.currentItem = current - 1
            else finish()
        }

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            val error = viewModel.validateStep(current + 1)
            if (error != null) {
                showStepError(error)
                return@setOnClickListener
            }
            if (current < totalSteps - 1) {
                binding.viewPager.currentItem = current + 1
            }
            // El paso 4 (confirmación) maneja la generación internamente
        }
    }

    private fun updateUI(position: Int) {
        // Barra de progreso: 1 a 4 pasos
        binding.progressIndicator.max = totalSteps
        binding.progressIndicator.progress = position + 1

        // Label del paso
        val stepLabels = arrayOf(
            "¿Quiénes y cuándo?",
            "¿Qué te gusta?",
            "Restricciones",
            "Confirmar y generar"
        )
        binding.tvStepLabel.text = "Paso ${position + 1} de $totalSteps · ${stepLabels[position]}"

        // Botón siguiente / generar
        binding.btnNext.text = if (position == totalSteps - 1) "" else "Siguiente"
        binding.btnNext.visibility = if (position == totalSteps - 1)
            android.view.View.GONE else android.view.View.VISIBLE

        // Botón atrás
        binding.btnBack.text = if (position == 0) "Cancelar" else "Atrás"
    }

    fun showStepError(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    /** Called by Step4ReviewFragment when generation completes successfully. */
    fun onRouteGenerated(route: GeneratedRoute, spots: List<TouristSpot>) {
        startActivity(RouteResultActivity.newIntent(this, route, spots))
        finish()
    }

    /** Enables or disables the Back/Next navigation buttons during generation. */
    fun setNavigationEnabled(enabled: Boolean) {
        binding.btnBack.isEnabled = enabled
        binding.btnNext.isEnabled = enabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class WizardPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount() = totalSteps

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> Step1WhoWhenFragment()
            1 -> Step2InterestsFragment()
            2 -> Step3ConstraintsFragment()
            3 -> Step4ReviewFragment()
            else -> Step1WhoWhenFragment()
        }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, RouteWizardActivity::class.java)
    }
}
