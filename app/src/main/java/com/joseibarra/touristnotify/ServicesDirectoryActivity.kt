package com.joseibarra.touristnotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.joseibarra.touristnotify.databinding.ActivityServicesDirectoryBinding

class ServicesDirectoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServicesDirectoryBinding

    private val serviceCategories = listOf(
        ServiceCategory("🏥", "Hospitales", "hospital+alamos+sonora"),
        ServiceCategory("💊", "Farmacias", "farmacia+alamos+sonora"),
        ServiceCategory("🏦", "Bancos", "banco+alamos+sonora"),
        ServiceCategory("⛽", "Gasolineras", "gasolinera+alamos+sonora"),
        ServiceCategory("🍽️", "Restaurantes", "restaurante+alamos+sonora"),
        ServiceCategory("🏪", "Tiendas", "tienda+alamos+sonora"),
        ServiceCategory("🚕", "Taxis", "taxi+alamos+sonora"),
        ServiceCategory("🏨", "Hoteles", "hotel+alamos+sonora")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServicesDirectoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        // Aplicar animación de entrada desde abajo (modal style)
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    private fun setupRecyclerView() {
        val adapter = ServiceCategoryAdapter(serviceCategories) { category ->
            openGoogleMaps(category.searchQuery)
        }
        binding.servicesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.servicesRecyclerView.adapter = adapter
    }

    private fun openGoogleMaps(query: String) {
        // Abrir Google Maps con búsqueda
        val gmmIntentUri = Uri.parse("geo:${AppConstants.ALAMOS_LAT},${AppConstants.ALAMOS_LNG}?q=$query")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Fallback: abrir en navegador
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/$query")
            )
            startActivity(browserIntent)
        }
    }
}

data class ServiceCategory(
    val emoji: String,
    val name: String,
    val searchQuery: String
)
