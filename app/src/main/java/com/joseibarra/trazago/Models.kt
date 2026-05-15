package com.joseibarra.trazago

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.util.Date

// KT-011: Parceler personalizado para Date? — @Parcelize no soporta Date nativo.
object DateParceler : Parceler<Date?> {
    override fun create(parcel: Parcel): Date? {
        return if (parcel.readByte() == 0.toByte()) null else Date(parcel.readLong())
    }
    override fun Date?.write(parcel: Parcel, flags: Int) {
        if (this == null) { parcel.writeByte(0) } else { parcel.writeByte(1); parcel.writeLong(time) }
    }
}

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
 * Modelo para eventos en Álamos
 */
@Parcelize
@TypeParceler<Date?, DateParceler>
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
    val organizerContact: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tags: List<String> = emptyList(),
    val priceInfo: String = "",
    val websiteUrl: String = "",
    val recurrence: String = ""
) : Parcelable

/**
 * Modelo para posts del blog
 */
@Parcelize
@TypeParceler<Date?, DateParceler>
data class BlogPost(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val excerpt: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val authorName: String = "",
    val authorId: String = "",
    val tags: List<String> = emptyList(),
    val galleryImages: List<String> = emptyList(),
    @ServerTimestamp
    val publishedAt: Date? = null,
    val viewCount: Int = 0,
    val likes: Int = 0,
    val isFeatured: Boolean = false
) : Parcelable

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
    val timestamp: Long = System.currentTimeMillis(),
    val isMock: Boolean = false
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
    val authorName: String = "",
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

// ── Comunidad ─────────────────────────────────────────────────────────────────

@Parcelize
@TypeParceler<Date?, DateParceler>
data class CommunityPost(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val title: String = "",
    val content: String = "",
    val photoUrls: List<String> = emptyList(),
    val taggedPlaceId: String = "",
    val taggedPlaceName: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val reportCount: Int = 0,
    val isHidden: Boolean = false
) : Parcelable

data class PostComment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

data class PostReport(
    val id: String = "",
    val postId: String = "",
    val reporterId: String = "",
    val reason: String = "",
    val detail: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val status: String = "pending"
)

enum class ReportReason(val key: String, @StringRes val labelRes: Int) {
    SPAM("SPAM", R.string.report_reason_spam),
    OFFENSIVE("OFFENSIVE", R.string.report_reason_offensive),
    FAKE_INFO("FAKE_INFO", R.string.report_reason_fake),
    OTHER("OTHER", R.string.report_reason_other)
}
