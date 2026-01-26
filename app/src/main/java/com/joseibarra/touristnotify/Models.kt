package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modelo para lugares favoritos del usuario
 */
data class Favorite(
    val id: String = "",
    val userId: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val placeCategory: String = "",
    @ServerTimestamp
    val addedAt: Date? = null
)

/**
 * Modelo para check-ins en lugares
 */
data class CheckIn(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val placeCategory: String = "",
    @ServerTimestamp
    val checkInTime: Date? = null,
    val comment: String = "",
    val photoUrl: String = ""
)

/**
 * Modelo para estadísticas del usuario
 */
data class UserStats(
    val userId: String = "",
    val totalCheckIns: Int = 0,
    val totalFavorites: Int = 0,
    val placesVisited: Int = 0,
    val kmTraveled: Double = 0.0,
    val categoriesExplored: Map<String, Int> = emptyMap(),
    val badges: List<String> = emptyList(),
    val lastActivity: Date? = null
)

/**
 * Modelo para eventos en Álamos
 */
data class Event(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "", // Festival, Cultural, Religioso, etc.
    val location: String = "",
    val placeId: String = "",
    val startDate: Date? = null,
    val endDate: Date? = null,
    val imageUrl: String = "",
    val isFeatured: Boolean = false,
    val organizerName: String = "",
    val organizerContact: String = ""
)

/**
 * Modelo para posts del blog
 */
data class BlogPost(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val excerpt: String = "",
    val category: String = "", // Tips, Historia, Gastronomía, etc.
    val imageUrl: String = "",
    val authorName: String = "",
    val authorId: String = "", // ID del usuario que creó el post
    val tags: List<String> = emptyList(),
    @ServerTimestamp
    val publishedAt: Date? = null,
    val viewCount: Int = 0,
    val likes: Int = 0,
    val isFeatured: Boolean = false // Post destacado en la página principal
) : java.io.Serializable

/**
 * Modelo para información del clima
 */
data class WeatherInfo(
    val temperature: Double = 0.0,
    val feelsLike: Double = 0.0,
    val description: String = "",
    val icon: String = "",
    val humidity: Int = 0,
    val windSpeed: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Modelo para pronóstico diario del clima
 */
data class ForecastDay(
    val date: Date,
    val tempMax: Double,
    val tempMin: Double,
    val description: String,
    val icon: String
)

/**
 * Modelo para rutas temáticas
 */
data class ThemedRoute(
    val id: String = "",
    val name: String = "",
    val theme: String = "", // Histórica, Gastronómica, Fotográfica, etc.
    val description: String = "",
    val placeIds: List<String> = emptyList(),
    val estimatedDuration: String = "",
    val difficulty: String = "", // Fácil, Moderado, Difícil
    val imageUrl: String = "",
    val color: String = "#FF6B35",
    val icon: String = "🎨",
    val isFeatured: Boolean = false
)

/**
 * Modelo para fotos de lugares turísticos
 */
data class PlacePhoto(
    val id: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val uploadedBy: String = "",
    val uploaderName: String = "",
    val caption: String = "",
    @ServerTimestamp
    val uploadedAt: Date? = null,
    val likes: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

