package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modelo de datos para una reseña de usuario sobre un lugar turístico
 *
 * Representa la opinión y calificación de un usuario sobre un sitio.
 * Soporta reseñas con imagen opcional.
 *
 * Almacenado en Firestore en subcollection: lugares/{placeId}/reviews
 *
 * @property userId ID del usuario que escribió la reseña (Firebase Auth UID)
 * @property userName Nombre público del usuario (por defecto "Anónimo")
 * @property rating Calificación de 1 a 5 estrellas
 * @property comment Texto de la reseña
 * @property imageUrl URL de imagen opcional adjunta a la reseña
 * @property timestamp Fecha/hora de creación (auto-generada por Firestore)
 */
data class Review(
    val userId: String = "",
    val userName: String = "Anónimo",
    val rating: Float = 0f,
    val comment: String = "",
    val imageUrl: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)
