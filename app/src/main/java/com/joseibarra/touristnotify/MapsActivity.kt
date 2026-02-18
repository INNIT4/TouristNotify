package com.joseibarra.touristnotify

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.joseibarra.touristnotify.databinding.ActivityMapsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val touristSpotMarkers = mutableListOf<Marker>()
    private var currentRouteSpots = listOf<TouristSpot>()
    private var routePolyline: Polyline? = null
    private var navigationPolyline: Polyline? = null
    private val selectedCategories = mutableSetOf<String>()
    private var allSpots = listOf<TouristSpot>()
    private var currentPlaceIndex = 0
    private var isNavigatingRoute = false
    private val okHttpClient = OkHttpClient()
    // Generación para evitar que callbacks viejos de Glide añadan marcadores duplicados
    private var markerGeneration = 0

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private val TAG = MapsActivity::class.java.simpleName
    }

    private val placeDetailsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val lat = result.data?.getDoubleExtra("DESTINATION_LAT", 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra("DESTINATION_LNG", 0.0) ?: 0.0
            if (lat != 0.0 && lng != 0.0) {
                getDirectionsTo(LatLng(lat, lng))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val alamosBounds = LatLngBounds(LatLng(26.98, -109.0), LatLng(27.06, -108.9))
        mMap.setLatLngBoundsForCameraTarget(alamosBounds)
        mMap.setMinZoomPreference(13.5f)
        mMap.setMaxZoomPreference(20.0f)

        mMap.setOnMarkerClickListener { marker ->
            val spot = marker.tag as? TouristSpot
            spot?.let {
                val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                    putExtra("PLACE_ID", it.id)
                    putExtra("PLACE_NAME", it.nombre)
                    putExtra("PLACE_CATEGORY", it.categoria)
                    putExtra("PLACE_DESCRIPTION", it.descripcion)
                    putExtra("GOOGLE_PLACE_ID", it.googlePlaceId)
                    it.ubicacion?.let { geo ->
                        putExtra("PLACE_LATITUDE", geo.latitude)
                        putExtra("PLACE_LONGITUDE", geo.longitude)
                    }
                }
                placeDetailsLauncher.launch(intent)
            }
            true
        }

        val alamos = LatLng(27.0275, -108.94)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(alamos, 15f))

        val routePlaceNames = intent.getStringArrayListExtra("ROUTE_PLACES")
        val routePlaceIds = intent.getStringArrayListExtra("ROUTE_PLACES_IDS")

        if (routePlaceIds != null && routePlaceIds.isNotEmpty()) {
            loadRouteByIds(routePlaceIds)
        } else if (routePlaceNames != null && routePlaceNames.isNotEmpty()) {
            loadPersonalizedRoute(routePlaceNames)
        } else {
            setupSearchView()
            setupFilterChips()
            cargarLugaresDesdeFirestore()
        }

        enableMyLocation()
        setupSaveRouteButton()
    }

    private fun setupFilterChips() {
        val categoryMap = mapOf(
            binding.chipMuseo to "museo",
            binding.chipRestaurante to "restaurante",
            binding.chipHotel to "hotel",
            binding.chipIglesia to "iglesia",
            binding.chipParque to "parque",
            binding.chipTienda to "tienda"
        )

        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedCategories.clear()
                categoryMap.keys.forEach { it.isChecked = false }
                applyFilters()
            }
        }

        categoryMap.forEach { (chip, category) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.chipAll.isChecked = false
                    selectedCategories.add(category)
                } else {
                    selectedCategories.remove(category)
                    if (selectedCategories.isEmpty()) {
                        binding.chipAll.isChecked = true
                    }
                }
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        clearAllMarkers()

        val filteredSpots = if (selectedCategories.isEmpty()) {
            allSpots
        } else {
            allSpots.filter { spot ->
                selectedCategories.any { category ->
                    spot.categoria.lowercase().contains(category.lowercase())
                }
            }
        }

        filteredSpots.forEach { addMarkerForTouristSpot(it) }

        if (filteredSpots.isEmpty() && allSpots.isNotEmpty()) {
            NotificationHelper.info(binding.root, "No se encontraron lugares con estos filtros")
        }
    }

    private fun loadRouteByIds(placeIds: List<String>) {
        binding.searchView.visibility = View.GONE
        binding.saveRouteButton.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE

        db.collection("lugares").whereIn(FieldPath.documentId(), placeIds).get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontraron los lugares de la ruta guardada", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val spots = documents.mapNotNull { doc -> doc.toObject(TouristSpot::class.java).copy(id = doc.id) }
                currentRouteSpots = spots.sortedBy { placeIds.indexOf(it.id) }

                currentRouteSpots.forEachIndexed { index, spot -> addMarkerForTouristSpot(spot, index + 1) }
                drawRoutePolyline(currentRouteSpots)

                isNavigatingRoute = true
                currentPlaceIndex = 0
                setupRouteNavigation(canSave = false)
                updateRouteNavigationPanel()

                val boundsBuilder = LatLngBounds.Builder()
                currentRouteSpots.forEach { spot ->
                    spot.ubicacion?.let { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                }
                if (currentRouteSpots.isNotEmpty()) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error cargando ruta por IDs", e)
                Toast.makeText(this, "Error al cargar la ruta guardada: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadPersonalizedRoute(placeNames: List<String>) {
        binding.searchView.visibility = View.GONE
        binding.saveRouteButton.visibility = View.GONE  // El botón guardar va dentro del panel
        binding.filterScrollView.visibility = View.GONE

        db.collection("lugares").whereIn("nombre", placeNames).get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontraron los lugares de la ruta", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val spots = documents.mapNotNull { doc -> doc.toObject(TouristSpot::class.java).copy(id = doc.id) }
                // Mantener el orden original de la IA
                currentRouteSpots = spots.sortedBy { placeNames.indexOf(it.nombre) }

                currentRouteSpots.forEachIndexed { index, spot -> addMarkerForTouristSpot(spot, index + 1) }
                drawRoutePolyline(currentRouteSpots)

                isNavigatingRoute = true
                currentPlaceIndex = 0
                setupRouteNavigation(canSave = true)
                updateRouteNavigationPanel()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error cargando ruta", e)
                Toast.makeText(this, "Error cargando la ruta: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupSaveRouteButton() {
        if (!AuthManager.isAuthenticated()) {
            binding.saveRouteButton.alpha = 0.5f
            binding.saveRouteButton.setImageResource(R.drawable.ic_lock_outline_black_24dp)
        }

        binding.saveRouteButton.setOnClickListener {
            AuthManager.requireAuth(this, AuthManager.AuthRequired.SAVE_ROUTES) {
                if (currentRouteSpots.isNotEmpty()) {
                    showSaveRouteDialog()
                } else {
                    NotificationHelper.warning(binding.root, "Agrega lugares a la ruta primero")
                }
            }
        }
    }

    private fun showSaveRouteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_route, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.route_name_edit_text)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.route_description_edit_text)

        AlertDialog.Builder(this)
            .setTitle("Guardar Ruta")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = nameEditText.text.toString()
                val description = descriptionEditText.text.toString()
                if (name.isNotBlank()) {
                    saveRouteToFirestore(name, description)
                } else {
                    NotificationHelper.warning(binding.root, "El nombre de la ruta no puede estar vacío")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveRouteToFirestore(name: String, description: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentRouteSpots.isEmpty()) {
            Toast.makeText(this, "Error: No hay lugares en la ruta para guardar", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid
        val routeId = db.collection("rutas").document().id

        val newRoute = Route(
            id_ruta = routeId,
            id_usuario = userId,
            nombre_ruta = name,
            descripcion = description,
            pdis_incluidos = currentRouteSpots.map { it.id },
            duracion_estimada = "N/A",
            distancia_total = "N/A",
            estado_ruta = "guardada"
        )

        db.collection("rutas").document(routeId).set(newRoute)
            .addOnSuccessListener {
                NotificationHelper.routeSaved(binding.root, name)
                // Ocultar botón guardar del panel tras guardar exitosamente
                binding.saveRouteButton.visibility = View.GONE
                binding.saveRoutePanelButton.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar ruta", e)
                NotificationHelper.error(binding.root, "Error al guardar la ruta: ${e.message}")
            }
    }

    // =============== MARCADORES CIRCULARES CON IMAGEN ===============

    /**
     * Añade un marcador al mapa. Si el lugar tiene imagen, carga un círculo con Glide.
     * En modo ruta (routeIndex >= 1), muestra un número encima de la imagen.
     */
    private fun addMarkerForTouristSpot(spot: TouristSpot, routeIndex: Int = -1) {
        spot.ubicacion ?: return
        val position = LatLng(spot.ubicacion.latitude, spot.ubicacion.longitude)
        val categoryColor = getCategoryColor(spot.categoria)

        if (spot.imagenUrl.isNotBlank()) {
            // Captura la generación actual — si cambia antes de que Glide responda, ignoramos el callback
            val capturedGen = markerGeneration

            Glide.with(this)
                .asBitmap()
                .load(spot.imagenUrl)
                .apply(
                    RequestOptions()
                        .circleCrop()
                        .override(120, 120)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                )
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        if (capturedGen != markerGeneration) return  // Callback obsoleto, descartamos
                        val markerBitmap = if (routeIndex >= 1) {
                            createCircularBitmapWithNumber(resource, routeIndex, categoryColor)
                        } else {
                            createCircularBitmapWithBorder(resource, categoryColor)
                        }
                        addMarkerToMap(spot, position, markerBitmap)
                    }
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        if (capturedGen != markerGeneration) return
                        // Fallback: círculo numerado o pin de color si falla la imagen
                        addFallbackMarker(spot, position, routeIndex, categoryColor)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } else {
            addFallbackMarker(spot, position, routeIndex, categoryColor)
        }
    }

    private fun addFallbackMarker(spot: TouristSpot, position: LatLng, routeIndex: Int, categoryColor: Int) {
        if (routeIndex >= 1) {
            addMarkerToMap(spot, position, createNumberedCircleMarker(routeIndex, categoryColor))
        } else {
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(spot.nombre)
                    .snippet("${spot.categoria} • ${String.format("%.1f", spot.rating)}⭐")
                    .icon(BitmapDescriptorFactory.defaultMarker(getCategoryHue(spot.categoria)))
            )
            marker?.tag = spot
            marker?.let { touristSpotMarkers.add(it) }
        }
    }

    private fun addMarkerToMap(spot: TouristSpot, position: LatLng, bitmap: Bitmap) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(position)
                .title(spot.nombre)
                .snippet("${spot.categoria} • ${String.format("%.1f", spot.rating)}⭐")
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                .anchor(0.5f, 0.5f)
        )
        marker?.tag = spot
        marker?.let { touristSpotMarkers.add(it) }
    }

    // =============== RUTAS CON ROUTES API V2 ===============

    @SuppressLint("MissingPermission")
    private fun getDirectionsTo(destination: LatLng) {
        if (!isLocationPermissionGranted()) {
            Toast.makeText(this, "Se necesita permiso de ubicación para calcular la ruta.", Toast.LENGTH_LONG).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val origin = LatLng(location.latitude, location.longitude)
                calculateAndDrawRoute(origin, destination)
            } else {
                Toast.makeText(this, "No se pudo obtener la ubicación actual. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Calcula y dibuja la ruta de navegación punto a punto usando Routes API v2.
     * Reemplaza la antigua Directions API (deprecada en marzo 2025).
     */
    private fun calculateAndDrawRoute(origin: LatLng, destination: LatLng) {
        val body = """
        {
            "origin":{"location":{"latLng":{"latitude":${origin.latitude},"longitude":${origin.longitude}}}},
            "destination":{"location":{"latLng":{"latitude":${destination.latitude},"longitude":${destination.longitude}}}},
            "travelMode":"WALK",
            "routingPreference":"ROUTING_PREFERENCE_UNSPECIFIED"
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Key", BuildConfig.DIRECTIONS_API_KEY)
            .header("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía")
                val encoded = JSONObject(responseBody)
                    .getJSONArray("routes")
                    .getJSONObject(0)
                    .getJSONObject("polyline")
                    .getString("encodedPolyline")

                withContext(Dispatchers.Main) {
                    navigationPolyline?.remove()
                    val path = PolyUtil.decode(encoded)
                    animatePolylineDraw(path, 0xFFEA4335.toInt(), 12f) { polyline ->
                        navigationPolyline = polyline
                    }
                    val bounds = LatLngBounds.Builder().include(origin).include(destination).build()
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error Routes API v2 (navegación): ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error al calcular la ruta: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Dibuja la polyline de la ruta turística completa usando Routes API v2.
     * Aplica estilo mejorado: RoundCap, colores profesionales y animación de dibujo.
     */
    private fun drawRoutePolyline(spots: List<TouristSpot>) {
        if (spots.size < 2) return
        routePolyline?.remove()

        val validSpots = spots.filter { it.ubicacion != null }
        if (validSpots.size < 2) return

        val body = buildRoutesApiBody(validSpots)

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Key", BuildConfig.DIRECTIONS_API_KEY)
            .header("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía")
                val json = JSONObject(responseBody)

                // Verificar que la respuesta tenga rutas
                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    throw Exception("Sin rutas disponibles")
                }

                val encoded = routes
                    .getJSONObject(0)
                    .getJSONObject("polyline")
                    .getString("encodedPolyline")

                withContext(Dispatchers.Main) {
                    val path = PolyUtil.decode(encoded)
                    animatePolylineDraw(path, 0xCC1A73E8.toInt(), 14f) { polyline ->
                        routePolyline = polyline
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Routes API v2 error (ruta): ${e.message}", e)
                withContext(Dispatchers.Main) {
                    drawStraightRoutePolyline(validSpots)
                }
            }
        }
    }

    /**
     * Construye el cuerpo JSON para Routes API v2 con múltiples paradas.
     */
    private fun buildRoutesApiBody(spots: List<TouristSpot>): String {
        val origin = spots.first().ubicacion!!
        val destination = spots.last().ubicacion!!

        val intermediatesJson = if (spots.size > 2) {
            spots.subList(1, spots.size - 1).joinToString(",") { spot ->
                val loc = spot.ubicacion!!
                """{"location":{"latLng":{"latitude":${loc.latitude},"longitude":${loc.longitude}}}}"""
            }
        } else ""

        return """
        {
            "origin":{"location":{"latLng":{"latitude":${origin.latitude},"longitude":${origin.longitude}}}},
            "destination":{"location":{"latLng":{"latitude":${destination.latitude},"longitude":${destination.longitude}}}},
            "intermediates":[$intermediatesJson],
            "travelMode":"WALK",
            "routingPreference":"ROUTING_PREFERENCE_UNSPECIFIED"
        }
        """.trimIndent()
    }

    private fun drawStraightRoutePolyline(spots: List<TouristSpot>) {
        routePolyline?.remove()
        val polylineOptions = PolylineOptions()
            .color(0xCC1A73E8.toInt())
            .width(14f)
            .startCap(RoundCap())
            .endCap(RoundCap())
            .jointType(JointType.ROUND)
        spots.forEach { spot ->
            spot.ubicacion?.let {
                polylineOptions.add(LatLng(it.latitude, it.longitude))
            }
        }
        routePolyline = mMap.addPolyline(polylineOptions)
    }

    /**
     * Anima el dibujo progresivo de una polyline desde el inicio hasta el final.
     * Duración: 1200ms con interpolación suave.
     */
    private fun animatePolylineDraw(
        fullPath: List<LatLng>,
        color: Int,
        width: Float,
        onCreated: (Polyline) -> Unit
    ) {
        val polyline = mMap.addPolyline(
            PolylineOptions()
                .color(color)
                .width(width)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .jointType(JointType.ROUND)
                .zIndex(1f)
        )
        onCreated(polyline)

        if (fullPath.size < 2) {
            polyline.points = fullPath
            return
        }

        // Pre-calcular distancias acumuladas
        val cumDist = mutableListOf(0.0)
        for (i in 0 until fullPath.size - 1) {
            cumDist.add(cumDist.last() + SphericalUtil.computeDistanceBetween(fullPath[i], fullPath[i + 1]))
        }
        val totalDist = cumDist.last()
        if (totalDist == 0.0) { polyline.points = fullPath; return }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val target = totalDist * anim.animatedFraction
                val partial = mutableListOf(fullPath[0])
                for (i in 0 until fullPath.size - 1) {
                    if (cumDist[i + 1] <= target) {
                        partial.add(fullPath[i + 1])
                    } else {
                        val segFrac = (target - cumDist[i]) / (cumDist[i + 1] - cumDist[i])
                        partial.add(SphericalUtil.interpolate(fullPath[i], fullPath[i + 1], segFrac))
                        break
                    }
                }
                polyline.points = partial
            }
            start()
        }
    }

    // =============== BÚSQUEDA ===============

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) { searchPlaces(query) }
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) { cargarLugaresDesdeFirestore() }
                return true
            }
        })
    }

    private fun searchPlaces(query: String) {
        db.collection("lugares").orderBy("nombre").startAt(query).endAt(query + '\uf8ff').get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontraron lugares", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val searchResults = documents.mapNotNull { document ->
                    try {
                        document.toObject(TouristSpot::class.java).copy(id = document.id)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error convirtiendo documento ${document.id}", e)
                        null
                    }
                }

                val filteredResults = if (selectedCategories.isEmpty()) {
                    searchResults
                } else {
                    searchResults.filter { spot ->
                        selectedCategories.any { category ->
                            spot.categoria.lowercase().contains(category.lowercase())
                        }
                    }
                }

                filteredResults.forEach { addMarkerForTouristSpot(it) }

                if (filteredResults.isEmpty()) {
                    NotificationHelper.info(binding.root, "No se encontraron lugares con estos filtros")
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Error en la búsqueda", e)
                Toast.makeText(this, "Error en la búsqueda: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // =============== UBICACIÓN ===============

    private fun isLocationPermissionGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableMyLocation() {
        if (isLocationPermissionGranted()) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Permiso denegado. La ubicación actual no se puede mostrar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cargarLugaresDesdeFirestore() {
        clearAllMarkers()
        db.collection("lugares").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "No hay lugares en la base de datos.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                allSpots = documents.mapNotNull { document ->
                    try {
                        document.toObject(TouristSpot::class.java).copy(id = document.id)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error convirtiendo documento ${document.id}", e)
                        null
                    }
                }

                applyFilters()
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Error obteniendo documentos", e)
                Toast.makeText(this, "Error leyendo Firestore: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun clearAllMarkers() {
        markerGeneration++  // Invalida callbacks de Glide pendientes de la generación anterior
        touristSpotMarkers.forEach { it.remove() }
        touristSpotMarkers.clear()
        routePolyline?.remove()
        navigationPolyline?.remove()
        routePolyline = null
        navigationPolyline = null
    }

    // =============== NAVEGACIÓN DE RUTA ===============

    /**
     * @param canSave true cuando la ruta viene de la IA y aún no ha sido guardada.
     */
    private fun setupRouteNavigation(canSave: Boolean = false) {
        binding.routeNavigationPanel.visibility = View.VISIBLE

        // Botón guardar dentro del panel (solo en rutas nuevas de IA)
        binding.saveRoutePanelButton.visibility = if (canSave) View.VISIBLE else View.GONE
        binding.saveRoutePanelButton.setOnClickListener {
            AuthManager.requireAuth(this, AuthManager.AuthRequired.SAVE_ROUTES) {
                if (currentRouteSpots.isNotEmpty()) {
                    showSaveRouteDialog()
                } else {
                    NotificationHelper.warning(binding.root, "No hay lugares en la ruta")
                }
            }
        }

        binding.previousPlaceButton.setOnClickListener {
            if (currentPlaceIndex > 0) {
                currentPlaceIndex--
                updateRouteNavigationPanel()
                centerOnCurrentPlace()
            }
        }

        binding.nextPlaceButton.setOnClickListener {
            if (currentPlaceIndex < currentRouteSpots.size - 1) {
                currentPlaceIndex++
                updateRouteNavigationPanel()
                centerOnCurrentPlace()
            }
        }

        binding.closeRouteButton.setOnClickListener {
            closeRouteNavigation()
        }

        binding.viewDetailsButton.setOnClickListener {
            val currentSpot = currentRouteSpots[currentPlaceIndex]
            val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                putExtra("PLACE_ID", currentSpot.id)
                putExtra("PLACE_NAME", currentSpot.nombre)
                putExtra("PLACE_CATEGORY", currentSpot.categoria)
                putExtra("PLACE_DESCRIPTION", currentSpot.descripcion)
                putExtra("GOOGLE_PLACE_ID", currentSpot.googlePlaceId)
                currentSpot.ubicacion?.let {
                    putExtra("PLACE_LATITUDE", it.latitude)
                    putExtra("PLACE_LONGITUDE", it.longitude)
                }
            }
            placeDetailsLauncher.launch(intent)
        }

        binding.navigateButton.setOnClickListener {
            val currentSpot = currentRouteSpots[currentPlaceIndex]
            currentSpot.ubicacion?.let { location ->
                val gmmIntentUri = android.net.Uri.parse(
                    "google.navigation:q=${location.latitude},${location.longitude}&mode=w"
                )
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    NotificationHelper.error(binding.root, "Google Maps no está instalado")
                }
            }
        }
    }

    private fun updateRouteNavigationPanel() {
        if (currentRouteSpots.isEmpty()) return

        val currentSpot = currentRouteSpots[currentPlaceIndex]

        binding.routeProgressText.text = "Lugar ${currentPlaceIndex + 1} de ${currentRouteSpots.size}"

        val estimatedMinutes = calculateEstimatedTime(currentRouteSpots.size)
        val hours = estimatedMinutes / 60
        val minutes = estimatedMinutes % 60
        binding.routeTimeEstimateText.text = if (hours > 0) {
            "⏱️ Tiempo estimado: ${hours}h ${minutes}min"
        } else {
            "⏱️ Tiempo estimado: ${minutes}min"
        }

        binding.currentPlaceName.text = currentSpot.nombre
        binding.currentPlaceCategory.text =
            "${CategoryUtils.getCategoryEmoji(currentSpot.categoria)} ${currentSpot.categoria}"
        binding.currentPlaceDescription.text = currentSpot.descripcion

        binding.previousPlaceButton.isEnabled = currentPlaceIndex > 0
        binding.nextPlaceButton.isEnabled = currentPlaceIndex < currentRouteSpots.size - 1
        binding.previousPlaceButton.alpha = if (currentPlaceIndex > 0) 1f else 0.5f
        binding.nextPlaceButton.alpha = if (currentPlaceIndex < currentRouteSpots.size - 1) 1f else 0.5f
    }

    private fun centerOnCurrentPlace() {
        if (currentRouteSpots.isEmpty()) return

        val currentSpot = currentRouteSpots[currentPlaceIndex]
        currentSpot.ubicacion?.let { location ->
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17f),
                500,
                null
            )
        }
    }

    private fun calculateEstimatedTime(placeCount: Int): Int {
        val timePerPlace = 15
        val timeBetweenPlaces = 5
        return (placeCount * timePerPlace) + ((placeCount - 1) * timeBetweenPlaces)
    }

    private fun closeRouteNavigation() {
        isNavigatingRoute = false
        binding.routeNavigationPanel.visibility = View.GONE
        binding.searchView.visibility = View.VISIBLE
        binding.filterScrollView.visibility = View.VISIBLE
        finish()
    }

    // =============== HELPERS DE BITMAP PARA MARCADORES ===============

    /**
     * Círculo con imagen de lugar + borde de color de categoría.
     */
    private fun createCircularBitmapWithBorder(source: Bitmap, borderColor: Int): Bitmap {
        val borderPx = dpToPx(3)
        val size = source.width + borderPx * 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dpToPx(1), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(source, borderPx.toFloat(), borderPx.toFloat(), null)
        return output
    }

    /**
     * Círculo con imagen de lugar + número de parada en badge superior-derecho.
     */
    private fun createCircularBitmapWithNumber(source: Bitmap, number: Int, borderColor: Int): Bitmap {
        val borderPx = dpToPx(3)
        val size = source.width + borderPx * 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Borde de color de categoría
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dpToPx(1), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(source, borderPx.toFloat(), borderPx.toFloat(), null)

        // Badge con número
        val badgeRadius = dpToPx(10).toFloat()
        val badgeX = size - badgeRadius
        val badgeY = badgeRadius
        canvas.drawCircle(badgeX, badgeY, badgeRadius + dpToPx(1), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(badgeX, badgeY, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A73E8") })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPx(10).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(number.toString(), badgeX, badgeY - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        return output
    }

    /**
     * Círculo de color con número (fallback cuando no hay imagen).
     */
    private fun createNumberedCircleMarker(number: Int, backgroundColor: Int): Bitmap {
        val sizePx = dpToPx(44)
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - dpToPx(2), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPx(16).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(number.toString(), sizePx / 2f, sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        return output
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun getCategoryColor(category: String): Int = when (category.lowercase()) {
        "museo" -> Color.parseColor("#9C27B0")
        "restaurante", "gastronomía" -> Color.parseColor("#FF5722")
        "hotel", "hospedaje" -> Color.parseColor("#2196F3")
        "iglesia", "templo" -> Color.parseColor("#00BCD4")
        "parque", "naturaleza" -> Color.parseColor("#4CAF50")
        "tienda", "comercio" -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#F44336")
    }

    private fun getCategoryHue(category: String): Float = when (category.lowercase()) {
        "museo" -> BitmapDescriptorFactory.HUE_VIOLET
        "restaurante", "gastronomía" -> BitmapDescriptorFactory.HUE_ORANGE
        "hotel", "hospedaje" -> BitmapDescriptorFactory.HUE_BLUE
        "iglesia", "templo" -> BitmapDescriptorFactory.HUE_CYAN
        "parque", "naturaleza" -> BitmapDescriptorFactory.HUE_GREEN
        "tienda", "comercio" -> BitmapDescriptorFactory.HUE_YELLOW
        else -> BitmapDescriptorFactory.HUE_RED
    }
}
