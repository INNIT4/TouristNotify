package com.joseibarra.touristnotify.result

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.joseibarra.touristnotify.databinding.FragmentRouteRegenShareBinding
import com.joseibarra.touristnotify.routegen.RouteGenerationCoordinator
import kotlinx.coroutines.launch

/**
 * Tab 4 — Regenerar con feedback y compartir.
 *
 * Presets de feedback ("más relajada", "más barata", etc.) + campo libre.
 * Botones: Regenerar · Abrir en Google Maps · Compartir como texto.
 */
class RouteRegenShareFragment : Fragment() {

    private var _binding: FragmentRouteRegenShareBinding? = null
    private val binding get() = _binding!!

    private val activity get() = requireActivity() as RouteResultActivity
    private var feedbackLibre: String = ""
    private var isRegenerating = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteRegenShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etFeedbackLibre.doAfterTextChanged {
            feedbackLibre = it?.toString()?.trim() ?: ""
        }

        binding.btnRegenerar.setOnClickListener { startRegeneration() }
        binding.btnAbrirGoogleMaps.setOnClickListener { openInGoogleMaps() }
        binding.btnCompartirTexto.setOnClickListener { shareAsText() }
    }

    private fun buildFeedbackText(): String {
        val selectedPresets = buildList {
            if (binding.chipMasRelajada.isChecked) add("más relajada")
            if (binding.chipMasBarata.isChecked) add("más barata")
            if (binding.chipMasCultural.isChecked) add("más cultural")
            if (binding.chipMenosCaminata.isChecked) add("con menos caminata")
            if (binding.chipMasGastronomia.isChecked) add("con más opciones gastronómicas")
            if (binding.chipMasNaturaleza.isChecked) add("con más contacto con la naturaleza")
        }
        val presetText = if (selectedPresets.isNotEmpty())
            "Quiero una ruta ${selectedPresets.joinToString(" y ")}. "
        else ""
        return (presetText + feedbackLibre).trim()
    }

    private fun startRegeneration() {
        if (isRegenerating) return
        val feedback = buildFeedbackText()
        if (feedback.isBlank()) {
            Snackbar.make(binding.root, "Selecciona un preset o escribe tu feedback", Snackbar.LENGTH_SHORT).show()
            return
        }
        isRegenerating = true
        setRegeneratingUi(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (newRoute, newSpots) = RouteGenerationCoordinator.generate(
                    request = RouteGenerationCoordinator.GenerationRequest(
                        prefs = buildCurrentPrefs(),
                        prevRoute = activity.currentRoute,
                        feedback = feedback
                    )
                )
                if (_binding != null) {
                    activity.updateRoute(newRoute, newSpots)
                    Snackbar.make(binding.root, "¡Ruta regenerada!", Snackbar.LENGTH_SHORT).show()
                    // Limpiar feedback
                    binding.chipGroupFeedback.clearCheck()
                    binding.etFeedbackLibre.setText("")
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    Snackbar.make(
                        binding.root,
                        "Error al regenerar: ${e.message ?: "Intenta de nuevo"}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } finally {
                if (_binding != null) {
                    isRegenerating = false
                    setRegeneratingUi(false)
                }
            }
        }
    }

    /** Re-usa las preferencias del intent de la Activity (ya viene en la ruta). */
    private fun buildCurrentPrefs() = com.joseibarra.touristnotify.model.UserRoutePreferences()

    private fun setRegeneratingUi(regenerating: Boolean) {
        binding.btnRegenerar.isEnabled = !regenerating
        binding.btnRegenerar.text = if (regenerating) "Regenerando..." else "Regenerar con este feedback"
        binding.layoutRegenerando.visibility = if (regenerating) View.VISIBLE else View.GONE
    }

    private fun openInGoogleMaps() {
        val url = RouteShareUrlBuilder.buildGoogleMapsUrl(activity.currentRoute, activity.routeSpots)
        if (url == null) {
            Snackbar.make(binding.root, "No hay suficientes paradas con coordenadas", Snackbar.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareAsText() {
        val text = RouteShareUrlBuilder.buildShareText(activity.currentRoute, activity.routeSpots)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Compartir ruta"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
