package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceTypes
import com.google.android.libraries.places.api.net.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.joseibarra.touristnotify.admin.AdminMigrationActivity
import com.joseibarra.touristnotify.admin.AdminPlaceEditorActivity
import com.joseibarra.touristnotify.admin.EnrichmentService
import com.joseibarra.touristnotify.databinding.ActivityAdminPlacesBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        if (!Places.isInitialized()) {
            val placesKey = getString(R.string.places_api_key)
            if (placesKey.isNotEmpty()) {
                Places.initialize(applicationContext, placesKey)
            }
        }

        if (!Places.isInitialized()) {
            AlertDialog.Builder(this)
                .setTitle("Error de configuración")
                .setMessage("Google Places API no está configurada. Verifica la API key en local.properties.")
                .setPositiveButton("Cerrar") { _, _ -> finish() }
                .show()
            return
        }

        placesClient = Places.createClient(this)

        setupRecyclerView()
        setupButtons()
    }

    private fun setupRecyclerView() {
        adapter = PlaceImportAdapter(
            places = foundPlaces,
            onImportClicked = { place -> showImportDialog(place) },
            onDetailsClicked = { place -> showPlaceDetails(place) },
            onEditClicked = { place ->
                startActivity(AdminPlaceEditorActivity.newIntent(this, place.placeId))
            }
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

        binding.buttonImportAll.setOnClickListener { confirmImportAll() }

        binding.buttonLoadCatalog.setOnClickListener { loadCatalog() }
        binding.buttonBulkEnrich.setOnClickListener { confirmBulkEnrich() }
        binding.buttonGoToMigration.setOnClickListener {
            startActivity(Intent(this, AdminMigrationActivity::class.java))
        }
    }

    private fun loadCatalog() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val snapshot = db.collection(FirestoreCollections.PLACES).get().await()
                val spots = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TouristSpot::class.java)?.copy(id = doc.id)
                }
                foundPlaces.clear()
                spots.forEach { spot ->
                    foundPlaces.add(PlaceImportItem(
                        placeId   = spot.id,
                        name      = spot.nombre,
                        address   = spot.direccion,
                        latLng    = spot.ubicacion?.let {
                            com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                        phoneNumber   = spot.telefono ?: "",
                        website       = spot.sitioWeb ?: "",
                        rating        = spot.rating,
                        userRatingsTotal = spot.reviewCount,
                        types         = listOf(spot.categoria),
                        openingHours  = spot.horarios ?: "",
                        priceLevel    = spot.precioNivel,
                        hasPhotos     = spot.imagenUrl?.isNotBlank() == true
                    ))
                }
                showLoading(false)
                adapter.notifyDataSetChanged()
                NotificationHelper.success(binding.root, "${spots.size} lugares en catálogo")
            } catch (e: Exception) {
                showLoading(false)
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
        }
    }

    private fun confirmBulkEnrich() {
        AlertDialog.Builder(this)
            .setTitle("Enriquecer todos con IA")
            .setMessage("Gemini procesará cada lugar del catálogo (1 cada 2 s). ¿Continuar?")
            .setPositiveButton("Enriquecer") { _, _ -> startBulkEnrichment() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private var bulkCancelled = false

    private fun startBulkEnrichment() {
        bulkCancelled = false
        binding.progressBulkEnrich.visibility = View.VISIBLE
        binding.tvBulkEnrichStatus.visibility = View.VISIBLE
        binding.buttonBulkEnrich.isEnabled = false

        lifecycleScope.launch {
            try {
                val snapshot = db.collection(FirestoreCollections.PLACES).get().await()
                val spots = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TouristSpot::class.java)?.copy(id = doc.id)
                }
                binding.progressBulkEnrich.max = spots.size
                val service = EnrichmentService()
                service.enrichAll(
                    spots = spots,
                    throttleMs = 2000L,
                    onProgress = { done, total, name, success ->
                        binding.progressBulkEnrich.progress = done
                        val status = if (success) "✓" else "✗"
                        binding.tvBulkEnrichStatus.text = "$status $done/$total · $name"
                    },
                    isCancelled = { bulkCancelled }
                )
                binding.tvBulkEnrichStatus.text = "Enriquecimiento completo"
            } catch (e: Exception) {
                binding.tvBulkEnrichStatus.text = "Error: ${e.message}"
            } finally {
                binding.progressBulkEnrich.visibility = View.GONE
                binding.buttonBulkEnrich.isEnabled = true
            }
        }
    }

    private fun confirmImportAll() {
        if (foundPlaces.isEmpty()) {
            NotificationHelper.info(binding.root, "No hay lugares en la lista para importar")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Importar ${foundPlaces.size} lugares")
            .setMessage("Se guardarán todos los resultados en Firebase. Las categorías se asignan automáticamente.")
            .setPositiveButton("Importar todos") { _, _ -> doBulkImport() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doBulkImport() {
        binding.progressImport.visibility = View.VISIBLE
        binding.tvImportStatus.visibility = View.VISIBLE
        binding.buttonImportAll.isEnabled = false

        lifecycleScope.launch {
            try {
                val placesToImport = foundPlaces.toList()
                binding.progressImport.max = placesToImport.size
                var imported = 0

                placesToImport.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { place ->
                        val category = CategoryUtils.guessCategory(place.types)
                        val newPlace = TouristSpot(
                            id = "",
                            nombre = place.name,
                            descripcion = "Importado desde Google Places.",
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
                        batch.set(db.collection(FirestoreCollections.PLACES).document(), newPlace)
                        imported++
                        binding.progressImport.progress = imported
                        binding.tvImportStatus.text = "Importando $imported/${placesToImport.size}…"
                    }
                    batch.commit().await()
                }

                binding.tvImportStatus.text = "✓ $imported lugares importados"
                foundPlaces.clear()
                adapter.notifyDataSetChanged()
                NotificationHelper.success(binding.root, "✓ $imported lugares importados a Firebase")
            } catch (e: Exception) {
                binding.tvImportStatus.text = "Error: ${e.message}"
                NotificationHelper.error(binding.root, "Error al importar: ${e.message}")
            } finally {
                binding.progressImport.visibility = View.GONE
                binding.buttonImportAll.isEnabled = true
            }
        }
    }

    private fun searchTouristSpots() {
        showLoading(true)
        searchPlacesByType("tourist_attraction")
    }

    private fun searchPlacesByType(type: String, accumulate: Boolean = false) {
        if (!accumulate) {
            showLoading(true)
            foundPlaces.clear()
            adapter.notifyDataSetChanged()
        }

        // Coordenadas de Álamos, Sonora
        val alamosCenter = com.google.android.gms.maps.model.LatLng(AppConstants.ALAMOS_LAT, AppConstants.ALAMOS_LNG)

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
        adapter.notifyDataSetChanged()

        val categories = listOf("tourist_attraction", "restaurant", "lodging", "museum")
        categories.forEach { category -> searchPlacesByType(category, accumulate = true) }
    }

    private fun showImportDialog(place: PlaceImportItem) {
        val categories = arrayOf("Museo", "Restaurante", "Hotel", "Iglesia", "Parque", "Tienda", "Atracción Turística", "Otro")
        var selectedCategory = CategoryUtils.guessCategory(place.types)
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
