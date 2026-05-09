package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.joseibarra.touristnotify.databinding.FragmentEditorTabEnrichmentBinding
import kotlinx.coroutines.launch

class EditorTabTagsAiFragment : Fragment() {

    private var _binding: FragmentEditorTabEnrichmentBinding? = null
    private val binding get() = _binding!!

    private val service = EnrichmentService()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabEnrichmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot

        binding.etTags.setText(spot.tags.joinToString(", "))
        binding.etHistoria.setText(spot.historiaResumen)
        binding.etTips.setText(spot.tipsVisita.joinToString("\n"))

        binding.tvEnrichmentMeta.text =
            "Versión: ${spot.enrichment.version} · Fuente: ${spot.enrichment.source.ifBlank { "—" }}" +
            " · Confianza: ${"%.0f".format(spot.enrichment.confidenceScore * 100)}%"

        binding.btnEnrichWithAi.setOnClickListener { startEnrichment() }
    }

    private fun startEnrichment() {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot
        binding.btnEnrichWithAi.isEnabled = false
        binding.progressEnrichment.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = service.enrichOne(spot)
            binding.progressEnrichment.visibility = View.GONE
            binding.btnEnrichWithAi.isEnabled = true

            result.onSuccess { updated ->
                binding.etTags.setText(updated.tags.joinToString(", "))
                binding.etHistoria.setText(updated.historiaResumen)
                binding.etTips.setText(updated.tipsVisita.joinToString("\n"))
                binding.tvEnrichmentMeta.text =
                    "Versión: ${updated.enrichment.version} · Fuente: ${updated.enrichment.source}" +
                    " · Confianza: ${"%.0f".format(updated.enrichment.confidenceScore * 100)}%"
                (requireActivity() as AdminPlaceEditorActivity).onSpotEnriched(updated)
            }
            result.onFailure { e ->
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, "Error: ${e.message}", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["tags"]            = binding.etTags.text?.toString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        map["historiaResumen"] = binding.etHistoria.text?.toString()?.trim()
        map["tipsVisita"]      = binding.etTips.text?.toString()
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
