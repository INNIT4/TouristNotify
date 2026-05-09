package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabBasicBinding

class EditorTabBasicFragment : Fragment() {

    private var _binding: FragmentEditorTabBasicBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        "Museo", "Iglesia", "Plaza", "Parque", "Restaurante", "Hotel",
        "Galería", "Mercado", "Mirador", "Monumento", "Teatro", "General"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabBasicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spot = (requireActivity() as AdminPlaceEditorActivity).currentSpot

        binding.etNombre.setText(spot.nombre)
        binding.etSlug.setText(spot.slug)
        binding.etDescripcion.setText(spot.descripcion)
        binding.etDescripcionCorta.setText(spot.descripcionCorta)
        binding.etDireccion.setText(spot.direccion)
        binding.etTelefono.setText(spot.telefono)
        binding.etSitioWeb.setText(spot.sitioWeb)
        binding.etBarrio.setText(spot.barrio)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategoria.setAdapter(adapter)
        binding.actvCategoria.setText(spot.categoria, false)
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["nombre"]          = binding.etNombre.text?.toString()?.trim()
        map["slug"]            = binding.etSlug.text?.toString()?.trim()
        map["categoria"]       = binding.actvCategoria.text?.toString()?.trim()
        map["descripcion"]     = binding.etDescripcion.text?.toString()?.trim()
        map["descripcionCorta"] = binding.etDescripcionCorta.text?.toString()?.trim()
        map["direccion"]       = binding.etDireccion.text?.toString()?.trim()
        map["telefono"]        = binding.etTelefono.text?.toString()?.trim()
        map["sitioWeb"]        = binding.etSitioWeb.text?.toString()?.trim()
        map["barrio"]          = binding.etBarrio.text?.toString()?.trim()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
