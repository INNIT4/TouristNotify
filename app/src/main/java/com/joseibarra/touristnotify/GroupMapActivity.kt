package com.joseibarra.touristnotify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.joseibarra.touristnotify.databinding.ActivityGroupMapBinding

/**
 * Actividad para mostrar mapa con ubicaciones en tiempo real de miembros del grupo
 */
class GroupMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityGroupMapBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private var groupId: String? = null
    private var groupName: String? = null
    private val memberMarkers = mutableMapOf<String, Marker>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        groupId = intent.getStringExtra("GROUP_ID")
        groupName = intent.getStringExtra("GROUP_NAME")

        if (groupId == null) {
            NotificationHelper.error(binding.root, "Error: grupo no encontrado")
            finish()
            return
        }

        supportActionBar?.title = "$groupName - Mapa"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
    }

    private fun setupUI() {
        binding.startSharingButton.setOnClickListener {
            startLocationSharing()
        }

        binding.stopSharingButton.setOnClickListener {
            stopLocationSharing()
        }

        binding.centerOnMeButton.setOnClickListener {
            centerOnMyLocation()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Configurar mapa
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = false

        // Verificar permisos de ubicación
        if (checkLocationPermission()) {
            enableMyLocation()
        } else {
            requestLocationPermission()
        }

        // Centrar en Álamos
        val alamos = LatLng(27.0242, -108.9384)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(alamos, 14f))

        // Escuchar ubicaciones de miembros
        listenToMemberLocations()
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }
    }

    private fun listenToMemberLocations() {
        val membersRef = database.getReference("group_members").child(groupId!!)

        membersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (memberSnapshot in snapshot.children) {
                    val member = memberSnapshot.getValue(GroupMember::class.java)
                    if (member != null && member.latitude != 0.0 && member.longitude != 0.0) {
                        updateMemberMarker(member)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                NotificationHelper.error(binding.root, "Error al cargar ubicaciones")
            }
        })
    }

    private fun updateMemberMarker(member: GroupMember) {
        val position = LatLng(member.latitude, member.longitude)

        if (memberMarkers.containsKey(member.userId)) {
            // Actualizar marker existente
            memberMarkers[member.userId]?.position = position
        } else {
            // Crear nuevo marker
            val markerOptions = MarkerOptions()
                .position(position)
                .title(member.userName)
                .snippet(if (member.isOnline) "En línea" else "Última ubicación")
                .icon(BitmapDescriptorFactory.defaultMarker(
                    if (member.userId == auth.currentUser?.uid) {
                        BitmapDescriptorFactory.HUE_AZURE
                    } else {
                        BitmapDescriptorFactory.HUE_RED
                    }
                ))

            val marker = mMap.addMarker(markerOptions)
            if (marker != null) {
                memberMarkers[member.userId] = marker
            }
        }
    }

    private fun startLocationSharing() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        // Iniciar servicio de compartir ubicación
        val intent = Intent(this, LocationSharingService::class.java).apply {
            action = LocationSharingService.ACTION_START_SHARING
            putExtra("GROUP_ID", groupId)
        }
        startService(intent)

        binding.startSharingButton.isEnabled = false
        binding.stopSharingButton.isEnabled = true
        binding.sharingStatusTextView.text = "📍 Compartiendo ubicación..."

        NotificationHelper.success(binding.root, "Compartiendo ubicación con el grupo")
    }

    private fun stopLocationSharing() {
        val intent = Intent(this, LocationSharingService::class.java).apply {
            action = LocationSharingService.ACTION_STOP_SHARING
        }
        startService(intent)

        binding.startSharingButton.isEnabled = true
        binding.stopSharingButton.isEnabled = false
        binding.sharingStatusTextView.text = "○ No compartiendo"

        NotificationHelper.info(binding.root, "Dejaste de compartir ubicación")
    }

    private fun centerOnMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val myLocation = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 15f))
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener compartir ubicación al salir
        stopLocationSharing()
    }
}
