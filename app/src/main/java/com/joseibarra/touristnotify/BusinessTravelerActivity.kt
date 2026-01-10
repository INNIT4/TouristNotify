package com.joseibarra.touristnotify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityBusinessTravelerBinding

/**
 * Activity para el Modo Viajero de Negocios
 * Filtra y muestra lugares útiles para trabajar y hacer negocios
 */
class BusinessTravelerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessTravelerBinding
    private val db = FirebaseFirestore.getInstance()
    private val businessPlaces = mutableListOf<TouristSpot>()
    private lateinit var adapter: BusinessPlacesAdapter

    // Categorías relevantes para viajeros de negocios
    private val businessCategories = listOf(
        "Restaurante",
        "Café",
        "Hotel",
        "Cafetería",
        "Coworking"
    )

    // Servicios clave para negocios
    private val businessServices = listOf(
        "WiFi",
        "Wi-Fi",
        "Internet",
        "Zona de trabajo",
        "Enchufes",
        "Aire acondicionado"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessTravelerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadBusinessMode()
        loadBusinessPlaces()
    }

    private fun setupUI() {
        // RecyclerView setup
        adapter = BusinessPlacesAdapter(businessPlaces) { place ->
            openPlaceDetails(place)
        }
        binding.businessPlacesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.businessPlacesRecyclerView.adapter = adapter

        // Toggle switch
        binding.businessModeSwitch.isChecked = isBusinessModeEnabled()
        binding.businessModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveBusinessMode(isChecked)
            updateModeDescription(isChecked)
        }

        updateModeDescription(binding.businessModeSwitch.isChecked)

        // Filter chips
        binding.filterAllChip.setOnClickListener {
            binding.filterAllChip.isChecked = true
            binding.filterWifiChip.isChecked = false
            binding.filterQuietChip.isChecked = false
            filterPlaces("all")
        }

        binding.filterWifiChip.setOnClickListener {
            binding.filterAllChip.isChecked = false
            binding.filterWifiChip.isChecked = true
            binding.filterQuietChip.isChecked = false
            filterPlaces("wifi")
        }

        binding.filterQuietChip.setOnClickListener {
            binding.filterAllChip.isChecked = false
            binding.filterWifiChip.isChecked = false
            binding.filterQuietChip.isChecked = true
            filterPlaces("quiet")
        }
    }

    private fun updateModeDescription(isEnabled: Boolean) {
        if (isEnabled) {
            binding.modeDescriptionTextView.text =
                "✅ Modo activo - Mostrando solo lugares para trabajar y hacer negocios"
            binding.businessPlacesRecyclerView.visibility = View.VISIBLE
            binding.emptyStateContainer.visibility = View.GONE
        } else {
            binding.modeDescriptionTextView.text =
                "ℹ️ Activa el modo para ver lugares con WiFi, espacios de trabajo y servicios para negocios"
            if (businessPlaces.isEmpty()) {
                binding.businessPlacesRecyclerView.visibility = View.GONE
                binding.emptyStateContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun loadBusinessPlaces() {
        binding.progressBar.visibility = View.VISIBLE
        binding.businessPlacesRecyclerView.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.GONE

        db.collection("lugares_turisticos")
            .get()
            .addOnSuccessListener { documents ->
                businessPlaces.clear()

                for (document in documents) {
                    val place = document.toObject(TouristSpot::class.java).copy(id = document.id)

                    // Filtrar por categoría o servicios de negocios
                    val hasBusinessCategory = businessCategories.any {
                        place.categoria.contains(it, ignoreCase = true)
                    }

                    val hasBusinessService = place.servicios.any { service ->
                        businessServices.any { businessService ->
                            service.contains(businessService, ignoreCase = true)
                        }
                    }

                    if (hasBusinessCategory || hasBusinessService) {
                        businessPlaces.add(place)
                    }
                }

                // Ordenar por rating
                businessPlaces.sortByDescending { it.rating }

                binding.progressBar.visibility = View.GONE

                if (businessPlaces.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                    binding.businessPlacesRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.businessPlacesRecyclerView.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()

                    // Update count
                    binding.placesCountTextView.text =
                        "${businessPlaces.size} lugares para trabajar"
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyStateContainer.visibility = View.VISIBLE
                NotificationHelper.error(binding.root, "Error al cargar lugares: ${e.message}")
            }
    }

    private fun filterPlaces(filter: String) {
        val filteredList = when (filter) {
            "wifi" -> businessPlaces.filter { place ->
                place.servicios.any { it.contains("WiFi", ignoreCase = true) ||
                                     it.contains("Wi-Fi", ignoreCase = true) ||
                                     it.contains("Internet", ignoreCase = true) }
            }
            "quiet" -> businessPlaces.filter { place ->
                place.categoria.contains("Café", ignoreCase = true) ||
                place.categoria.contains("Cafetería", ignoreCase = true) ||
                place.servicios.any { it.contains("Zona tranquila", ignoreCase = true) }
            }
            else -> businessPlaces
        }

        adapter.updatePlaces(filteredList)
        binding.placesCountTextView.text = "${filteredList.size} lugares para trabajar"
    }

    private fun openPlaceDetails(place: TouristSpot) {
        val intent = Intent(this, PlaceDetailsActivity::class.java)
        intent.putExtra("PLACE_ID", place.id)
        intent.putExtra("PLACE_NAME", place.nombre)
        startActivity(intent)
    }

    // SharedPreferences para guardar el estado del modo
    private fun isBusinessModeEnabled(): Boolean {
        val prefs = getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("business_mode_enabled", false)
    }

    private fun saveBusinessMode(enabled: Boolean) {
        val prefs = getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("business_mode_enabled", enabled).apply()

        if (enabled) {
            NotificationHelper.success(binding.root, "Modo Viajero de Negocios activado")
        } else {
            NotificationHelper.info(binding.root, "Modo desactivado")
        }
    }

    private fun loadBusinessMode() {
        binding.businessModeSwitch.isChecked = isBusinessModeEnabled()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("business_mode_enabled", false)
        }
    }
}
