package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabHorariosBinding

class EditorTabHorariosFragment : Fragment() {

    private var _binding: FragmentEditorTabHorariosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabHorariosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot
        binding.swCerradoTemporalmente.isChecked = spot.cerradoTemporalmente
        binding.etHorariosTexto.setText(
            spot.horariosTextoOriginal.ifBlank { spot.horarios }
        )
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["cerradoTemporalmente"] = binding.swCerradoTemporalmente.isChecked
        map["horariosTextoOriginal"] = binding.etHorariosTexto.text?.toString()?.trim()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
