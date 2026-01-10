package com.joseibarra.touristnotify

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room principal para modo offline
 */
@Database(
    entities = [
        TouristSpotEntity::class,
        EventEntity::class,
        BlogPostEntity::class,
        FavoriteEntity::class,
        CheckInEntity::class,
        OfflineMetadata::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun touristSpotDao(): TouristSpotDao
    abstract fun eventDao(): EventDao
    abstract fun blogPostDao(): BlogPostDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun checkInDao(): CheckInDao
    abstract fun offlineMetadataDao(): OfflineMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tourist_notify_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
