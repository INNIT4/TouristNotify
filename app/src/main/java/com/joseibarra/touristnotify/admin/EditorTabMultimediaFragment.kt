package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabMultimediaBinding

class EditorTabMultimediaFragment : Fragment() {

    private var _binding: FragmentEditorTabMultimediaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabMultimediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot
        binding.etImagenUrl.setText(spot.imagenUrl)
        binding.etImagenesGaleria.setText(spot.imagenesGaleria.joinToString("\n"))
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["imagenUrl"] = binding.etImagenUrl.text?.toString()?.trim()
        map["imagenesGaleria"] = binding.etImagenesGaleria.text?.toString()
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
