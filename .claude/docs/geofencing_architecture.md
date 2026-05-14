# Geofencing Architecture — TrazaGo

## Overview

Proximity notifications alert tourists when they are near a tourist spot. The system uses the Google Play Services Geofencing API, a `BroadcastReceiver` for event handling, and `NotificationCompat` for delivery.

**Key file**: `ProximityNotificationManager.kt`

---

## Components

### ProximityNotificationManager

A regular `class` (not a singleton) instantiated per context. Holds a `GeofencingClient` and a `FirebaseFirestore` reference.

```
ProximityNotificationManager(context)
    ├── GeofencingClient          (Google Play Services)
    ├── FirebaseFirestore          (loads place coordinates)
    └── NotificationChannel       (created on init, API 26+)
```

### GeofenceBroadcastReceiver

Receives `GEOFENCE_TRANSITION_ENTER` events from the OS and delegates to `ProximityNotificationManager.showProximityNotification(placeId, placeName)`.

---

## Setup Flow

```
User enables proximity notifications in OfflineSettingsActivity
    ↓
ProximityNotificationManager.setupGeofencesForAllPlaces(radiusMeters, callback)
    ↓
1. Check ACCESS_FINE_LOCATION permission
2. Check ACCESS_BACKGROUND_LOCATION permission (required API 29+)
3. removeAllGeofences() — clears any previously registered geofences
4. Firestore query: FirestoreCollections.PLACES .limit(100)
5. For each place with non-null ubicacion:
       Geofence.Builder()
           .setRequestId(place.id)
           .setCircularRegion(lat, lng, radiusMeters)
           .setExpirationDuration(24h)
           .setTransitionTypes(GEOFENCE_TRANSITION_ENTER)
6. GeofencingClient.addGeofences(request, pendingIntent)
7. callback(success, count)
```

---

## Hard Limits

| Constant | Value | Source |
|---|---|---|
| `GEOFENCE_MAX_COUNT` | 100 | `AppConstants` (Google Play Services limit) |
| `GEOFENCE_EXPIRATION_MS` | 24 × 60 × 60 × 1000 ms | `AppConstants` / `ProximityNotificationManager` |
| Transition type | `ENTER` only | No `EXIT` or `DWELL` events registered |
| Initial trigger | `INITIAL_TRIGGER_ENTER` | Fires immediately if already inside on setup |

---

## Permissions

| Permission | Required from | Notes |
|---|---|---|
| `ACCESS_FINE_LOCATION` | All API levels | Must be granted before `setupGeofencesForAllPlaces` |
| `ACCESS_BACKGROUND_LOCATION` | API 29+ (Android 10) | Must be requested **after** foreground location (Play Store policy) |
| `POST_NOTIFICATIONS` | API 33+ (Android 13) | Checked in `sendNotification()` before `notify()` |

`hasBackgroundLocationPermission()` is public — used by `OfflineSettingsActivity` to gate the enable button.

---

## Notification Flow

```
OS detects GEOFENCE_TRANSITION_ENTER
    ↓
GeofenceBroadcastReceiver.onReceive()
    ↓
ProximityNotificationManager.showProximityNotification(placeId, placeName)
    ↓
Firestore: FirestoreCollections.PLACES/{placeId}
    ├── Success + place exists → sendNotification(place)
    │       NotificationCompat with BigTextStyle (name, description, rating)
    │       PendingIntent → PlaceDetailsActivity with PLACE_ID extra
    └── Failure or missing → sendSimpleNotification(placeName)
            NotificationCompat minimal
            PendingIntent → MenuActivity
```

Notification ID = `place.id.hashCode()` — prevents duplicate notifications for the same place if the user re-enters.

---

## PendingIntent Security

Uses `FLAG_IMMUTABLE` on API 31+ to prevent intent injection by co-resident apps:
```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
else
    PendingIntent.FLAG_UPDATE_CURRENT
```

---

## Notification Channel

| Property | Value |
|---|---|
| Channel ID | `proximity_notifications` |
| Channel name | `Notificaciones de Proximidad` |
| Importance | `IMPORTANCE_DEFAULT` |
| Created | On `ProximityNotificationManager` init (API 26+) |

---

## Known Limitations

- **100 geofence hard cap**: If Firestore has more than 100 places only the first 100 returned by Firestore are registered. Ordering is not deterministic — consider adding `.orderBy("visitCount", DESCENDING)` to prioritize popular spots.
- **24h expiration**: Geofences expire after 24 hours. Re-registering daily via `SyncWorker` (or the user toggling the feature off/on) keeps them fresh.
- **No EXIT/DWELL**: Only entry events fire. No "you've been here 10 min" or "you left" logic.
- **Background battery**: On Android 8+ the OS limits background location frequency. Google Play Services batches geofence events; expect up to ~2 min latency.
