package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabAccesibilidadBinding

class EditorTabAccesibilidadFragment : Fragment() {

    private var _binding: FragmentEditorTabAccesibilidadBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabAccesibilidadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val a = (requireActivity() as AdminPlaceEditorActivity).currentSpot.accesibilidad
        binding.swSillaRuedas.isChecked           = a.sillaRuedas
        binding.swBanoAccesible.isChecked         = a.banoAccesible
        binding.swEstacionamiento.isChecked       = a.estacionamiento
        binding.swEstacionamientoAccesible.isChecked = a.estacionamientoAccesible
        binding.swBraille.isChecked               = a.señalizacionBraille
        binding.etNotasAccesibilidad.setText(a.notas)
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["accesibilidad.sillaRuedas"]              = binding.swSillaRuedas.isChecked
        map["accesibilidad.banoAccesible"]            = binding.swBanoAccesible.isChecked
        map["accesibilidad.estacionamiento"]          = binding.swEstacionamiento.isChecked
        map["accesibilidad.estacionamientoAccesible"] = binding.swEstacionamientoAccesible.isChecked
        map["accesibilidad.señalizacionBraille"]      = binding.swBraille.isChecked
        map["accesibilidad.notas"]                    = binding.etNotasAccesibilidad.text?.toString()?.trim()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
