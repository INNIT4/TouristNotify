package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabPrecioBinding
import com.joseibarra.touristnotify.model.TipoActividad

class EditorTabPrecioFragment : Fragment() {

    private var _binding: FragmentEditorTabPrecioBinding? = null
    private val binding get() = _binding!!

    private val tiposActividad = TipoActividad.values().map { it.name }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabPrecioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tiposActividad)
        binding.actvTipoActividad.setAdapter(adapter)
        binding.actvTipoActividad.setText(spot.tipoActividad, false)

        binding.swEntradaGratuita.isChecked = spot.entradaGratuita
        binding.sliderPrecioNivel.progress = spot.precioNivel.coerceIn(0, 4)
        updatePrecioLabel(spot.precioNivel)
        binding.sliderPrecioNivel.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) = updatePrecioLabel(p)
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
        })

        if (spot.precioPromedioMxn > 0) binding.etPrecioPromedio.setText(spot.precioPromedioMxn.toString())
        binding.etDuracionMin.setText(spot.duracionMinSugeridaMin.toString())
        binding.etDuracionMax.setText(spot.duracionMaxSugeridaMin.toString())
        binding.etMejorMomento.setText(spot.mejorMomentoDelDia.joinToString(", "))
        binding.etMejorTemporada.setText(spot.mejorTemporada.joinToString(", "))
        binding.etEpocasEvitar.setText(spot.epocasEvitar.joinToString(", "))
    }

    private fun updatePrecioLabel(nivel: Int) {
        binding.tvPrecioNivelLabel.text = when (nivel) {
            0 -> "0 — Gratis"
            1 -> "1 — \$"
            2 -> "2 — \$\$"
            3 -> "3 — \$\$\$"
            4 -> "4 — \$\$\$\$"
            else -> nivel.toString()
        }
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["tipoActividad"]          = binding.actvTipoActividad.text?.toString()?.trim()
        map["entradaGratuita"]        = binding.swEntradaGratuita.isChecked
        map["precioNivel"]            = binding.sliderPrecioNivel.progress
        map["precioPromedioMxn"]      = binding.etPrecioPromedio.text?.toString()?.toIntOrNull() ?: 0
        map["duracionMinSugeridaMin"] = binding.etDuracionMin.text?.toString()?.toIntOrNull() ?: 20
        map["duracionMaxSugeridaMin"] = binding.etDuracionMax.text?.toString()?.toIntOrNull() ?: 45
        map["mejorMomentoDelDia"]     = binding.etMejorMomento.text?.toString()
            ?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }
        map["mejorTemporada"]         = binding.etMejorTemporada.text?.toString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        map["epocasEvitar"]           = binding.etEpocasEvitar.text?.toString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
