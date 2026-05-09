package com.joseibarra.touristnotify.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.joseibarra.touristnotify.BuildConfig
import com.joseibarra.touristnotify.MarkerRenderer
import com.joseibarra.touristnotify.R
import com.joseibarra.touristnotify.RoutePolylineManager
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.databinding.FragmentRouteMapBinding
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Tab 1 — Mapa de la ruta generada.
 * Muestra markers numerados y una polyline animada entre las paradas.
 */
class RouteMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentRouteMapBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var markerRenderer: MarkerRenderer? = null
    private var polylineManager: RoutePolylineManager? = null

    private val activity get() = requireActivity() as RouteResultActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)
        updateMetrics()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true

        markerRenderer = MarkerRenderer(requireContext(), map, viewLifecycleOwner.lifecycleScope)
        polylineManager = RoutePolylineManager(
            map = map,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            apiKey = BuildConfig.DIRECTIONS_API_KEY,
            onError = { /* polyline errors are non-fatal */ },
            http = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build(),
            context = requireContext()
        )

        renderRoute(activity.routeSpots)
    }

    /** Called by [RouteResultActivity.updateRoute] when the route changes. */
    fun onRouteUpdated() {
        googleMap ?: return
        markerRenderer?.clearMarkers()
        polylineManager?.clearRoutes()
        renderRoute(activity.routeSpots)
        updateMetrics()
    }

    private fun renderRoute(spots: List<TouristSpot>) {
        val map = googleMap ?: return
        val validSpots = spots.filter { it.ubicacion != null }
        if (validSpots.isEmpty()) return

        // Añadir markers numerados
        validSpots.forEachIndexed { index, spot ->
            markerRenderer?.addMarker(spot, routeIndex = index + 1)
        }

        // Dibujar polyline
        polylineManager?.drawTouristRoute(validSpots)

        // Centrar la cámara en los bounds de la ruta
        val boundsBuilder = LatLngBounds.Builder()
        validSpots.forEach { spot ->
            boundsBuilder.include(
                LatLng(spot.ubicacion!!.latitude, spot.ubicacion!!.longitude)
            )
        }
        try {
            val padding = 120
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding))
        } catch (_: Exception) { }
    }

    private fun updateMetrics() {
        val route = activity.currentRoute
        val metricas = route.metricas

        binding.tvRouteTitulo.text = route.resumen.titulo.ifBlank { "Tu ruta" }
        binding.tvMetricaParadas.text = route.paradas.size.toString()

        val h = metricas.tiempoTotalMin / 60
        val m = metricas.tiempoTotalMin % 60
        binding.tvMetricaTiempo.text = if (m == 0) "${h}h" else "${h}h${m}m"

        binding.tvMetricaCosto.text = if (metricas.costoTotalEstimadoMxn > 0)
            "$${metricas.costoTotalEstimadoMxn}"
        else
            "—"

        if (metricas.advertenciaClima.isNotBlank()) {
            binding.tvAdvertenciaClima.text = "⚠️ ${metricas.advertenciaClima}"
            binding.tvAdvertenciaClima.visibility = View.VISIBLE
        } else {
            binding.tvAdvertenciaClima.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        polylineManager?.clearRoutes()
        markerRenderer?.destroy()
        super.onDestroyView()
        _binding = null
    }
}
