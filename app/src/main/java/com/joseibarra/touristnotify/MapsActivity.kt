package com.joseibarra.touristnotify

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
    private lateinit var routeRenderer: RouteRenderer
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
        routeRenderer = RouteRenderer(mMap, okHttpClient)

        // Ocultar los puntos de interés por defecto de Google Maps (iglesias, tiendas, etc.)
        // para que solo se vean nuestros marcadores personalizados
        try {
            val styleOptions = com.google.android.gms.maps.model.MapStyleOptions
                .loadRawResourceStyle(this, R.raw.map_style)
            mMap.setMapStyle(styleOptions)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("MapsActivity", "No se pudo aplicar el estilo del mapa", e)
        }

        val alamosBounds = LatLngBounds(LatLng(26.98, -109.0), LatLng(27.06, -108.9))
        mMap.setLatLngBoundsForCameraTarget(alamosBounds)
        mMap.setMinZoomPreference(13.5f)
        mMap.setMaxZoomPreference(20.0f)

        mMap.setOnMarkerClickListener { marker ->
            val spot = marker.tag as? TouristSpot
            if (spot != null) {
                val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                    putExtra("PLACE_ID", spot.id)
                    putExtra("PLACE_NAME", spot.nombre)
                    putExtra("PLACE_CATEGORY", spot.categoria)
                    putExtra("PLACE_DESCRIPTION", spot.descripcion)
                    putExtra("GOOGLE_PLACE_ID", spot.googlePlaceId)
                    spot.ubicacion?.let { geo ->
                        putExtra("PLACE_LATITUDE", geo.latitude)
                        putExtra("PLACE_LONGITUDE", geo.longitude)
                    }
                }
                placeDetailsLauncher.launch(intent)
            } else if (BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Marker clicked but tag is not TouristSpot: ${marker.tag?.javaClass?.simpleName}")
            }
            true
        }

        val alamos = LatLng(AppConstants.ALAMOS_LAT, AppConstants.ALAMOS_LNG)
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
            NotificationHelper.info(binding.root, getString(R.string.msg_no_places_filters))
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
                    Toast.makeText(this, getString(R.string.error_saved_route_not_found), Toast.LENGTH_LONG).show()
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
                if (BuildConfig.DEBUG) Log.e("Firestore", "Error cargando ruta por IDs", e)
                Toast.makeText(this, getString(R.string.error_loading_saved_route, e.message), Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, getString(R.string.error_route_places_not_found), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val spots = documents.mapNotNull { doc -> doc.toObject(TouristSpot::class.java).copy(id = doc.id) }
                // Deduplicar docs de Firestore por nombre
                val spotsByName = spots.groupBy { it.nombre }.mapValues { it.value.first() }
                // Mantener el orden original de la IA, eliminando nombres duplicados en la lista
                currentRouteSpots = placeNames.distinct().mapNotNull { spotsByName[it] }

                currentRouteSpots.forEachIndexed { index, spot -> addMarkerForTouristSpot(spot, index + 1) }
                drawRoutePolyline(currentRouteSpots)

                isNavigatingRoute = true
                currentPlaceIndex = 0
                setupRouteNavigation(canSave = true)
                updateRouteNavigationPanel()
            }
            .addOnFailureListener { e ->
                if (BuildConfig.DEBUG) Log.e("Firestore", "Error cargando ruta", e)
                Toast.makeText(this, getString(R.string.error_loading_route, e.message), Toast.LENGTH_LONG).show()
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
                    NotificationHelper.warning(binding.root, getString(R.string.warning_add_places_first))
                }
            }
        }
    }

    private fun showSaveRouteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_route, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.route_name_edit_text)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.route_description_edit_text)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_save_route))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = nameEditText.text.toString()
                val description = descriptionEditText.text.toString()
                if (name.isNotBlank()) {
                    saveRouteToFirestore(name, description)
                } else {
                    NotificationHelper.warning(binding.root, getString(R.string.warning_route_name_required))
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun saveRouteToFirestore(name: String, description: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.error_user_not_authenticated), Toast.LENGTH_SHORT).show()
            return
        }

        if (currentRouteSpots.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_places_to_save), Toast.LENGTH_SHORT).show()
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
                if (BuildConfig.DEBUG) Log.e(TAG, "Error al guardar ruta", e)
                NotificationHelper.error(binding.root, getString(R.string.error_saving_route, e.message))
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
        val categoryColor = CategoryColorMapper.getColor(spot.categoria)

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
                            MarkerBitmapFactory.createCircularWithNumber(resource, routeIndex, categoryColor, resources)
                        } else {
                            MarkerBitmapFactory.createCircularWithBorder(resource, categoryColor, resources)
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
        val bitmap = if (routeIndex >= 1) {
            MarkerBitmapFactory.createNumberedCircle(routeIndex, categoryColor, resources)
        } else {
            MarkerBitmapFactory.createColoredCircle(categoryColor)
        }
        addMarkerToMap(spot, position, bitmap)
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
            Toast.makeText(this, getString(R.string.msg_location_permission_needed), Toast.LENGTH_LONG).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val origin = LatLng(location.latitude, location.longitude)
                calculateAndDrawRoute(origin, destination)
            } else {
                Toast.makeText(this, getString(R.string.error_location_not_obtained), Toast.LENGTH_LONG).show()
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
                if (BuildConfig.DEBUG) Log.e(TAG, "Error Routes API v2 (navegación): ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, getString(R.string.error_calculating_route, e.message), Toast.LENGTH_LONG).show()
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
                if (BuildConfig.DEBUG) Log.e(TAG, "Routes API v2 error (ruta): ${e.message}", e)
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
        val origin = spots.first().ubicacion ?: throw IllegalArgumentException("Origin ubicacion is null")
        val destination = spots.last().ubicacion ?: throw IllegalArgumentException("Destination ubicacion is null")

        val intermediatesJson = if (spots.size > 2) {
            spots.subList(1, spots.size - 1).mapNotNull { spot ->
                spot.ubicacion?.let { loc ->
                    """{"location":{"latLng":{"latitude":${loc.latitude},"longitude":${loc.longitude}}}}"""
                }
            }.joinToString(",")
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
                    Toast.makeText(this, getString(R.string.msg_no_places_found), Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val searchResults = documents.mapNotNull { document ->
                    try {
                        document.toObject(TouristSpot::class.java).copy(id = document.id)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("Firestore", "Error convirtiendo documento ${document.id}", e)
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
                    NotificationHelper.info(binding.root, getString(R.string.msg_no_places_filters))
                }
            }.addOnFailureListener { e ->
                if (BuildConfig.DEBUG) Log.e("Firestore", "Error en la búsqueda", e)
                Toast.makeText(this, getString(R.string.error_search_failed, e.message), Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, getString(R.string.error_location_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun cargarLugaresDesdeFirestore() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.error_no_internet), Toast.LENGTH_LONG).show()
            return
        }
        clearAllMarkers()
        db.collection("lugares").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, getString(R.string.msg_no_places_in_database), Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                allSpots = documents.mapNotNull { document ->
                    try {
                        document.toObject(TouristSpot::class.java).copy(id = document.id)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("Firestore", "Error convirtiendo documento ${document.id}", e)
                        null
                    }
                }

                applyFilters()
            }.addOnFailureListener { e ->
                if (BuildConfig.DEBUG) Log.e("Firestore", "Error obteniendo documentos", e)
                Toast.makeText(this, getString(R.string.error_firestore_read, e.message), Toast.LENGTH_LONG).show()
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
                    NotificationHelper.warning(binding.root, getString(R.string.warning_no_places_in_route))
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
                    NotificationHelper.error(binding.root, getString(R.string.error_google_maps_not_installed))
                }
            }
        }
    }

    private fun updateRouteNavigationPanel() {
        if (currentRouteSpots.isEmpty()) return

        val currentSpot = currentRouteSpots[currentPlaceIndex]

        binding.routeProgressText.text = getString(R.string.label_route_progress, currentPlaceIndex + 1, currentRouteSpots.size)

        val estimatedMinutes = routeRenderer.calculateEstimatedTime(currentRouteSpots.size)
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

    private fun closeRouteNavigation() {
        isNavigatingRoute = false
        binding.routeNavigationPanel.visibility = View.GONE
        binding.searchView.visibility = View.VISIBLE
        binding.filterScrollView.visibility = View.VISIBLE
        finish()
    }

}
