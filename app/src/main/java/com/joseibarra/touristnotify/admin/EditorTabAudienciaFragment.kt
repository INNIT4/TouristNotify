package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabAudienciaBinding
import com.joseibarra.touristnotify.model.AudienciaIdeal

class EditorTabAudienciaFragment : Fragment() {

    private var _binding: FragmentEditorTabAudienciaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabAudienciaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot
        binding.swAptoNinos.isChecked    = spot.aptoNiños
        binding.swAptoMascotas.isChecked = spot.aptoMascotas
        binding.sliderDificultad.progress = (spot.nivelDificultadFisica - 1).coerceIn(0, 4)
        updateDificultadLabel(spot.nivelDificultadFisica)
        binding.sliderDificultad.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) = updateDificultadLabel(p + 1)
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
        })

        val ids = spot.audienciaIdeal.toSet()
        binding.cbSolo.isChecked    = AudienciaIdeal.SOLO.name in ids
        binding.cbPareja.isChecked  = AudienciaIdeal.PAREJA.name in ids
        binding.cbFamilia.isChecked = AudienciaIdeal.FAMILIA.name in ids
        binding.cbAmigos.isChecked  = AudienciaIdeal.AMIGOS.name in ids
        binding.cbNinos.isChecked   = AudienciaIdeal.NIÑOS.name in ids
        binding.cbMayores.isChecked = AudienciaIdeal.MAYORES.name in ids
    }

    private fun updateDificultadLabel(nivel: Int) {
        binding.tvDificultadLabel.text = when (nivel) {
            1 -> "1 — Muy fácil (apto para todos)"
            2 -> "2 — Fácil"
            3 -> "3 — Moderado"
            4 -> "4 — Difícil"
            5 -> "5 — Muy difícil (físicamente exigente)"
            else -> nivel.toString()
        }
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["aptoNiños"]           = binding.swAptoNinos.isChecked
        map["aptoMascotas"]        = binding.swAptoMascotas.isChecked
        map["nivelDificultadFisica"] = binding.sliderDificultad.progress + 1
        val audiencia = mutableListOf<String>()
        if (binding.cbSolo.isChecked)    audiencia.add(AudienciaIdeal.SOLO.name)
        if (binding.cbPareja.isChecked)  audiencia.add(AudienciaIdeal.PAREJA.name)
        if (binding.cbFamilia.isChecked) audiencia.add(AudienciaIdeal.FAMILIA.name)
        if (binding.cbAmigos.isChecked)  audiencia.add(AudienciaIdeal.AMIGOS.name)
        if (binding.cbNinos.isChecked)   audiencia.add(AudienciaIdeal.NIÑOS.name)
        if (binding.cbMayores.isChecked) audiencia.add(AudienciaIdeal.MAYORES.name)
        map["audienciaIdeal"] = audiencia
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
