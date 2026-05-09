package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.databinding.FragmentEditorTabRestauranteBinding
import com.joseibarra.touristnotify.model.OpcionDietetica

class EditorTabRestauranteFragment : Fragment() {

    private var _binding: FragmentEditorTabRestauranteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorTabRestauranteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val r = (requireActivity() as AdminPlaceEditorActivity).currentSpot.restaurante
        binding.etTipoCocina.setText(r.tipoCocina.joinToString(", "))
        binding.swTerraza.isChecked = r.tieneTerraza
        binding.etPlatosRecomendados.setText(r.platosRecomendados.joinToString("\n"))

        val opts = r.opcionesDieteticas.toSet()
        binding.cbVegetariano.isChecked = OpcionDietetica.VEGETARIANO.name in opts
        binding.cbVegano.isChecked      = OpcionDietetica.VEGANO.name in opts
        binding.cbSinGluten.isChecked   = OpcionDietetica.SIN_GLUTEN.name in opts
        binding.cbHalal.isChecked       = OpcionDietetica.HALAL.name in opts
        binding.cbKosher.isChecked      = OpcionDietetica.KOSHER.name in opts
        binding.cbSinLactosa.isChecked  = OpcionDietetica.SIN_LACTOSA.name in opts
    }

    fun collectInto(map: MutableMap<String, Any?>) {
        map["restaurante.tipoCocina"] = binding.etTipoCocina.text?.toString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        map["restaurante.tieneTerraza"] = binding.swTerraza.isChecked
        map["restaurante.platosRecomendados"] = binding.etPlatosRecomendados.text?.toString()
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }
        val dieta = mutableListOf<String>()
        if (binding.cbVegetariano.isChecked) dieta.add(OpcionDietetica.VEGETARIANO.name)
        if (binding.cbVegano.isChecked)      dieta.add(OpcionDietetica.VEGANO.name)
        if (binding.cbSinGluten.isChecked)   dieta.add(OpcionDietetica.SIN_GLUTEN.name)
        if (binding.cbHalal.isChecked)       dieta.add(OpcionDietetica.HALAL.name)
        if (binding.cbKosher.isChecked)      dieta.add(OpcionDietetica.KOSHER.name)
        if (binding.cbSinLactosa.isChecked)  dieta.add(OpcionDietetica.SIN_LACTOSA.name)
        map["restaurante.opcionesDieteticas"] = dieta
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
