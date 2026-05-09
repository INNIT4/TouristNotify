package com.joseibarra.touristnotify

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReviewRepository(private val db: FirebaseFirestore) {

    suspend fun loadReviews(placeId: String, limit: Long = 20): Result<List<Review>> = runCatching {
        db.collection("lugares").document(placeId).collection("reviews")
            .orderBy("timestamp").limit(limit).get().await()
            .map { it.toObject(Review::class.java) }
    }

    suspend fun findExistingReview(placeId: String, userId: String): String? =
        runCatching {
            db.collection("lugares").document(placeId).collection("reviews")
                .whereEqualTo("userId", userId).get().await()
                .documents.firstOrNull()?.id
        }.getOrNull()

    suspend fun submitNewReview(
        placeId: String,
        userId: String,
        userName: String,
        rating: Float,
        comment: String
    ): Result<Unit> = runCatching {
        val placeRef = db.collection("lugares").document(placeId)
        db.runTransaction { tx ->
            val spot = tx.get(placeRef).toObject(TouristSpot::class.java)
                ?: throw Exception("No se pudo cargar la información del lugar")
            val newCount = spot.reviewCount + 1
            val newRating = ((spot.rating * spot.reviewCount) + rating) / newCount
            tx.update(placeRef, "rating", newRating)
            tx.update(placeRef, "reviewCount", newCount)
            tx.set(placeRef.collection("reviews").document(), Review(userId, userName, rating, comment))
            null
        }.await()
    }

    suspend fun updateExistingReview(
        placeId: String,
        reviewId: String,
        rating: Float,
        comment: String
    ): Result<Unit> = runCatching {
        val placeRef = db.collection("lugares").document(placeId)
        db.runTransaction { tx ->
            val spot = tx.get(placeRef).toObject(TouristSpot::class.java)
                ?: throw Exception("No se pudo cargar la información del lugar")
            val oldReview = tx.get(placeRef.collection("reviews").document(reviewId))
                .toObject(Review::class.java)
                ?: throw Exception("No se encontró la reseña anterior")
            val newRating = (spot.rating * spot.reviewCount - oldReview.rating + rating) / spot.reviewCount
            tx.update(placeRef, "rating", newRating)
            tx.update(placeRef.collection("reviews").document(reviewId), mapOf("rating" to rating, "comment" to comment))
            null
        }.await()
    }
}
