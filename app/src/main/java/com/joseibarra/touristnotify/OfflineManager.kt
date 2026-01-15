package com.joseibarra.touristnotify

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manager para gestionar sincronización de datos offline
 */
class OfflineManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "offline_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREF_OFFLINE_MODE_ENABLED = "offline_mode_enabled"
        private const val PREF_LAST_SYNC_TIME = "last_sync_time"
        private const val PREF_AUTO_SYNC = "auto_sync"
    }

    /**
     * Verifica si el modo offline está habilitado
     */
    fun isOfflineModeEnabled(): Boolean {
        return prefs.getBoolean(PREF_OFFLINE_MODE_ENABLED, false)
    }

    /**
     * Habilita o deshabilita el modo offline
     */
    fun setOfflineModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_OFFLINE_MODE_ENABLED, enabled).apply()
    }

    /**
     * Verifica si la sincronización automática está habilitada
     */
    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_SYNC, true)
    }

    /**
     * Habilita o deshabilita la sincronización automática
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_SYNC, enabled).apply()
    }

    /**
     * Obtiene la última vez que se sincronizaron los datos
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(PREF_LAST_SYNC_TIME, 0)
    }

    /**
     * Sincroniza todos los datos desde Firebase a Room
     */
    suspend fun syncFromFirebase(): Result<Unit> {
        return try {
            // Sincronizar lugares turísticos
            syncTouristSpots()

            // Sincronizar eventos
            syncEvents()

            // Sincronizar posts del blog
            syncBlogPosts()

            // Sincronizar favoritos y check-ins del usuario
            auth.currentUser?.let { user ->
                syncUserData(user.uid)
            }

            // Actualizar timestamp de última sincronización
            prefs.edit().putLong(PREF_LAST_SYNC_TIME, System.currentTimeMillis()).apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sincroniza lugares turísticos
     */
    private suspend fun syncTouristSpots() {
        val snapshot = firestore.collection("lugares").get().await()
        val spots = snapshot.documents.mapNotNull { doc ->
            doc.toObject(TouristSpot::class.java)?.let { spot ->
                TouristSpotEntity.fromTouristSpot(spot.copy(id = doc.id))
            }
        }
        db.touristSpotDao().insertSpots(spots)
    }

    /**
     * Sincroniza eventos
     */
    private suspend fun syncEvents() {
        val snapshot = firestore.collection("events").get().await()
        val events = snapshot.documents.mapNotNull { doc ->
            doc.toObject(Event::class.java)?.let { event ->
                EventEntity.fromEvent(event.copy(id = doc.id))
            }
        }
        db.eventDao().insertEvents(events)
    }

    /**
     * Sincroniza posts del blog
     */
    private suspend fun syncBlogPosts() {
        val snapshot = firestore.collection("blog_posts").get().await()
        val posts = snapshot.documents.mapNotNull { doc ->
            doc.toObject(BlogPost::class.java)?.let { post ->
                BlogPostEntity.fromBlogPost(post.copy(id = doc.id))
            }
        }
        db.blogPostDao().insertPosts(posts)
    }

    /**
     * Sincroniza datos del usuario (favoritos y check-ins)
     */
    private suspend fun syncUserData(userId: String) {
        // Sincronizar favoritos
        val favoritesSnapshot = firestore.collection("favorites")
            .whereEqualTo("userId", userId)
            .get()
            .await()

        val favorites = favoritesSnapshot.documents.mapNotNull { doc ->
            doc.toObject(Favorite::class.java)?.let { favorite ->
                FavoriteEntity(
                    id = doc.id,
                    userId = favorite.userId,
                    placeId = favorite.placeId,
                    placeName = favorite.placeName,
                    category = favorite.placeCategory,
                    createdAtTimestamp = favorite.addedAt?.time ?: System.currentTimeMillis()
                )
            }
        }
        favorites.forEach { db.favoriteDao().insertFavorite(it) }

        // Sincronizar check-ins
        val checkInsSnapshot = firestore.collection("check_ins")
            .whereEqualTo("userId", userId)
            .get()
            .await()

        val checkIns = checkInsSnapshot.documents.mapNotNull { doc ->
            doc.toObject(CheckIn::class.java)?.let { checkIn ->
                CheckInEntity(
                    id = doc.id,
                    userId = checkIn.userId,
                    placeId = checkIn.placeId,
                    placeName = checkIn.placeName,
                    category = checkIn.placeCategory,
                    timestampLong = checkIn.checkInTime?.time ?: System.currentTimeMillis()
                )
            }
        }
        checkIns.forEach { db.checkInDao().insertCheckIn(it) }
    }

    /**
     * Limpia todos los datos offline
     */
    suspend fun clearOfflineData() {
        db.touristSpotDao().deleteAllSpots()
        db.eventDao().deleteAllEvents()
        db.blogPostDao().deleteAllPosts()

        auth.currentUser?.let { user ->
            db.favoriteDao().deleteAllFavorites(user.uid)
            db.checkInDao().deleteAllCheckIns(user.uid)
        }

        prefs.edit().putLong(PREF_LAST_SYNC_TIME, 0).apply()
    }

    /**
     * Obtiene el tamaño estimado de los datos offline en MB
     */
    suspend fun getOfflineDataSize(): Double {
        val spotCount = db.touristSpotDao().getSpotCount()
        val eventCount = db.eventDao().getEventCount()
        val postCount = db.blogPostDao().getPostCount()

        // Estimación aproximada: cada lugar ~2KB, evento ~1KB, post ~3KB
        val bytesEstimate = (spotCount * 2048) + (eventCount * 1024) + (postCount * 3072)
        return bytesEstimate / (1024.0 * 1024.0) // Convertir a MB
    }

    /**
     * Obtiene estadísticas de datos offline
     */
    suspend fun getOfflineStats(): OfflineStats {
        return OfflineStats(
            spotCount = db.touristSpotDao().getSpotCount(),
            eventCount = db.eventDao().getEventCount(),
            postCount = db.blogPostDao().getPostCount(),
            lastSyncTime = getLastSyncTime(),
            dataSizeMB = getOfflineDataSize()
        )
    }
}

data class OfflineStats(
    val spotCount: Int,
    val eventCount: Int,
    val postCount: Int,
    val lastSyncTime: Long,
    val dataSizeMB: Double
)
