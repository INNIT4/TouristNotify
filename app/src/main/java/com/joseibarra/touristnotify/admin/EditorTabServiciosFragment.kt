package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabServiciosBinding

class EditorTabServiciosFragment : Fragment() {

    private var _binding: FragmentEditorTabServiciosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabServiciosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val s = (requireActivity() as AdminPlaceEditorActivity).currentSpot.servicios
        binding.swWifi.isChecked              = s.wifi
        binding.swAireAcondicionado.isChecked = s.aireAcondicionado
        binding.swTomasCorriente.isChecked    = s.tomasCorriente
        binding.swAceptaTarjeta.isChecked     = s.aceptaTarjeta
        binding.swReservacion.isChecked       = s.reservacionRequerida
        binding.swTourGuiado.isChecked        = s.tourGuiado
        binding.swAudioguia.isChecked         = s.audioguia
        binding.etIdiomas.setText(s.idiomas.joinToString(", "))
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["servicios.wifi"]                = binding.swWifi.isChecked
        map["servicios.aireAcondicionado"]   = binding.swAireAcondicionado.isChecked
        map["servicios.tomasCorriente"]      = binding.swTomasCorriente.isChecked
        map["servicios.aceptaTarjeta"]       = binding.swAceptaTarjeta.isChecked
        map["servicios.reservacionRequerida"] = binding.swReservacion.isChecked
        map["servicios.tourGuiado"]          = binding.swTourGuiado.isChecked
        map["servicios.audioguia"]           = binding.swAudioguia.isChecked
        map["servicios.idiomas"]             = binding.etIdiomas.text?.toString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
