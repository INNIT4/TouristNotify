package com.joseibarra.trazago

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.databinding.ActivityMapsBinding
import com.joseibarra.trazago.ui.BaseActivity

class MapsActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var markerRenderer: MarkerRenderer
    private lateinit var routePolylineManager: RoutePolylineManager
    private lateinit var routeNavController: RouteNavigationController

    private var currentRouteSpots = listOf<TouristSpot>()
    private val selectedCategories = mutableSetOf<String>()
    private var allSpots = listOf<TouristSpot>()

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

        markerRenderer = MarkerRenderer(this, mMap, lifecycleScope)
        routePolylineManager = RoutePolylineManager(
            map = mMap,
            lifecycleScope = lifecycleScope,
            apiKey = BuildConfig.DIRECTIONS_API_KEY,
            onError = { error -> NotificationHelper.error(binding.root, error) },
            http = (application as TrazaGoApplication).http,
            context = this
        )
        routeNavController = RouteNavigationController(
            map = mMap,
            onNavigateToDetails = { spot ->
                val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                    putExtra(PlaceSummary.EXTRA_KEY, PlaceSummary.fromTouristSpot(spot))
                }
                placeDetailsLauncher.launch(intent)
            },
            onClose = { finish() },
            onSave = {
                AuthManager.requireAuth(this, AuthManager.AuthRequired.SAVE_ROUTES) {
                    if (currentRouteSpots.isNotEmpty()) showSaveRouteDialog()
                    else NotificationHelper.warning(binding.root, "No hay lugares en la ruta")
                }
            }
        )

        try {
            val styleOptions = com.google.android.gms.maps.model.MapStyleOptions
                .loadRawResourceStyle(this, R.raw.map_style)
            mMap.setMapStyle(styleOptions)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo aplicar el estilo del mapa", e)
        }

        val alamosBounds = LatLngBounds(LatLng(26.98, -109.0), LatLng(27.06, -108.9))
        mMap.setLatLngBoundsForCameraTarget(alamosBounds)
        mMap.setMinZoomPreference(13.5f)
        mMap.setMaxZoomPreference(20.0f)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true

        mMap.setOnMarkerClickListener { marker ->
            val spot = marker.tag as? TouristSpot
            spot?.let {
                val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                    putExtra(PlaceSummary.EXTRA_KEY, PlaceSummary.fromTouristSpot(it))
                }
                placeDetailsLauncher.launch(intent)
            }
            true
        }

        val alamos = LatLng(AppConstants.ALAMOS_LAT, AppConstants.ALAMOS_LNG)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(alamos, 15f))

        val routePlaceIds = intent.getStringArrayListExtra("ROUTE_PLACES_IDS")

        if (routePlaceIds != null && routePlaceIds.isNotEmpty()) {
            loadRouteByIds(routePlaceIds)
        } else {
            setupSearchView()
            setupFilterChips()
            cargarLugaresDesdeFirestore()
        }

        enableMyLocation()
        setupSaveRouteButton()
        setupToggleUiButton()
        setupPlacesListButton()
    }

    private var uiVisible = true

    private fun setupPlacesListButton() {
        binding.placesListButton.setOnClickListener {
            val visible = if (selectedCategories.isEmpty()) allSpots else allSpots.filter { spot ->
                selectedCategories.any { spot.categoria.lowercase().contains(it.lowercase()) }
            }
            if (visible.isEmpty()) {
                NotificationHelper.info(binding.root, "No hay lugares cargados")
                return@setOnClickListener
            }
            val names = visible.map { it.nombre }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.map_places_title))
                .setItems(names) { _, which ->
                    visible[which].ubicacion?.let { geo ->
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(geo.latitude, geo.longitude), 17f))
                    }
                }
                .setNegativeButton(getString(R.string.close), null)
                .show()
        }
    }

    private fun setupToggleUiButton() {
        binding.toggleUiButton.setOnClickListener {
            uiVisible = !uiVisible
            val visibility = if (uiVisible) View.VISIBLE else View.GONE
            binding.searchView.visibility = visibility
            binding.filterScrollView.visibility = visibility
        }
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
                chip.contentDescription = getString(
                    if (isChecked) R.string.a11y_chip_active else R.string.a11y_chip_inactive,
                    chip.text
                )
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

        filteredSpots.forEach { markerRenderer.addMarker(it) }

        if (filteredSpots.isEmpty() && allSpots.isNotEmpty()) {
            NotificationHelper.info(binding.root, "No se encontraron lugares con estos filtros")
        }
    }

    private fun loadRouteByIds(placeIds: List<String>) {
        binding.searchView.visibility = View.GONE
        binding.saveRouteButton.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        binding.placesListButton.visibility = View.GONE

        db.collection("lugares").whereIn(FieldPath.documentId(), placeIds).get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    NotificationHelper.warning(binding.root, "No se encontraron los lugares de la ruta guardada")
                    return@addOnSuccessListener
                }

                val spots = documents.mapNotNull { doc -> doc.toObject(TouristSpot::class.java).copy(id = doc.id) }
                currentRouteSpots = spots.sortedBy { placeIds.indexOf(it.id) }

                currentRouteSpots.forEachIndexed { index, spot -> markerRenderer.addMarker(spot, index + 1) }
                routePolylineManager.drawTouristRoute(currentRouteSpots)
                routeNavController.startNavigation(currentRouteSpots, canSave = false, binding)

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
                NotificationHelper.error(binding.root, "Error al cargar la ruta guardada: ${e.message}")
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
            .setTitle(getString(R.string.map_save_route_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.map_save_button)) { _, _ ->
                val name = nameEditText.text.toString()
                val description = descriptionEditText.text.toString()
                if (name.isNotBlank()) {
                    saveRouteToFirestore(name, description)
                } else {
                    NotificationHelper.warning(binding.root, "El nombre de la ruta no puede estar vacío")
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveRouteToFirestore(name: String, description: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            NotificationHelper.error(binding.root, "Error: Usuario no autenticado")
            return
        }

        if (currentRouteSpots.isEmpty()) {
            NotificationHelper.warning(binding.root, "Error: No hay lugares en la ruta para guardar")
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
                binding.saveRouteButton.visibility = View.GONE
                binding.saveRoutePanelButton.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar ruta", e)
                NotificationHelper.error(binding.root, "Error al guardar la ruta: ${e.message}")
            }
    }

    @SuppressLint("MissingPermission")
    private fun getDirectionsTo(destination: LatLng) {
        if (!isLocationPermissionGranted()) {
            NotificationHelper.locationPermissionNeeded(binding.root) {}
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                routePolylineManager.drawNavigationRoute(
                    LatLng(location.latitude, location.longitude),
                    destination
                )
            } else {
                NotificationHelper.warning(binding.root, "No se pudo obtener la ubicación actual. Inténtalo de nuevo.")
            }
        }
    }

    private fun setupSearchView() {
        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()
                if (query.isNullOrBlank()) cargarLugaresDesdeFirestore()
                else searchPlaces(query)
            }
        })
    }

    private fun searchPlaces(query: String) {
        db.collection("lugares").orderBy("nombre").startAt(query).endAt(query + '').get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    NotificationHelper.info(binding.root, "No se encontraron lugares")
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

                filteredResults.forEach { markerRenderer.addMarker(it) }

                if (filteredResults.isEmpty()) {
                    NotificationHelper.info(binding.root, "No se encontraron lugares con estos filtros")
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Error en la búsqueda", e)
                NotificationHelper.error(binding.root, "Error en la búsqueda: ${e.message}")
            }
    }

    private fun isLocationPermissionGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableMyLocation() {
        if (isLocationPermissionGranted()) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                NotificationHelper.warning(binding.root, "Permiso denegado. La ubicación actual no se puede mostrar.")
            }
        }
    }

    private fun cargarLugaresDesdeFirestore() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            NotificationHelper.noInternet(binding.root)
            return
        }
        clearAllMarkers()
        db.collection(FirestoreCollections.PLACES).limit(200).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    NotificationHelper.info(binding.root, "No hay lugares en la base de datos.")
                    return@addOnSuccessListener
                }
                if (documents.size() == 200) {
                    Log.w(TAG, "cargarLugaresDesdeFirestore: resultado truncado a 200 documentos — considerar paginación")
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
                NotificationHelper.error(binding.root, "Error leyendo Firestore: ${e.message}")
            }
    }

    private fun clearAllMarkers() {
        if (::markerRenderer.isInitialized) markerRenderer.clearMarkers()
        if (::routePolylineManager.isInitialized) routePolylineManager.clearRoutes()
        if (::mMap.isInitialized) mMap.clear()
    }

    // AND-005: preservar estado crítico en rotación de pantalla
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selected_categories", ArrayList(selectedCategories))
        if (::routeNavController.isInitialized) {
            outState.putInt("current_place_index", routeNavController.currentIndex)
            outState.putBoolean("is_navigating_route", routeNavController.isActive)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getStringArrayList("selected_categories")?.let {
            selectedCategories.addAll(it)
        }
    }

    override fun onDestroy() {
        if (::markerRenderer.isInitialized) markerRenderer.destroy()
        if (::routePolylineManager.isInitialized) routePolylineManager.cancel()
        super.onDestroy()
    }
}
