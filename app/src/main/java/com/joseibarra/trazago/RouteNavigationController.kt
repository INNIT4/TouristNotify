package com.joseibarra.trazago

import android.content.Intent
import android.net.Uri
import android.view.View
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.joseibarra.trazago.databinding.ActivityMapsBinding

class RouteNavigationController(
    private val map: GoogleMap,
    private val onNavigateToDetails: (TouristSpot) -> Unit,
    private val onClose: () -> Unit,
    private val onSave: () -> Unit
) {
    private var spots: List<TouristSpot> = emptyList()
    var currentIndex = 0
        private set
    var isActive: Boolean = false
        private set

    val currentSpot: TouristSpot? get() = spots.getOrNull(currentIndex)

    fun startNavigation(newSpots: List<TouristSpot>, canSave: Boolean, binding: ActivityMapsBinding) {
        spots = newSpots
        currentIndex = 0
        isActive = true
        binding.routeNavigationPanel.visibility = View.VISIBLE
        binding.saveRoutePanelButton.visibility = if (canSave) View.VISIBLE else View.GONE
        binding.saveRoutePanelButton.setOnClickListener { onSave() }
        binding.previousPlaceButton.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                updatePanel(binding)
                centerOnCurrent()
            }
        }
        binding.nextPlaceButton.setOnClickListener {
            if (currentIndex < spots.size - 1) {
                currentIndex++
                updatePanel(binding)
                centerOnCurrent()
            }
        }
        binding.closeRouteButton.setOnClickListener { close(binding) }
        binding.viewDetailsButton.setOnClickListener { currentSpot?.let(onNavigateToDetails) }
        binding.navigateButton.setOnClickListener {
            currentSpot?.ubicacion?.let { location ->
                val gmmUri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
                val intent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                binding.root.context.startActivity(intent)
            }
        }
        updatePanel(binding)
    }

    fun updatePanel(binding: ActivityMapsBinding) {
        if (spots.isEmpty()) return
        val spot = spots[currentIndex]
        binding.routeProgressText.text = binding.root.context.getString(R.string.route_nav_step, currentIndex + 1, spots.size)
        val estimatedMinutes = calculateEstimatedTime(spots.size)
        val hours = estimatedMinutes / 60
        val minutes = estimatedMinutes % 60
        binding.routeTimeEstimateText.text = if (hours > 0) {
            binding.root.context.getString(R.string.route_nav_time_hm, hours, minutes)
        } else {
            binding.root.context.getString(R.string.route_nav_time_m, minutes)
        }
        binding.currentPlaceName.text = spot.nombre
        binding.currentPlaceCategory.text =
            "${CategoryUtils.getCategoryEmoji(spot.categoria)} ${spot.categoria}"
        binding.currentPlaceDescription.text = spot.descripcion
        binding.previousPlaceButton.isEnabled = currentIndex > 0
        binding.nextPlaceButton.isEnabled = currentIndex < spots.size - 1
        binding.previousPlaceButton.alpha = if (currentIndex > 0) 1f else 0.5f
        binding.nextPlaceButton.alpha = if (currentIndex < spots.size - 1) 1f else 0.5f
    }

    fun close(binding: ActivityMapsBinding) {
        isActive = false
        spots = emptyList()
        binding.routeNavigationPanel.visibility = View.GONE
        binding.searchView.visibility = View.VISIBLE
        binding.filterScrollView.visibility = View.VISIBLE
        onClose()
    }

    private fun centerOnCurrent() {
        currentSpot?.ubicacion?.let { location ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17f),
                500,
                null
            )
        }
    }

    private fun calculateEstimatedTime(count: Int): Int = (count * 15) + ((count - 1) * 5)
}
