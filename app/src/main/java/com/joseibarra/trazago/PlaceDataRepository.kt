package com.joseibarra.trazago

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object PlaceDataRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun loadAllSpots(limit: Long = 200): Result<List<TouristSpot>> = runCatching {
        db.collection(FirestoreCollections.PLACES).limit(limit).get().await()
            .mapNotNull { doc ->
                runCatching { doc.toObject(TouristSpot::class.java).copy(id = doc.id) }.getOrNull()
            }
    }

    suspend fun searchByName(query: String): Result<List<TouristSpot>> = runCatching {
        db.collection(FirestoreCollections.PLACES)
            .orderBy("nombre")
            .startAt(query).endAt(query + '')
            .get().await()
            .mapNotNull { doc ->
                runCatching { doc.toObject(TouristSpot::class.java).copy(id = doc.id) }.getOrNull()
            }
    }

    suspend fun loadByIds(ids: List<String>): Result<List<TouristSpot>> = runCatching {
        if (ids.isEmpty()) return@runCatching emptyList()
        db.collection(FirestoreCollections.PLACES)
            .whereIn(FieldPath.documentId(), ids)
            .get().await()
            .mapNotNull { doc ->
                runCatching { doc.toObject(TouristSpot::class.java).copy(id = doc.id) }.getOrNull()
            }
    }

    suspend fun loadByNames(names: List<String>): Result<List<TouristSpot>> = runCatching {
        if (names.isEmpty()) return@runCatching emptyList()
        db.collection(FirestoreCollections.PLACES)
            .whereIn("nombre", names)
            .get().await()
            .mapNotNull { doc ->
                runCatching { doc.toObject(TouristSpot::class.java).copy(id = doc.id) }.getOrNull()
            }
    }
}
