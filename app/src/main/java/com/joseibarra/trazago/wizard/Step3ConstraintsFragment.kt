package com.joseibarra.trazago.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.joseibarra.trazago.databinding.FragmentStep3ConstraintsBinding
import com.joseibarra.trazago.model.Movilidad
import com.joseibarra.trazago.model.OpcionDietetica

/**
 * Paso 3: Restricciones
 * Captura: presupuesto (slider), movilidad, accesibilidad, dieta, switch "incluir comida".
 */
class Step3ConstraintsFragment : Fragment() {

    private var _binding: FragmentStep3ConstraintsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PreferencesViewModel by activityViewModels()

    private val chipDietaMap by lazy {
        mapOf(
            binding.chipVegetariano.id to OpcionDietetica.VEGETARIANO,
            binding.chipVegano.id      to OpcionDietetica.VEGANO,
            binding.chipSinGluten.id   to OpcionDietetica.SIN_GLUTEN,
            binding.chipHalal.id       to OpcionDietetica.HALAL,
            binding.chipSinLactosa.id  to OpcionDietetica.SIN_LACTOSA
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStep3ConstraintsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreFromViewModel()
        setupListeners()
    }

    private fun restoreFromViewModel() {
        // Presupuesto
        binding.sliderPresupuesto.value = viewModel.presupuestoMxn.toFloat()
        updatePresupuestoLabel()

        // Movilidad
        val movilidadChipId = when (viewModel.movilidad) {
            Movilidad.A_PIE -> binding.chipAPie.id
            Movilidad.AUTO  -> binding.chipAuto.id
            Movilidad.MIXTO -> binding.chipMixto.id
        }
        binding.chipGroupMovilidad.check(movilidadChipId)

        // Accesibilidad
        binding.checkSillaRuedas.isChecked = viewModel.sillaRuedas
        binding.checkBanoAccesible.isChecked = viewModel.banoAccesible
        binding.checkEstacionamientoAccesible.isChecked = viewModel.estacionamientoAccesible

        // Dieta
        val savedDieta = viewModel.restriccionesDieteticas.toSet()
        for ((chipId, opcion) in chipDietaMap) {
            binding.chipGroupDieta.findViewById<com.google.android.material.chip.Chip>(chipId)
                ?.isChecked = opcion in savedDieta
        }

        // Incluir comida
        binding.switchIncluirComida.isChecked = viewModel.incluirComida
    }

    private fun setupListeners() {
        binding.sliderPresupuesto.addOnChangeListener { _, value, _ ->
            viewModel.presupuestoMxn = value.toInt()
            updatePresupuestoLabel()
        }

        binding.chipGroupMovilidad.setOnCheckedStateChangeListener { _, _ ->
            viewModel.movilidad = selectedMovilidad()
        }

        binding.checkSillaRuedas.setOnCheckedChangeListener { _, checked ->
            viewModel.sillaRuedas = checked
        }
        binding.checkBanoAccesible.setOnCheckedChangeListener { _, checked ->
            viewModel.banoAccesible = checked
        }
        binding.checkEstacionamientoAccesible.setOnCheckedChangeListener { _, checked ->
            viewModel.estacionamientoAccesible = checked
        }

        binding.chipGroupDieta.setOnCheckedStateChangeListener { _, _ ->
            viewModel.restriccionesDieteticas = selectedDieta()
        }

        binding.switchIncluirComida.setOnCheckedChangeListener { _, checked ->
            viewModel.incluirComida = checked
        }
    }

    private fun updatePresupuestoLabel() {
        binding.tvPresupuestoLabel.text = "$${viewModel.presupuestoMxn} MXN"
    }

    private fun selectedMovilidad(): Movilidad = when (binding.chipGroupMovilidad.checkedChipId) {
        binding.chipAuto.id  -> Movilidad.AUTO
        binding.chipMixto.id -> Movilidad.MIXTO
        else                 -> Movilidad.A_PIE
    }

    private fun selectedDieta(): List<OpcionDietetica> =
        chipDietaMap
            .filter { (chipId, _) ->
                binding.chipGroupDieta
                    .findViewById<com.google.android.material.chip.Chip>(chipId)
                    ?.isChecked == true
            }
            .values
            .toList()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
