package com.joseibarra.touristnotify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    companion object {
        private const val CHANNEL_ID = "proximity_notifications"
        private const val CHANNEL_NAME = "Notificaciones de Proximidad"
        private const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 24 * 60 * 60 * 1000L // 24 horas
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
     * Configurar geofences para todos los lugares turísticos
     */
    fun setupGeofencesForAllPlaces(radiusInMeters: Float, callback: (Boolean, Int) -> Unit) {
        if (!hasLocationPermission()) {
            callback(false, 0)
            return
        }

        // Primero remover geofences anteriores
        removeAllGeofences()

        // Cargar lugares de Firebase
        db.collection("lugares_turisticos")
            .limit(100) // Límite de 100 geofences (límite de Google)
            .get()
            .addOnSuccessListener { documents ->
                val geofences = mutableListOf<Geofence>()

                for (document in documents) {
                    val place = document.toObject(TouristSpot::class.java).copy(id = document.id)
                    val location = place.ubicacion

                    if (location != null) {
                        val geofence = Geofence.Builder()
                            .setRequestId(place.id)
                            .setCircularRegion(
                                location.latitude,
                                location.longitude,
                                radiusInMeters
                            )
                            .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                            .build()

                        geofences.add(geofence)
                    }
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
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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

    /**
     * Mostrar notificación cuando el usuario entra a un geofence
     */
    fun showProximityNotification(placeId: String, placeName: String) {
        // Obtener detalles del lugar
        db.collection("lugares_turisticos").document(placeId)
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

    private fun sendNotification(place: TouristSpot) {
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

        val categoryEmoji = getCategoryEmoji(place.categoria)
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

    private fun sendSimpleNotification(placeName: String) {
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

    private fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "museo", "museum" -> "🏛️"
            "restaurante", "restaurant" -> "🍽️"
            "café", "cafe", "cafetería" -> "☕"
            "hotel" -> "🏨"
            "iglesia", "church" -> "⛪"
            "parque", "park" -> "🌳"
            "tienda", "shop" -> "🛍️"
            else -> "📍"
        }
    }
}
