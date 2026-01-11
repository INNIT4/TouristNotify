package com.joseibarra.touristnotify

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceTypes
import com.google.android.libraries.places.api.net.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.joseibarra.touristnotify.databinding.ActivityAdminPlacesBinding

/**
 * Actividad administrativa para importar lugares desde Google Places API
 * IMPORTANTE: Esta actividad es solo para administradores
 */
class AdminPlacesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPlacesBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: PlaceImportAdapter
    private val foundPlaces = mutableListOf<PlaceImportItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        placesClient = Places.createClient(this)

        setupRecyclerView()
        setupButtons()
    }

    private fun setupRecyclerView() {
        adapter = PlaceImportAdapter(
            places = foundPlaces,
            onImportClicked = { place -> showImportDialog(place) },
            onDetailsClicked = { place -> showPlaceDetails(place) }
        )
        binding.placesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.placesRecyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.buttonSearchTouristSpots.setOnClickListener {
            searchTouristSpots()
        }

        binding.buttonSearchRestaurants.setOnClickListener {
            searchPlacesByType("restaurant")
        }

        binding.buttonSearchHotels.setOnClickListener {
            searchPlacesByType("lodging")
        }

        binding.buttonSearchMuseums.setOnClickListener {
            searchPlacesByType("museum")
        }

        binding.buttonSearchAll.setOnClickListener {
            searchAllCategories()
        }
    }

    private fun searchTouristSpots() {
        showLoading(true)
        searchPlacesByType("tourist_attraction")
    }

    private fun searchPlacesByType(type: String) {
        showLoading(true)
        foundPlaces.clear()
        adapter.notifyDataSetChanged()

        // Coordenadas de Álamos, Sonora
        val alamosCenter = com.google.android.gms.maps.model.LatLng(27.0275, -108.94)

        // Búsqueda de texto para encontrar lugares en Álamos
        val searchQuery = when(type) {
            "restaurant" -> "restaurantes en Álamos, Sonora"
            "lodging" -> "hoteles en Álamos, Sonora"
            "museum" -> "museos en Álamos, Sonora"
            "tourist_attraction" -> "lugares turísticos en Álamos, Sonora"
            else -> "puntos de interés en Álamos, Sonora"
        }

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(searchQuery)
            .setLocationBias(
                com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                    com.google.android.gms.maps.model.LatLng(26.98, -109.0),
                    com.google.android.gms.maps.model.LatLng(27.06, -108.9)
                )
            )
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val placeIds = response.autocompletePredictions.map { it.placeId }
                fetchPlaceDetails(placeIds)
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                if (exception is ApiException) {
                    NotificationHelper.error(binding.root, "Error API: ${exception.statusCode}")
                } else {
                    NotificationHelper.error(binding.root, "Error: ${exception.message}")
                }
            }
    }

    private fun fetchPlaceDetails(placeIds: List<String>) {
        if (placeIds.isEmpty()) {
            showLoading(false)
            NotificationHelper.info(binding.root, "No se encontraron lugares")
            return
        }

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.TYPES,
            Place.Field.OPENING_HOURS,
            Place.Field.PRICE_LEVEL,
            Place.Field.PHOTO_METADATAS
        )

        var processedCount = 0
        placeIds.forEach { placeId ->
            val request = FetchPlaceRequest.newInstance(placeId, placeFields)

            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    val importItem = PlaceImportItem(
                        placeId = place.id ?: "",
                        name = place.name ?: "",
                        address = place.address ?: "",
                        latLng = place.latLng,
                        phoneNumber = place.phoneNumber ?: "",
                        website = place.websiteUri?.toString() ?: "",
                        rating = place.rating ?: 0.0,
                        userRatingsTotal = place.userRatingsTotal ?: 0,
                        types = place.types?.map { it.name } ?: emptyList(),
                        openingHours = place.openingHours?.weekdayText?.joinToString("\n") ?: "",
                        priceLevel = place.priceLevel ?: 0,
                        hasPhotos = !place.photoMetadatas.isNullOrEmpty()
                    )
                    foundPlaces.add(importItem)

                    processedCount++
                    if (processedCount == placeIds.size) {
                        showLoading(false)
                        adapter.notifyDataSetChanged()
                        NotificationHelper.success(binding.root, "Se encontraron ${foundPlaces.size} lugares")
                    }
                }
                .addOnFailureListener {
                    processedCount++
                    if (processedCount == placeIds.size) {
                        showLoading(false)
                        adapter.notifyDataSetChanged()
                    }
                }
        }
    }

    private fun searchAllCategories() {
        showLoading(true)
        foundPlaces.clear()

        val categories = listOf("tourist_attraction", "restaurant", "lodging", "museum")
        var completedCategories = 0

        categories.forEach { category ->
            searchPlacesByType(category)
            completedCategories++
        }
    }

    private fun showImportDialog(place: PlaceImportItem) {
        val categories = arrayOf("Museo", "Restaurante", "Hotel", "Iglesia", "Parque", "Tienda", "Atracción Turística", "Otro")
        var selectedCategory = guessCategory(place.types)
        var selectedCategoryIndex = categories.indexOf(selectedCategory)

        AlertDialog.Builder(this)
            .setTitle("Importar: ${place.name}")
            .setMessage("¿Deseas importar este lugar a Firebase?\n\nSelecciona la categoría:")
            .setSingleChoiceItems(categories, selectedCategoryIndex) { _, which ->
                selectedCategoryIndex = which
                selectedCategory = categories[which]
            }
            .setPositiveButton("Importar") { _, _ ->
                importPlaceToFirebase(place, selectedCategory)
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Ver Detalles") { _, _ ->
                showPlaceDetails(place)
            }
            .show()
    }

    private fun guessCategory(types: List<String>): String {
        return when {
            types.contains("museum") -> "Museo"
            types.contains("restaurant") || types.contains("food") -> "Restaurante"
            types.contains("lodging") || types.contains("hotel") -> "Hotel"
            types.contains("church") || types.contains("place_of_worship") -> "Iglesia"
            types.contains("park") -> "Parque"
            types.contains("store") || types.contains("shopping_mall") -> "Tienda"
            types.contains("tourist_attraction") -> "Atracción Turística"
            else -> "Otro"
        }
    }

    private fun importPlaceToFirebase(place: PlaceImportItem, category: String) {
        val newPlace = TouristSpot(
            id = "",  // Firestore generará el ID
            nombre = place.name,
            descripcion = "Importado desde Google Places. Agregar descripción personalizada.",
            categoria = category,
            ubicacion = place.latLng?.let { GeoPoint(it.latitude, it.longitude) },
            horarios = place.openingHours,
            telefono = place.phoneNumber,
            sitioWeb = place.website,
            direccion = place.address,
            rating = place.rating,
            reviewCount = place.userRatingsTotal,
            precioEstimado = getPriceString(place.priceLevel),
            googlePlaceId = place.placeId,
            visitCount = 0
        )

        db.collection("lugares")
            .add(newPlace)
            .addOnSuccessListener { documentReference ->
                NotificationHelper.success(binding.root, "✓ '${place.name}' importado exitosamente")
                foundPlaces.remove(place)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                NotificationHelper.error(binding.root, "Error al importar: ${e.message}")
            }
    }

    private fun getPriceString(priceLevel: Int): String {
        return when (priceLevel) {
            0 -> "Gratis"
            1 -> "$"
            2 -> "$$"
            3 -> "$$$"
            4 -> "$$$$"
            else -> ""
        }
    }

    private fun showPlaceDetails(place: PlaceImportItem) {
        val details = buildString {
            append("Nombre: ${place.name}\n\n")
            append("Dirección: ${place.address}\n\n")
            append("Teléfono: ${place.phoneNumber.ifBlank { "No disponible" }}\n\n")
            append("Sitio web: ${place.website.ifBlank { "No disponible" }}\n\n")
            append("Rating: ${place.rating} (${place.userRatingsTotal} reseñas)\n\n")
            append("Precio: ${getPriceString(place.priceLevel)}\n\n")
            append("Horarios:\n${place.openingHours.ifBlank { "No disponibles" }}\n\n")
            append("Tipos: ${place.types.joinToString(", ")}")
        }

        AlertDialog.Builder(this)
            .setTitle("Detalles del Lugar")
            .setMessage(details)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.placesRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}

data class PlaceImportItem(
    val placeId: String,
    val name: String,
    val address: String,
    val latLng: com.google.android.gms.maps.model.LatLng?,
    val phoneNumber: String,
    val website: String,
    val rating: Double,
    val userRatingsTotal: Int,
    val types: List<String>,
    val openingHours: String,
    val priceLevel: Int,
    val hasPhotos: Boolean
)
