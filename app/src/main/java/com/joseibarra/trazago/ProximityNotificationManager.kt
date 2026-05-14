package com.joseibarra.trazago

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Manager para gestionar notificaciones de proximidad usando Geofences
 */
class ProximityNotificationManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val db = FirebaseFirestore.getInstance()

    private val cooldownPrefs: SharedPreferences =
        context.getSharedPreferences(COOLDOWN_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val CHANNEL_ID = "proximity_notifications"
        private const val CHANNEL_NAME = "Notificaciones de Proximidad"
        private const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 24 * 60 * 60 * 1000L // 24 horas
        private const val MAX_GEOFENCES = 100 // Límite de Google
        private const val NOTIFICATION_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24h por lugar
        private const val COOLDOWN_PREFS_NAME = "proximity_cooldown_prefs"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notificaciones cuando estás cerca de lugares turísticos"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Configurar geofences para todos los lugares turísticos.
     * Si [userLocation] está disponible, prioriza los 100 lugares más cercanos
     * (Google limita a 100 geofences activos por app). Si es null, toma los
     * primeros 100 que devuelva Firestore.
     */
    fun setupGeofencesForAllPlaces(
        radiusInMeters: Float,
        userLocation: Location? = null,
        callback: (Boolean, Int) -> Unit
    ) {
        if (!hasLocationPermission() || !hasBackgroundLocationPermission()) {
            callback(false, 0)
            return
        }

        // Primero remover geofences anteriores
        removeAllGeofences()

        // Cargar TODOS los lugares (sin límite Firestore) para poder priorizar por cercanía
        db.collection(FirestoreCollections.PLACES)
            .get()
            .addOnSuccessListener { documents ->
                // Mapear a (TouristSpot, distancia). distancia = Float.MAX si no hay ubicación
                val candidates = documents.mapNotNull { document ->
                    val place = document.toObject(TouristSpot::class.java).copy(id = document.id)
                    val location = place.ubicacion ?: return@mapNotNull null
                    val distance = if (userLocation != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            userLocation.latitude, userLocation.longitude,
                            location.latitude, location.longitude,
                            results
                        )
                        results[0]
                    } else {
                        Float.MAX_VALUE
                    }
                    Triple(place, location, distance)
                }

                // Si tenemos ubicación: ordenar por distancia ascendente. Si no: orden natural.
                val prioritized = if (userLocation != null) {
                    candidates.sortedBy { it.third }
                } else {
                    candidates
                }.take(MAX_GEOFENCES)

                val geofences = prioritized.map { (place, location, _) ->
                    Geofence.Builder()
                        .setRequestId(place.id)
                        .setCircularRegion(
                            location.latitude,
                            location.longitude,
                            radiusInMeters
                        )
                        .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                        .build()
                }

                if (geofences.isNotEmpty()) {
                    addGeofences(geofences, callback)
                } else {
                    callback(false, 0)
                }
            }
            .addOnFailureListener {
                callback(false, 0)
            }
    }

    private fun addGeofences(geofences: List<Geofence>, callback: (Boolean, Int) -> Unit) {
        if (!hasLocationPermission()) {
            callback(false, 0)
            return
        }

        val geofencingRequest = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofences)
        }.build()

        val pendingIntent = getGeofencePendingIntent()

        try {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    callback(true, geofences.size)
                }
                .addOnFailureListener {
                    callback(false, 0)
                }
        } catch (e: SecurityException) {
            callback(false, 0)
        }
    }

    /**
     * Remover todos los geofences activos
     */
    fun removeAllGeofences() {
        geofencingClient.removeGeofences(getGeofencePendingIntent())
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // FLAG_IMMUTABLE evita que un actor coresidente con el mismo permiso
        // pueda inyectar extras en el Intent broadcast. Los geofences no
        // requieren mutabilidad (el GeofencingClient adjunta el evento al
        // PendingIntent existente sin modificarlo).
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // AND-004: los geofences requieren ACCESS_BACKGROUND_LOCATION en API 29+.
    // Debe solicitarse separado DESPUÉS de conceder foreground location (política Play Store).
    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Mostrar notificación cuando el usuario entra a un geofence.
     * Aplica cooldown de 24h por lugar para evitar spam si el usuario sale y
     * vuelve a entrar al radio (típico al pasear cerca de un punto).
     */
    fun showProximityNotification(placeId: String, placeName: String) {
        if (isInCooldown(placeId)) return
        markNotified(placeId)

        // Obtener detalles del lugar
        db.collection(FirestoreCollections.PLACES).document(placeId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val place = document.toObject(TouristSpot::class.java)?.copy(id = document.id)
                    if (place != null) {
                        sendNotification(place)
                    }
                } else {
                    // Notificación simple si no se puede obtener detalles
                    sendSimpleNotification(placeName)
                }
            }
            .addOnFailureListener {
                sendSimpleNotification(placeName)
            }
    }

    // AND-007: verificar POST_NOTIFICATIONS en API 33+ antes de notify()
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun sendNotification(place: TouristSpot) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, PlaceDetailsActivity::class.java).apply {
            putExtra("PLACE_ID", place.id)
            putExtra("PLACE_NAME", place.nombre)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            place.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val categoryEmoji = CategoryUtils.getCategoryEmoji(place.categoria)
        val ratingText = if (place.rating > 0) {
            "⭐ ${place.rating} (${place.reviewCount} reseñas)"
        } else {
            "Lugar interesante"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_travel)
            .setContentTitle("$categoryEmoji ${place.nombre}")
            .setContentText("Estás cerca de este lugar. $ratingText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${place.descripcion}\n\n$ratingText\n\nToca para ver más detalles.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(place.id.hashCode(), notification)
    }

    private fun isInCooldown(placeId: String): Boolean {
        val lastNotified = cooldownPrefs.getLong("last_$placeId", 0L)
        return System.currentTimeMillis() - lastNotified < NOTIFICATION_COOLDOWN_MS
    }

    private fun markNotified(placeId: String) {
        cooldownPrefs.edit()
            .putLong("last_$placeId", System.currentTimeMillis())
            .apply()
    }

    /**
     * Limpia el cooldown de todos los lugares. Útil cuando el usuario reconfigura
     * los geofences o cambia el radio — quiere ver notificaciones frescas.
     */
    fun clearCooldowns() {
        cooldownPrefs.edit().clear().apply()
    }

    private fun sendSimpleNotification(placeName: String) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, MenuActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_travel)
            .setContentTitle("📍 Lugar cercano")
            .setContentText("Estás cerca de $placeName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(placeName.hashCode(), notification)
    }
}
