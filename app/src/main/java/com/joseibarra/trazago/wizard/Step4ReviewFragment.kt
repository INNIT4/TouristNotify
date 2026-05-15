package com.joseibarra.trazago.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.joseibarra.trazago.R
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.UsageManager
import com.joseibarra.trazago.WeatherManager
import com.joseibarra.trazago.databinding.FragmentStep4ReviewBinding
import com.joseibarra.trazago.model.GeneratedRoute
import com.joseibarra.trazago.routegen.RouteGenerationCoordinator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Paso 4: Confirmación + Generación.
 * Muestra resumen de preferencias, clima del día, disclaimer IA y botón de generación.
 */
class Step4ReviewFragment : Fragment() {

    private var _binding: FragmentStep4ReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PreferencesViewModel by activityViewModels()

    private val dateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale("es", "MX"))
    private var isGenerating = false
    private var disclaimerAccepted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStep4ReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateSummary()
        loadWeather()
        binding.btnGenerar.setOnClickListener { startGeneration() }
    }

    private fun populateSummary() {
        val prefs = viewModel.buildPreferences()

        // Viajeros
        val ninosStr = if (prefs.numNiños > 0) " + ${prefs.numNiños} ${getString(R.string.step_children).lowercase()}" else ""
        binding.tvResumenViajeros.text = "${prefs.tipoViaje.label(requireContext())} · ${prefs.numAdultos} ${getString(R.string.step_adults).lowercase()}$ninosStr"

        // Fecha y hora
        val cal = Calendar.getInstance().apply { timeInMillis = prefs.fechaViajeMs }
        val fechaStr = dateFormat.format(cal.time)
        binding.tvResumenFechaHora.text = "$fechaStr · ${prefs.horaInicioStr}"

        // Duración
        val h = prefs.duracionHoras
        binding.tvResumenDuracion.text = when {
            h == 1f        -> getString(R.string.step_duration_hour)
            h % 1f == 0f   -> getString(R.string.step_duration_hours, h.toInt().toString())
            else           -> "${h.toInt()}h ${((h % 1f) * 60).toInt()}min"
        }

        // Presupuesto
        binding.tvResumenPresupuesto.text = getString(R.string.step_review_budget_format, prefs.presupuestoMxn)

        // Intereses
        binding.tvResumenIntereses.text = if (prefs.intereses.isNotEmpty())
            prefs.intereses.joinToString(", ")
        else
            getString(R.string.unspecified)

        // Ritmo
        binding.tvResumenRitmo.text = prefs.ritmo.label(requireContext())
    }

    private fun loadWeather() {
        viewLifecycleOwner.lifecycleScope.launch {
            WeatherManager.getCurrentWeather()
                .onSuccess { weather ->
                    if (_binding == null) return@onSuccess
                    val emoji = WeatherManager.getWeatherEmoji(weather.icon)
                    binding.tvClimaEmoji.text = emoji
                    val isRaining = weather.icon.startsWith("09") ||
                                    weather.icon.startsWith("10") ||
                                    weather.icon.startsWith("11")
                    val aviso = if (isRaining) " · ${getString(R.string.weather_rain_warning)}" else ""
                    binding.tvClimaDescripcion.text =
                        "${weather.description.replaceFirstChar { it.uppercase() }}, " +
                        "${weather.temperature.toInt()}°C$aviso"
                    binding.cardClima.visibility = View.VISIBLE
                }
        }
    }

    private fun startGeneration() {
        if (isGenerating) return

        if (!disclaimerAccepted) {
            showAiDisclaimer()
            return
        }

        executeGeneration()
    }

    private fun showAiDisclaimer() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ai_disclaimer_title))
            .setMessage(getString(R.string.ai_disclaimer_message))
            .setPositiveButton(getString(R.string.ai_disclaimer_accept)) { _, _ ->
                disclaimerAccepted = true
                executeGeneration()
            }
            .setNegativeButton(getString(R.string.ai_disclaimer_cancel), null)
            .setCancelable(false)
            .show()
    }

    private fun executeGeneration() {
        isGenerating = true
        setGeneratingUi(true)

        val prefs = viewModel.buildPreferences()

        // Obtener climateBrief e isRaining del estado de la card de clima si ya cargó
        val climateDesc = binding.tvClimaDescripcion.text.toString()
        val isRaining = climateDesc.contains("lluvia", ignoreCase = true) ||
                        climateDesc.contains("tormenta", ignoreCase = true) ||
                        climateDesc.contains("rain", ignoreCase = true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (canGenerate, limitMessage) = UsageManager.canGenerateRoute(requireContext())
                if (!canGenerate) {
                    if (_binding != null) {
                        setGeneratingUi(false)
                        isGenerating = false
                        Snackbar.make(binding.root, limitMessage, Snackbar.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val (route, spots) = RouteGenerationCoordinator.generate(
                    request = RouteGenerationCoordinator.GenerationRequest(
                        context = requireContext(),
                        prefs = prefs,
                        climateBrief = climateDesc,
                        isRaining = isRaining
                    ),
                    onProgress = { state ->
                        requireActivity().runOnUiThread {
                            if (_binding != null) {
                                binding.tvGeneratingStatus.text = when (state) {
                                    is RouteGenerationCoordinator.GenerationState.LoadingPlaces ->
                                        getString(R.string.ai_progress_loading_places)
                                    is RouteGenerationCoordinator.GenerationState.FilteringCandidates ->
                                        getString(R.string.ai_progress_filtering)
                                    is RouteGenerationCoordinator.GenerationState.CallingAI ->
                                        getString(R.string.ai_progress_calling)
                                    is RouteGenerationCoordinator.GenerationState.Optimizing ->
                                        getString(R.string.ai_progress_optimizing)
                                    else -> getString(R.string.step_review_generating)
                                }
                            }
                        }
                    }
                )
                UsageManager.recordRouteGeneration(requireContext())
                if (_binding != null) {
                    (requireActivity() as RouteWizardActivity).onRouteGenerated(route, spots)
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    setGeneratingUi(false)
                    isGenerating = false
                    Snackbar.make(
                        binding.root,
                        getString(R.string.ai_generation_error, e.message ?: getString(R.string.try_again)),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setGeneratingUi(generating: Boolean) {
        binding.btnGenerar.isEnabled = !generating
        binding.btnGenerar.text = if (generating) getString(R.string.generating) else getString(R.string.step_review_generate_btn)
        binding.layoutGenerating.visibility = if (generating) View.VISIBLE else View.GONE
        (requireActivity() as RouteWizardActivity).setNavigationEnabled(!generating)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
