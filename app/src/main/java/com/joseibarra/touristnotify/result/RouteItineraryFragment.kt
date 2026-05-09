package com.joseibarra.touristnotify.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.joseibarra.touristnotify.databinding.FragmentRouteItineraryBinding

/**
 * Tab 2 — Itinerario cronológico de la ruta.
 * Muestra cada parada como una card enriquecida con foto, horario, razón IA y tips.
 */
class RouteItineraryFragment : Fragment() {

    private var _binding: FragmentRouteItineraryBinding? = null
    private val binding get() = _binding!!

    private val adapter = RouteItineraryAdapter()
    private val activity get() = requireActivity() as RouteResultActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteItineraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerParadas.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RouteItineraryFragment.adapter
        }
        loadData()
    }

    /** Called by [RouteResultActivity.updateRoute] when the route changes. */
    fun onRouteUpdated() {
        if (_binding != null) loadData()
    }

    private fun loadData() {
        val route = activity.currentRoute
        val spots = activity.routeSpots
        val spotsById = spots.associateBy { it.id }

        binding.tvRutaTitulo.text = route.resumen.titulo.ifBlank { "Tu ruta" }
        binding.tvRutaDescripcion.text = route.resumen.descripcion

        val items = route.paradas.map { stop -> stop to spotsById[stop.placeId] }
        adapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
