package com.joseibarra.touristnotify

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CheckInManager(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        val instance: CheckInManager by lazy { CheckInManager() }
    }

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
            db.collection(FirestoreCollections.CHECK_INS)
                .add(checkIn)
                .await()

            // Incrementar contador de visitas del lugar
            db.collection(FirestoreCollections.PLACES)
                .document(placeId)
                .update("visitCount", FieldValue.increment(1))
                .await()

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

            val snapshot = db.collection(FirestoreCollections.CHECK_INS)
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

            val yesterday = clock() - (24 * 60 * 60 * 1000)

            val snapshot = db.collection(FirestoreCollections.CHECK_INS)
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

}
