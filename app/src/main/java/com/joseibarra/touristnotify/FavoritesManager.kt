package com.joseibarra.touristnotify

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FavoritesManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Agregar lugar a favoritos
     */
    suspend fun addFavorite(placeId: String, placeName: String, placeCategory: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            val favorite = Favorite(
                id = "",
                userId = userId,
                placeId = placeId,
                placeName = placeName,
                placeCategory = placeCategory
            )

            db.collection("users")
                .document(userId)
                .collection("favorites")
                .document(placeId)
                .set(favorite)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remover lugar de favoritos
     */
    suspend fun removeFavorite(placeId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            db.collection("users")
                .document(userId)
                .collection("favorites")
                .document(placeId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verificar si un lugar está en favoritos
     */
    suspend fun isFavorite(placeId: String): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false

            val doc = db.collection("users")
                .document(userId)
                .collection("favorites")
                .document(placeId)
                .get()
                .await()

            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtener todos los favoritos del usuario
     */
    suspend fun getFavorites(): Result<List<Favorite>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            val snapshot = db.collection("users")
                .document(userId)
                .collection("favorites")
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
