package com.joseibarra.touristnotify

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object CheckInManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Hacer check-in en un lugar
     */
    suspend fun checkIn(
        placeId: String,
        placeName: String,
        placeCategory: String,
        comment: String = ""
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

            val checkIn = CheckIn(
                id = "",
                userId = currentUser.uid,
                userName = currentUser.displayName ?: "Usuario",
                placeId = placeId,
                placeName = placeName,
                placeCategory = placeCategory,
                comment = comment
            )

            // Guardar check-in
            db.collection("checkIns")
                .add(checkIn)
                .await()

            // Incrementar contador de visitas del lugar
            db.collection("lugares")
                .document(placeId)
                .update("visitCount", FieldValue.increment(1))
                .await()

            // Actualizar estadísticas del usuario
            updateUserStats(currentUser.uid, placeCategory)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtener check-ins del usuario
     */
    suspend fun getUserCheckIns(): Result<List<CheckIn>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            val snapshot = db.collection("checkIns")
                .whereEqualTo("userId", userId)
                .orderBy("checkInTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val checkIns = snapshot.documents.mapNotNull {
                it.toObject(CheckIn::class.java)?.copy(id = it.id)
            }

            Result.success(checkIns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verificar si el usuario ya hizo check-in en un lugar hoy
     */
    suspend fun hasCheckedInToday(placeId: String): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false

            // Obtener timestamp de hace 24 horas
            val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

            val snapshot = db.collection("checkIns")
                .whereEqualTo("userId", userId)
                .whereEqualTo("placeId", placeId)
                .whereGreaterThan("checkInTime", java.util.Date(yesterday))
                .get()
                .await()

            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Actualizar estadísticas del usuario
     */
    private suspend fun updateUserStats(userId: String, category: String) {
        try {
            val statsRef = db.collection("users").document(userId).collection("stats").document("summary")

            db.runTransaction { transaction ->
                val snapshot = transaction.get(statsRef)
                val stats = snapshot.toObject(UserStats::class.java) ?: UserStats(userId = userId)

                val updatedCategories = stats.categoriesExplored.toMutableMap()
                updatedCategories[category] = (updatedCategories[category] ?: 0) + 1

                val updatedStats = stats.copy(
                    totalCheckIns = stats.totalCheckIns + 1,
                    categoriesExplored = updatedCategories,
                    lastActivity = java.util.Date()
                )

                transaction.set(statsRef, updatedStats)
            }.await()
        } catch (e: Exception) {
            // Log pero no fallar
            android.util.Log.e("CheckInManager", "Error updating stats", e)
        }
    }
}
