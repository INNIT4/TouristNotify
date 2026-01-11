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
            GeoPoint(it[0].toDouble(), it[1].toDouble())
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
    val userRatingsTotal: Int,
    val address: String,
    val phoneNumber: String?,
    val website: String?,
    val openingHours: List<String>?,
    val photos: List<String>?,
    val priceLevel: Int,
    val isVerified: Boolean,
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
            userRatingsTotal = userRatingsTotal,
            address = address,
            phoneNumber = phoneNumber,
            website = website,
            openingHours = openingHours,
            photos = photos,
            priceLevel = priceLevel,
            isVerified = isVerified,
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
                userRatingsTotal = spot.userRatingsTotal,
                address = spot.address,
                phoneNumber = spot.phoneNumber,
                website = spot.website,
                openingHours = spot.openingHours,
                photos = spot.photos,
                priceLevel = spot.priceLevel,
                isVerified = spot.isVerified,
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
    val placeId: String,
    val placeName: String,
    val dateTimestamp: Long,
    val endDateTimestamp: Long?,
    val isFeatured: Boolean,
    val imageUrl: String?,
    val organizer: String,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toEvent(): Event {
        return Event(
            id = id,
            title = title,
            description = description,
            category = category,
            placeId = placeId,
            placeName = placeName,
            date = java.util.Date(dateTimestamp),
            endDate = endDateTimestamp?.let { java.util.Date(it) },
            isFeatured = isFeatured,
            imageUrl = imageUrl,
            organizer = organizer
        )
    }

    companion object {
        fun fromEvent(event: Event): EventEntity {
            return EventEntity(
                id = event.id,
                title = event.title,
                description = event.description,
                category = event.category,
                placeId = event.placeId,
                placeName = event.placeName,
                dateTimestamp = event.date?.time ?: System.currentTimeMillis(),
                endDateTimestamp = event.endDate?.time,
                isFeatured = event.isFeatured,
                imageUrl = event.imageUrl,
                organizer = event.organizer
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
    val author: String,
    val authorId: String,
    val imageUrl: String?,
    val likes: Int,
    val views: Int,
    val isFeatured: Boolean,
    val createdAtTimestamp: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toBlogPost(): BlogPost {
        return BlogPost(
            id = id,
            title = title,
            content = content,
            category = category,
            author = author,
            authorId = authorId,
            imageUrl = imageUrl,
            likes = likes,
            views = views,
            isFeatured = isFeatured,
            createdAt = java.util.Date(createdAtTimestamp)
        )
    }

    companion object {
        fun fromBlogPost(post: BlogPost): BlogPostEntity {
            return BlogPostEntity(
                id = post.id,
                title = post.title,
                content = post.content,
                category = post.category,
                author = post.author,
                authorId = post.authorId,
                imageUrl = post.imageUrl,
                likes = post.likes,
                views = post.views,
                isFeatured = post.isFeatured,
                createdAtTimestamp = post.createdAt?.time ?: System.currentTimeMillis()
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
