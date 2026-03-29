package com.example.image_preview.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entity ──────────────────────────────────────────────────────────────────

/**
 * How events are stored in Room.
 * We serialize properties to JSON string since Room can't store Map directly.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val propertiesJson: String,   // serialized Map<String, Any>
    val timestamp: Long,
    val distinctId: String,
    val sessionId: String,
    val deviceInfoJson: String,   // serialized DeviceInfo
    val retryCount: Int = 0,
    val status: EventStatus = EventStatus.PENDING
)

enum class EventStatus {
    PENDING,    // waiting to be sent
    SENDING,    // currently in a batch being sent
    FAILED      // exceeded max retries, dead letter
}

// ─── DAO ─────────────────────────────────────────────────────────────────────

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    // Get oldest pending events up to a batch size limit
    @Query("SELECT * FROM events WHERE status = 'PENDING' ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingEvents(limit: Int = 50): List<EventEntity>

    // Observe count of pending events — useful for debugging UI
    @Query("SELECT COUNT(*) FROM events WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE events SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatus(ids: List<String>, status: EventStatus)

    @Query("UPDATE events SET retryCount = retryCount + 1 WHERE id IN (:ids)")
    suspend fun incrementRetryCount(ids: List<String>)

    // Successfully sent — remove from queue
    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    // Clean up dead letter events older than 7 days
    @Query("DELETE FROM events WHERE status = 'FAILED' AND timestamp < :cutoff")
    suspend fun purgeOldFailedEvents(cutoff: Long)

    @Query("SELECT COUNT(*) FROM events")
    suspend fun totalCount(): Int
}
