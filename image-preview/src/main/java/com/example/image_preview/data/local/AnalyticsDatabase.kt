package com.example.image_preview.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Room database for analytics event queue.
 */
@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(EventStatusConverter::class)
abstract class AnalyticsDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        private const val DB_NAME = "kadara_analytics.db"

        @Volatile
        private var INSTANCE: AnalyticsDatabase? = null

        fun getInstance(context: Context): AnalyticsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnalyticsDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Store EventStatus as a stable string value so queries can use literals like 'PENDING'.
 */
class EventStatusConverter {
    @TypeConverter
    fun fromStatus(status: EventStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): EventStatus = EventStatus.valueOf(value)
}
