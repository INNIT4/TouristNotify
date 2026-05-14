package com.joseibarra.trazago.result

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.joseibarra.trazago.R
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.AuthManager
import com.joseibarra.trazago.Route
import com.joseibarra.trazago.databinding.FragmentRouteRegenShareBinding
import com.joseibarra.trazago.routegen.RouteGenerationCoordinator
import kotlinx.coroutines.launch
import java.util.UUID

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
        binding.btnGuardarRuta.setOnClickListener { saveRoute() }
        binding.btnAbrirGoogleMaps.setOnClickListener { openInGoogleMaps() }
        binding.btnCompartirTexto.setOnClickListener { shareAsText() }
    }

    private fun buildFeedbackText(): String {
        val selectedPresets = buildList {
            if (binding.chipMasRelajada.isChecked) add(getString(R.string.ai_preset_relaxed))
            if (binding.chipMasBarata.isChecked) add(getString(R.string.ai_preset_cheaper))
            if (binding.chipMasCultural.isChecked) add(getString(R.string.ai_preset_cultural))
            if (binding.chipMenosCaminata.isChecked) add(getString(R.string.ai_preset_less_walking))
            if (binding.chipMasGastronomia.isChecked) add(getString(R.string.ai_preset_food))
            if (binding.chipMasNaturaleza.isChecked) add(getString(R.string.ai_preset_nature))
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
            Snackbar.make(binding.root, getString(R.string.regen_select_preset), Snackbar.LENGTH_SHORT).show()
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
                    Snackbar.make(binding.root, getString(R.string.regen_success), Snackbar.LENGTH_SHORT).show()
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

    private fun saveRoute() {
        if (!AuthManager.isAuthenticated()) {
            Snackbar.make(binding.root, getString(R.string.regen_login_required), Snackbar.LENGTH_SHORT).show()
            return
        }
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val route = activity.currentRoute
        val spots = activity.routeSpots

        val totalMin = route.metricas.tiempoTotalMin
        val duracion = if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}min" else "${totalMin}min"
        val distanciaM = route.metricas.distanciaCaminadaMetros
        val distancia = if (distanciaM >= 1000) "${"%.1f".format(distanciaM / 1000.0)} km" else "${distanciaM} m"

        val doc = Route(
            id_ruta = UUID.randomUUID().toString(),
            id_usuario = userId,
            nombre_ruta = route.resumen.titulo,
            descripcion = route.resumen.descripcion,
            pdis_incluidos = route.paradas.map { it.placeId },
            duracion_estimada = duracion,
            distancia_total = distancia
        )

        binding.btnGuardarRuta.isEnabled = false
        FirebaseFirestore.getInstance()
            .collection("rutas")
            .document(doc.id_ruta)
            .set(doc)
            .addOnSuccessListener {
                if (_binding != null) {
                    Snackbar.make(binding.root, getString(R.string.regen_saved), Snackbar.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (_binding != null) {
                    binding.btnGuardarRuta.isEnabled = true
                    Snackbar.make(binding.root, getString(R.string.regen_save_error, e.message ?: ""), Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    /** Re-usa las preferencias del intent de la Activity (ya viene en la ruta). */
    private fun buildCurrentPrefs() = com.joseibarra.trazago.model.UserRoutePreferences()

    private fun setRegeneratingUi(regenerating: Boolean) {
        binding.btnRegenerar.isEnabled = !regenerating
        binding.btnRegenerar.text = if (regenerating) "Regenerando..." else "Regenerar con este feedback"
        binding.layoutRegenerando.visibility = if (regenerating) View.VISIBLE else View.GONE
    }

    private fun openInGoogleMaps() {
        val url = RouteShareUrlBuilder.buildGoogleMapsUrl(activity.currentRoute, activity.routeSpots)
        if (url == null) {
            Snackbar.make(binding.root, getString(R.string.regen_no_coordinates), Snackbar.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareAsText() {
        val text = RouteShareUrlBuilder.buildShareText(requireContext(), activity.currentRoute, activity.routeSpots)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_route)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
