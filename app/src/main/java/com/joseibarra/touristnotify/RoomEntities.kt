package com.joseibarra.touristnotify

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.firebase.firestore.GeoPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Entidades Room para modo offline
 */

// ========== TYPE CONVERTERS ==========

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromGeoPoint(value: GeoPoint?): String? {
        return value?.let { "${it.latitude},${it.longitude}" }
    }

    @TypeConverter
    fun toGeoPoint(value: String?): GeoPoint? {
        return value?.split(",")?.let {
            if (it.size == 2) {
                GeoPoint(it[0].toDouble(), it[1].toDouble())
            } else null
        }
    }

    @TypeConverter
    fun fromTimestamp(value: Long?): java.util.Date? {
        return value?.let { java.util.Date(it) }
    }

    @TypeConverter
    fun toTimestamp(date: java.util.Date?): Long? {
        return date?.time
    }
}

// ========== ENTITIES ==========

@Entity(tableName = "tourist_spots")
@TypeConverters(Converters::class)
data class TouristSpotEntity(
    @PrimaryKey
    val id: String,
    val nombre: String,
    val descripcion: String,
    val categoria: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val reviewCount: Int,
    val direccion: String,
    val telefono: String?,
    val sitioWeb: String?,
    val horarios: String?,
    val imagenUrl: String?,
    val visitCount: Int,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toTouristSpot(): TouristSpot {
        return TouristSpot(
            id = id,
            nombre = nombre,
            descripcion = descripcion,
            categoria = categoria,
            ubicacion = GeoPoint(latitude, longitude),
            rating = rating,
            reviewCount = reviewCount,
            direccion = direccion,
            telefono = telefono ?: "",
            sitioWeb = sitioWeb ?: "",
            horarios = horarios ?: "",
            imagenUrl = imagenUrl ?: "",
            visitCount = visitCount
        )
    }

    companion object {
        fun fromTouristSpot(spot: TouristSpot): TouristSpotEntity {
            return TouristSpotEntity(
                id = spot.id,
                nombre = spot.nombre,
                descripcion = spot.descripcion,
                categoria = spot.categoria,
                latitude = spot.ubicacion?.latitude ?: 0.0,
                longitude = spot.ubicacion?.longitude ?: 0.0,
                rating = spot.rating,
                reviewCount = spot.reviewCount,
                direccion = spot.direccion,
                telefono = spot.telefono,
                sitioWeb = spot.sitioWeb,
                horarios = spot.horarios,
                imagenUrl = spot.imagenUrl,
                visitCount = spot.visitCount
            )
        }
    }
}

@Entity(tableName = "events")
@TypeConverters(Converters::class)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val placeId: String,
    val startDateTimestamp: Long,
    val endDateTimestamp: Long?,
    val isFeatured: Boolean,
    val imageUrl: String?,
    val organizerName: String,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toEvent(): Event {
        return Event(
            id = id,
            title = title,
            description = description,
            category = category,
            location = location,
            placeId = placeId,
            startDate = java.util.Date(startDateTimestamp),
            endDate = endDateTimestamp?.let { java.util.Date(it) },
            isFeatured = isFeatured,
            imageUrl = imageUrl ?: "",
            organizerName = organizerName
        )
    }

    companion object {
        fun fromEvent(event: Event): EventEntity {
            return EventEntity(
                id = event.id,
                title = event.title,
                description = event.description,
                category = event.category,
                location = event.location,
                placeId = event.placeId,
                startDateTimestamp = event.startDate?.time ?: System.currentTimeMillis(),
                endDateTimestamp = event.endDate?.time,
                isFeatured = event.isFeatured,
                imageUrl = event.imageUrl,
                organizerName = event.organizerName
            )
        }
    }
}

@Entity(tableName = "blog_posts")
@TypeConverters(Converters::class)
data class BlogPostEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val authorName: String,
    val authorId: String,
    val imageUrl: String?,
    val likes: Int,
    val viewCount: Int,
    val isFeatured: Boolean,
    val publishedAtTimestamp: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toBlogPost(): BlogPost {
        return BlogPost(
            id = id,
            title = title,
            content = content,
            category = category,
            authorName = authorName,
            authorId = authorId,
            imageUrl = imageUrl ?: "",
            likes = likes,
            viewCount = viewCount,
            isFeatured = isFeatured,
            publishedAt = java.util.Date(publishedAtTimestamp)
        )
    }

    companion object {
        fun fromBlogPost(post: BlogPost): BlogPostEntity {
            return BlogPostEntity(
                id = post.id,
                title = post.title,
                content = post.content,
                category = post.category,
                authorName = post.authorName,
                authorId = post.authorId,
                imageUrl = post.imageUrl,
                likes = post.likes,
                viewCount = post.viewCount,
                isFeatured = post.isFeatured,
                publishedAtTimestamp = post.publishedAt?.time ?: System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val placeId: String,
    val placeName: String,
    val category: String,
    val createdAtTimestamp: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val placeId: String,
    val placeName: String,
    val category: String,
    val timestampLong: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_metadata")
data class OfflineMetadata(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
