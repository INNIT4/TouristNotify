package com.joseibarra.touristnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receiver para manejar eventos de geofencing
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        // Verificar si las notificaciones están habilitadas
        if (!ProximityNotificationsActivity.isEnabled(context)) {
            Log.d(TAG, "Proximity notifications are disabled")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        // Solo procesar cuando el usuario ENTRA al geofence
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            if (triggeringGeofences != null) {
                for (geofence in triggeringGeofences) {
                    val placeId = geofence.requestId
                    Log.d(TAG, "User entered geofence for place: $placeId")

                    // Mostrar notificación
                    val proximityManager = ProximityNotificationManager(context)
                    proximityManager.showProximityNotification(placeId, "lugar turístico")
                }
            }
        }
    }
}
