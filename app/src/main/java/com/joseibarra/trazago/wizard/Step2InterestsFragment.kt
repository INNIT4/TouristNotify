package com.joseibarra.trazago.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.joseibarra.trazago.databinding.FragmentStep2InterestsBinding
import com.joseibarra.trazago.model.Ritmo

/**
 * Paso 2: ¿Qué te gusta?
 * Captura: intereses (chips múltiples), ritmo de viaje, tema libre (texto sanitizado).
 */
class Step2InterestsFragment : Fragment() {

    private var _binding: FragmentStep2InterestsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PreferencesViewModel by activityViewModels()

    // Mapa chip-id → etiqueta de interés
    private val chipInteresMap by lazy {
        mapOf(
            binding.chipHistoria.id    to "Historia",
            binding.chipArquitectura.id to "Arquitectura",
            binding.chipGastronomia.id to "Gastronomía",
            binding.chipNaturaleza.id  to "Naturaleza",
            binding.chipCompras.id     to "Compras",
            binding.chipFotografia.id  to "Fotografía",
            binding.chipArte.id        to "Arte",
            binding.chipMusica.id      to "Música",
            binding.chipReligion.id    to "Religión",
            binding.chipAventura.id    to "Aventura",
            binding.chipMirador.id     to "Miradores",
            binding.chipFamilia.id     to "Familiar",
            binding.chipLujo.id        to "Lujo",
            binding.chipEconomico.id   to "Económico",
            binding.chipVidaNocturna.id to "Vida nocturna"
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStep2InterestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreFromViewModel()
        setupListeners()
    }

    private fun restoreFromViewModel() {
        // Marcar chips de intereses guardados
        val saved = viewModel.intereses.toSet()
        for ((chipId, label) in chipInteresMap) {
            binding.chipGroupIntereses.findViewById<com.google.android.material.chip.Chip>(chipId)
                ?.isChecked = label in saved
        }

        // Ritmo
        val ritmoChipId = when (viewModel.ritmo) {
            Ritmo.RELAJADO  -> binding.chipRitmoRelajado.id
            Ritmo.MODERADO  -> binding.chipRitmoModerado.id
            Ritmo.INTENSO   -> binding.chipRitmoIntensivo.id
        }
        binding.chipGroupRitmo.check(ritmoChipId)

        // Tema libre
        binding.etTemaLibre.setText(viewModel.temaSolicitudLibre)
    }

    private fun setupListeners() {
        binding.chipGroupIntereses.setOnCheckedStateChangeListener { _, _ ->
            viewModel.intereses = selectedIntereses()
        }

        binding.chipGroupRitmo.setOnCheckedStateChangeListener { _, _ ->
            viewModel.ritmo = selectedRitmo()
        }

        binding.etTemaLibre.doAfterTextChanged { text ->
            viewModel.temaSolicitudLibre = text?.toString()?.trim() ?: ""
        }
    }

    private fun selectedIntereses(): List<String> =
        chipInteresMap
            .filter { (chipId, _) ->
                binding.chipGroupIntereses
                    .findViewById<com.google.android.material.chip.Chip>(chipId)
                    ?.isChecked == true
            }
            .values
            .toList()

    private fun selectedRitmo(): Ritmo = when (binding.chipGroupRitmo.checkedChipId) {
        binding.chipRitmoRelajado.id  -> Ritmo.RELAJADO
        binding.chipRitmoIntensivo.id -> Ritmo.INTENSO
        else                          -> Ritmo.MODERADO
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
