package com.example.image_preview.core

import android.content.Context
import com.example.image_preview.data.local.AnalyticsDatabase
import com.example.image_preview.data.local.EventEntity
import com.example.image_preview.data.local.EventStatus
import com.example.image_preview.enrichment.DeviceInfoProvider
import com.example.image_preview.flush.FlushWorker
import com.example.image_preview.identity.IdentityManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The internal engine of the SDK.
 * Wires together identity, enrichment, persistence, and flushing.
 * Not exposed directly to SDK consumers — they use KadaraAnalytics instead.
 */
internal class EventTracker(
    private val context: Context,
    private val config: AnalyticsConfig
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val db = AnalyticsDatabase.getInstance(context)
    private val deviceInfoProvider = DeviceInfoProvider(context)
    val identityManager = IdentityManager(context)

    // Flush when this many events are queued (in addition to periodic flush)
    private val flushThreshold = config.flushAt

    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        scope.launch {
            val identity = identityManager.getIdentity()
            val distinctId = identity.userId ?: identity.anonymousId
            val sessionId = identityManager.getSessionId()
            val deviceInfo = deviceInfoProvider.get()

            // Merge developer properties with super properties (global props set once)
            val mergedProperties = config.superProperties + properties

            val entity = EventEntity(
                id = UUID.randomUUID().toString(),
                name = event,
                propertiesJson = gson.toJson(mergedProperties),
                timestamp = System.currentTimeMillis(),
                distinctId = distinctId,
                sessionId = sessionId,
                deviceInfoJson = gson.toJson(deviceInfo),
                status = EventStatus.PENDING
            )

            db.eventDao().insert(entity)

            // Check if we've hit the flush threshold
            val pendingCount = db.eventDao().totalCount()
            if (pendingCount >= flushThreshold) {
                FlushWorker.flush(context, config.apiKey, config.baseUrl)
            }

            config.logger?.invoke("📊 Captured: $event | pending: $pendingCount")
        }
    }

    fun observePendingCount(): Flow<Int> {
        return db.eventDao().observePendingCount()
    }

    fun flush() {
        FlushWorker.flush(context, config.apiKey, config.baseUrl)
    }
}

data class AnalyticsConfig(
    val apiKey: String,
    val baseUrl: String,
    val flushAt: Int = 20,                          // flush when queue hits this size
    val superProperties: Map<String, Any> = emptyMap(), // attached to every event
    val trackScreenViews: Boolean = true,
    val trackAppLifecycle: Boolean = true,
    val logger: ((String) -> Unit)? = null          // null = silent in production
) {
    class Builder {
        var apiKey: String? = null
        var baseUrl: String? = null
        var flushAt: Int = 20
        var superProperties: Map<String, Any> = emptyMap()
        var trackScreenViews: Boolean = true
        var trackAppLifecycle: Boolean = true
        var logger: ((String) -> Unit)? = null

        fun build(): AnalyticsConfig {
            val resolvedApiKey = apiKey?.takeIf { it.isNotBlank() }
                ?: error("KadaraAnalytics: apiKey is required")
            val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() }
                ?: error("KadaraAnalytics: baseUrl is required")

            return AnalyticsConfig(
                apiKey = resolvedApiKey,
                baseUrl = resolvedBaseUrl,
                flushAt = flushAt,
                superProperties = superProperties,
                trackScreenViews = trackScreenViews,
                trackAppLifecycle = trackAppLifecycle,
                logger = logger
            )
        }
    }
}
