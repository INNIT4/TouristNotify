package com.joseibarra.touristnotify

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FavoritesManager(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        val instance: FavoritesManager by lazy { FavoritesManager() }

        // Clave para saber si ya se ejecutó la migración del path legacy.
        private const val PREF_MIGRATION_V1 = "favorites_path_migrated_v1"
        private const val PREF_NAME = "favorites_migration"
    }

    /**
     * CR-006: Migración one-time del path legacy `usuarios/{uid}/favoritos/` al
     * path actual `users/{uid}/favorites/`. Se ejecuta una sola vez por dispositivo;
     * el resultado se persiste en SharedPreferences para no volver a consultar.
     */
    suspend fun migrateFromLegacyPath(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_MIGRATION_V1, false)) return

        try {
            val legacyDocs = db.collection("usuarios")
                .document(uid)
                .collection("favoritos")
                .get()
                .await()

            if (!legacyDocs.isEmpty) {
                val batch = db.batch()
                val newBase = db.collection(FirestoreCollections.USERS)
                    .document(uid)
                    .collection(FirestoreCollections.USER_FAVORITES)

                legacyDocs.documents.forEach { doc ->
                    batch.set(newBase.document(doc.id), doc.data ?: return@forEach)
                }
                batch.commit().await()
                Timber.d("FavoritesManager: migrados ${legacyDocs.size()} favoritos del path legacy")
            }
        } catch (e: Exception) {
            // No propagamos el error: la migración es best-effort.
            // Si falla (permiso denegado en path viejo, sin red), se reintentará
            // en la próxima sesión porque el flag no se escribe.
            Timber.w(e, "FavoritesManager: migración legacy falló, se reintentará")
            return
        }

        prefs.edit().putBoolean(PREF_MIGRATION_V1, true).apply()
    }

    suspend fun addFavorite(
        placeId: String,
        placeName: String,
        placeCategory: String,
        context: Context? = null
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            val favorite = Favorite(
                id = placeId,
                userId = userId,
                placeId = placeId,
                placeName = placeName,
                placeCategory = placeCategory
            )

            db.collection(FirestoreCollections.USERS)
                .document(userId)
                .collection(FirestoreCollections.USER_FAVORITES)
                .document(placeId)
                .set(favorite)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(placeId: String, context: Context? = null): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            db.collection(FirestoreCollections.USERS)
                .document(userId)
                .collection(FirestoreCollections.USER_FAVORITES)
                .document(placeId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isFavorite(placeId: String): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false

            val doc = db.collection(FirestoreCollections.USERS)
                .document(userId)
                .collection(FirestoreCollections.USER_FAVORITES)
                .document(placeId)
                .get()
                .await()

            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getFavorites(): Result<List<Favorite>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            val snapshot = db.collection(FirestoreCollections.USERS)
                .document(userId)
                .collection(FirestoreCollections.USER_FAVORITES)
                .get()
                .await()

            val favorites = snapshot.documents.mapNotNull {
                it.toObject(Favorite::class.java)?.copy(id = it.id)
            }

            Result.success(favorites)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
