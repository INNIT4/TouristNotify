package com.joseibarra.touristnotify.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.joseibarra.touristnotify.R
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.UsageManager
import com.joseibarra.touristnotify.WeatherManager
import com.joseibarra.touristnotify.databinding.FragmentStep4ReviewBinding
import com.joseibarra.touristnotify.model.GeneratedRoute
import com.joseibarra.touristnotify.routegen.RouteGenerationCoordinator
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
        val ninosStr = if (prefs.numNiños > 0) " + ${prefs.numNiños} niños" else ""
        binding.tvResumenViajeros.text = "${prefs.tipoViaje.label()} · ${prefs.numAdultos} adultos$ninosStr"

        // Fecha y hora
        val cal = Calendar.getInstance().apply { timeInMillis = prefs.fechaViajeMs }
        val fechaStr = dateFormat.format(cal.time)
        binding.tvResumenFechaHora.text = "$fechaStr · ${prefs.horaInicioStr}"

        // Duración
        val h = prefs.duracionHoras
        binding.tvResumenDuracion.text = when {
            h == 1f        -> "1 hora"
            h % 1f == 0f   -> "${h.toInt()} horas"
            else           -> "${h.toInt()}h ${((h % 1f) * 60).toInt()}min"
        }

        // Presupuesto
        binding.tvResumenPresupuesto.text = "$${prefs.presupuestoMxn} MXN"

        // Intereses
        binding.tvResumenIntereses.text = if (prefs.intereses.isNotEmpty())
            prefs.intereses.joinToString(", ")
        else
            prefs.temaSolicitudLibre.ifBlank { "Sin especificar" }

        // Ritmo
        binding.tvResumenRitmo.text = prefs.ritmo.label()
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
                    val aviso = if (isRaining) " · Se recomienda ropa impermeable" else ""
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
                        prefs = prefs,
                        climateBrief = climateDesc,
                        isRaining = isRaining
                    ),
                    onProgress = { state ->
                        requireActivity().runOnUiThread {
                            if (_binding != null) {
                                binding.tvGeneratingStatus.text = when (state) {
                                    is RouteGenerationCoordinator.GenerationState.LoadingPlaces ->
                                        "Cargando lugares de interés..."
                                    is RouteGenerationCoordinator.GenerationState.FilteringCandidates ->
                                        "Filtrando candidatos..."
                                    is RouteGenerationCoordinator.GenerationState.CallingAI ->
                                        "Consultando inteligencia artificial..."
                                    is RouteGenerationCoordinator.GenerationState.Optimizing ->
                                        "Optimizando la ruta..."
                                    else -> "Generando tu ruta..."
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
                        "Error al generar la ruta: ${e.message ?: "Intenta de nuevo"}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setGeneratingUi(generating: Boolean) {
        binding.btnGenerar.isEnabled = !generating
        binding.btnGenerar.text = if (generating) "Generando..." else "Generar ruta con IA"
        binding.layoutGenerating.visibility = if (generating) View.VISIBLE else View.GONE
        (requireActivity() as RouteWizardActivity).setNavigationEnabled(!generating)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
