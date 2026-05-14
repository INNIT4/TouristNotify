package com.joseibarra.trazago.result

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
import com.joseibarra.trazago.BuildConfig
import com.joseibarra.trazago.MarkerRenderer
import com.joseibarra.trazago.R
import com.joseibarra.trazago.RoutePolylineManager
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.databinding.FragmentRouteMapBinding
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
    private var currentStopIndex = 0

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
        setupStopNavigation()
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
        currentStopIndex = 0
        renderRoute(activity.routeSpots)
        updateMetrics()
        updateStopCounter()
    }

    private fun setupStopNavigation() {
        binding.btnPrevStop.setOnClickListener { navigateToStop(currentStopIndex - 1) }
        binding.btnNextStop.setOnClickListener { navigateToStop(currentStopIndex + 1) }
        updateStopCounter()
    }

    private fun navigateToStop(index: Int) {
        val spots = activity.routeSpots.filter { it.ubicacion != null }
        if (index < 0 || index >= spots.size) return
        currentStopIndex = index
        updateStopCounter()
        val spot = spots[index]
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(spot.ubicacion!!.latitude, spot.ubicacion!!.longitude),
                17f
            ), 500, null
        )
    }

    private fun updateStopCounter() {
        val total = activity.routeSpots.count { it.ubicacion != null }
        binding.tvStopCounter.text = if (total > 0) "Parada ${currentStopIndex + 1} de $total" else "Sin paradas"
        binding.btnPrevStop.isEnabled = currentStopIndex > 0
        binding.btnNextStop.isEnabled = currentStopIndex < total - 1
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

        binding.tvRouteTitulo.text = route.resumen.titulo.ifBlank { getString(R.string.route_your_route) }
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
