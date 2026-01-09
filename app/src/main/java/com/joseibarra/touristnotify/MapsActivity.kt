package com.joseibarra.touristnotify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.SearchView
import android.widget.Toast
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
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.android.PolyUtil
import com.google.maps.model.TravelMode
import com.joseibarra.touristnotify.databinding.ActivityMapsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    it.ubicacion?.let {
                        putExtra("PLACE_LATITUDE", it.latitude)
                        putExtra("PLACE_LONGITUDE", it.longitude)
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
            cargarLugaresDesdeFirestore()
        }

        enableMyLocation()
        setupSaveRouteButton()
    }
    
    private fun loadRouteByIds(placeIds: List<String>) {
        binding.searchView.visibility = View.GONE
        binding.saveRouteButton.visibility = View.GONE

        db.collection("lugares").whereIn(FieldPath.documentId(), placeIds).get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontraron los lugares de la ruta guardada", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val spots = documents.mapNotNull { doc -> doc.toObject(TouristSpot::class.java).copy(id = doc.id) }
                currentRouteSpots = spots.sortedBy { placeIds.indexOf(it.id) }

                currentRouteSpots.forEach { addMarkerForTouristSpot(it) }
                drawRoutePolyline(currentRouteSpots)

                val boundsBuilder = LatLngBounds.Builder()
                currentRouteSpots.forEach { spot -> spot.ubicacion?.let { boundsBuilder.include(LatLng(it.latitude, it.longitude)) } }
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
        binding.saveRouteButton.visibility = View.VISIBLE

        db.collection("lugares").whereIn("nombre", placeNames).get()
            .addOnSuccessListener { documents ->
                clearAllMarkers()
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontraron los lugares de la ruta", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val spots = documents.mapNotNull { doc -> doc.toObject(TouristSpot::class.java).copy(id = doc.id) }
                currentRouteSpots = spots.sortedBy { placeNames.indexOf(it.nombre) }

                currentRouteSpots.forEach { addMarkerForTouristSpot(it) }
                drawRoutePolyline(currentRouteSpots)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error cargando ruta", e)
                Toast.makeText(this, "Error cargando la ruta: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupSaveRouteButton() {
        binding.saveRouteButton.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(this, "Debes iniciar sesión para guardar una ruta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentRouteSpots.isNotEmpty()) {
                showSaveRouteDialog()
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
                    Toast.makeText(this, "El nombre de la ruta no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveRouteToFirestore(name: String, description: String) {
        // Validación de usuario autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        // Validación de ruta no vacía
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
                Toast.makeText(this, "Ruta guardada con éxito", Toast.LENGTH_SHORT).show()
                binding.saveRouteButton.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar ruta", e)
                Toast.makeText(this, "Error al guardar la ruta: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun addMarkerForTouristSpot(spot: TouristSpot) {
        spot.ubicacion?.let { geoPoint ->
            val position = LatLng(geoPoint.latitude, geoPoint.longitude)
            val markerOptions = MarkerOptions().position(position).title(spot.nombre).snippet(spot.categoria)
            val marker = mMap.addMarker(markerOptions)
            marker?.tag = spot
            if (marker != null) {
                touristSpotMarkers.add(marker)
            }
        }
    }

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

    private fun calculateAndDrawRoute(origin: LatLng, destination: LatLng) {
        val geoApiContext = GeoApiContext.Builder().apiKey(BuildConfig.DIRECTIONS_API_KEY).build()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val directionsResult = DirectionsApi.newRequest(geoApiContext)
                    .mode(TravelMode.DRIVING)
                    .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                    .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                    .await()

                withContext(Dispatchers.Main) {
                    navigationPolyline?.remove()
                    val polylineOptions = PolylineOptions().color(Color.RED).width(15f)
                    val decodedPath = PolyUtil.decode(directionsResult.routes[0].overviewPolyline.encodedPath)
                    polylineOptions.addAll(decodedPath)
                    navigationPolyline = mMap.addPolyline(polylineOptions)

                    val bounds = LatLngBounds.Builder().include(origin).include(destination).build()
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            } catch (e: Exception) {
                Log.e("DirectionsAPI", "Error al calcular la ruta", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapsActivity, "Error al calcular la ruta: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun drawRoutePolyline(spots: List<TouristSpot>) {
        if (spots.size < 2) return

        routePolyline?.remove()

        val waypoints = spots.mapNotNull { spot ->
            spot.ubicacion?.let { com.google.maps.model.LatLng(it.latitude, it.longitude) }
        }

        if (waypoints.size < 2) return

        val origin = waypoints.first()
        val destination = waypoints.last()
        val intermediateWaypoints = if (waypoints.size > 2) {
            waypoints.subList(1, waypoints.size - 1).toTypedArray()
        } else {
            emptyArray()
        }

        val geoApiContext = GeoApiContext.Builder()
            .apiKey(BuildConfig.DIRECTIONS_API_KEY)
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val directionsResult = DirectionsApi.newRequest(geoApiContext)
                    .mode(TravelMode.DRIVING)
                    .origin(origin)
                    .destination(destination)
                    .waypoints(*intermediateWaypoints)
                    .optimizeWaypoints(true)
                    .await()

                withContext(Dispatchers.Main) {
                    if (directionsResult.routes.isNotEmpty() && directionsResult.routes[0].overviewPolyline != null) {
                        val decodedPath = PolyUtil.decode(directionsResult.routes[0].overviewPolyline.encodedPath)
                        routePolyline = mMap.addPolyline(PolylineOptions()
                            .addAll(decodedPath)
                            .color(Color.BLUE)
                            .width(10f))
                    } else {
                        Log.w(TAG, "Directions API returned no routes.")
                        drawStraightRoutePolyline(spots)
                        Toast.makeText(this@MapsActivity, "No se pudo encontrar una ruta. Mostrando líneas rectas.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching directions", e)
                withContext(Dispatchers.Main) {
                    drawStraightRoutePolyline(spots)
                    Toast.makeText(this@MapsActivity, "Error al obtener direcciones. Mostrando líneas rectas.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun drawStraightRoutePolyline(spots: List<TouristSpot>) {
        routePolyline?.remove()
        val polylineOptions = PolylineOptions().color(Color.BLUE).width(10f)
        spots.forEach { spot ->
            spot.ubicacion?.let {
                polylineOptions.add(LatLng(it.latitude, it.longitude))
            }
        }
        routePolyline = mMap.addPolyline(polylineOptions)
    }

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
                if (documents.isEmpty) { Toast.makeText(this, "No se encontraron lugares", Toast.LENGTH_SHORT).show() }
                for (document in documents) {
                    try {
                        val spot = document.toObject(TouristSpot::class.java).copy(id = document.id)
                        addMarkerForTouristSpot(spot)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error convirtiendo documento ${document.id}", e)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Error en la búsqueda", e)
                Toast.makeText(this, "Error en la búsqueda: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun isLocationPermissionGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableMyLocation() {
        if (isLocationPermissionGranted()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
            }
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
                if (documents.isEmpty) { Toast.makeText(this, "No hay lugares en la base de datos.", Toast.LENGTH_SHORT).show() }
                for (document in documents) {
                    try {
                        val spot = document.toObject(TouristSpot::class.java).copy(id = document.id)
                        addMarkerForTouristSpot(spot)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error convirtiendo documento ${document.id}", e)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Error obteniendo documentos", e)
                Toast.makeText(this, "Error leyendo Firestore: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun clearAllMarkers() {
        touristSpotMarkers.forEach { it.remove() }
        touristSpotMarkers.clear()
        routePolyline?.remove()
        navigationPolyline?.remove()
        routePolyline = null
        navigationPolyline = null
    }
}