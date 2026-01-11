package com.joseibarra.touristnotify

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAOs (Data Access Objects) para operaciones de base de datos offline
 */

@Dao
interface TouristSpotDao {
    @Query("SELECT * FROM tourist_spots ORDER BY nombre ASC")
    fun getAllSpotsFlow(): Flow<List<TouristSpotEntity>>

    @Query("SELECT * FROM tourist_spots ORDER BY nombre ASC")
    suspend fun getAllSpots(): List<TouristSpotEntity>

    @Query("SELECT * FROM tourist_spots WHERE id = :id")
    suspend fun getSpotById(id: String): TouristSpotEntity?

    @Query("SELECT * FROM tourist_spots WHERE categoria = :category ORDER BY nombre ASC")
    suspend fun getSpotsByCategory(category: String): List<TouristSpotEntity>

    @Query("SELECT * FROM tourist_spots WHERE nombre LIKE '%' || :query || '%' OR descripcion LIKE '%' || :query || '%'")
    suspend fun searchSpots(query: String): List<TouristSpotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpot(spot: TouristSpotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpots(spots: List<TouristSpotEntity>)

    @Update
    suspend fun updateSpot(spot: TouristSpotEntity)

    @Delete
    suspend fun deleteSpot(spot: TouristSpotEntity)

    @Query("DELETE FROM tourist_spots")
    suspend fun deleteAllSpots()

    @Query("SELECT COUNT(*) FROM tourist_spots")
    suspend fun getSpotCount(): Int

    @Query("SELECT * FROM tourist_spots ORDER BY rating DESC LIMIT 10")
    suspend fun getTopSpots(): List<TouristSpotEntity>
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY dateTimestamp ASC")
    fun getAllEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY dateTimestamp ASC")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE category = :category ORDER BY dateTimestamp ASC")
    suspend fun getEventsByCategory(category: String): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int
}

@Dao
interface BlogPostDao {
    @Query("SELECT * FROM blog_posts ORDER BY createdAtTimestamp DESC")
    fun getAllPostsFlow(): Flow<List<BlogPostEntity>>

    @Query("SELECT * FROM blog_posts ORDER BY createdAtTimestamp DESC")
    suspend fun getAllPosts(): List<BlogPostEntity>

    @Query("SELECT * FROM blog_posts WHERE id = :id")
    suspend fun getPostById(id: String): BlogPostEntity?

    @Query("SELECT * FROM blog_posts WHERE category = :category ORDER BY createdAtTimestamp DESC")
    suspend fun getPostsByCategory(category: String): List<BlogPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: BlogPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<BlogPostEntity>)

    @Delete
    suspend fun deletePost(post: BlogPostEntity)

    @Query("DELETE FROM blog_posts")
    suspend fun deleteAllPosts()

    @Query("SELECT COUNT(*) FROM blog_posts")
    suspend fun getPostCount(): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAtTimestamp DESC")
    fun getFavoritesFlow(userId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAtTimestamp DESC")
    suspend fun getFavorites(userId: String): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE userId = :userId AND placeId = :placeId")
    suspend fun getFavorite(userId: String, placeId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId")
    suspend fun deleteAllFavorites(userId: String)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE userId = :userId ORDER BY timestampLong DESC")
    fun getCheckInsFlow(userId: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE userId = :userId ORDER BY timestampLong DESC")
    suspend fun getCheckIns(userId: String): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE userId = :userId AND placeId = :placeId")
    suspend fun getCheckInsForPlace(userId: String, placeId: String): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: CheckInEntity)

    @Delete
    suspend fun deleteCheckIn(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins WHERE userId = :userId")
    suspend fun deleteAllCheckIns(userId: String)
}

@Dao
interface OfflineMetadataDao {
    @Query("SELECT * FROM offline_metadata WHERE key = :key")
    suspend fun getMetadata(key: String): OfflineMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: OfflineMetadata)

    @Query("DELETE FROM offline_metadata WHERE key = :key")
    suspend fun deleteMetadata(key: String)
}
