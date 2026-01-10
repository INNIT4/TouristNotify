package com.joseibarra.touristnotify

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Servicio en primer plano para compartir ubicación en tiempo real con el grupo
 */
class LocationSharingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private var groupId: String? = null
    private var isSharing = false

    companion object {
        const val ACTION_START_SHARING = "com.joseibarra.touristnotify.START_SHARING"
        const val ACTION_STOP_SHARING = "com.joseibarra.touristnotify.STOP_SHARING"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "location_sharing_channel"
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 segundos
        private const val LOCATION_UPDATE_FASTEST_INTERVAL = 5000L // 5 segundos
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHARING -> {
                groupId = intent.getStringExtra("GROUP_ID")
                if (groupId != null) {
                    startLocationSharing()
                }
            }
            ACTION_STOP_SHARING -> {
                stopLocationSharing()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Compartir Ubicación",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación para compartir ubicación con el grupo"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationInFirebase(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun startLocationSharing() {
        if (isSharing) return

        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        // Iniciar como servicio en primer plano
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Configurar solicitud de ubicación
        val locationRequest = LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_UPDATE_FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Comenzar a recibir actualizaciones
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        isSharing = true
    }

    private fun stopLocationSharing() {
        if (!isSharing) {
            stopSelf()
            return
        }

        // Detener actualizaciones de ubicación
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Marcar como offline en Firebase
        markAsOffline()

        isSharing = false
        stopForeground(true)
        stopSelf()
    }

    private fun updateLocationInFirebase(latitude: Double, longitude: Double) {
        val currentUser = auth.currentUser ?: return
        if (groupId == null) return

        val memberRef = database.getReference("group_members")
            .child(groupId!!)
            .child(currentUser.uid)

        val updates = mapOf(
            "userId" to currentUser.uid,
            "userName" to (currentUser.displayName ?: "Usuario"),
            "latitude" to latitude,
            "longitude" to longitude,
            "isOnline" to true,
            "lastUpdate" to System.currentTimeMillis()
        )

        memberRef.updateChildren(updates)
    }

    private fun markAsOffline() {
        val currentUser = auth.currentUser ?: return
        if (groupId == null) return

        val memberRef = database.getReference("group_members")
            .child(groupId!!)
            .child(currentUser.uid)

        memberRef.child("isOnline").setValue(false)
        memberRef.child("lastUpdate").setValue(System.currentTimeMillis())
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, GroupMapActivity::class.java).apply {
            putExtra("GROUP_ID", groupId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Compartiendo ubicación")
            .setContentText("Tu grupo puede ver tu ubicación en tiempo real")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isSharing) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            markAsOffline()
        }
    }
}
