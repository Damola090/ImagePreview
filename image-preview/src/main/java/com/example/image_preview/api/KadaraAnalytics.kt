package com.example.image_preview.api

import android.app.Application
import android.content.Context
import com.example.image_preview.core.AnalyticsConfig
import com.example.image_preview.core.EventTracker
import com.example.image_preview.flush.FlushWorker
import com.example.image_preview.lifecycle.ActivityLifecycleTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║              KadaraAnalytics — Public API                ║
 * ║                                                          ║
 * ║  This is the ONLY class SDK consumers interact with.     ║
 * ║  Everything else is internal implementation detail.      ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * Usage:
 *
 *   // In your Application.onCreate()
 *   KadaraAnalytics.setup(this) {
 *       apiKey = "your-api-key"
 *       baseUrl = "https://your-analytics-server.com/"
 *       trackScreenViews = true
 *   }
 *
 *   // Anywhere in your app
 *   KadaraAnalytics.capture("button_clicked", mapOf("button_id" to "sign_up"))
 *   KadaraAnalytics.identify("user_123", mapOf("plan" to "pro"))
 *   KadaraAnalytics.reset() // on logout
 */
object KadaraAnalytics {

    @Volatile
    private var tracker: EventTracker? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialize the SDK. Call this once in Application.onCreate().
     * Calling it multiple times is safe — subsequent calls are ignored.
     */
    fun setup(application: Application, block: AnalyticsConfig.Builder.() -> Unit) {
        if (tracker != null) return // already initialized

        synchronized(this) {
            if (tracker != null) return // double-checked locking

            val config = AnalyticsConfig.Builder().apply(block).build()
            val newTracker = EventTracker(application, config)
            tracker = newTracker

            // Schedule periodic background flushing via WorkManager
            FlushWorker.schedule(application, config.apiKey, config.baseUrl)

            // Register lifecycle callbacks for auto screen tracking
            if (config.trackScreenViews || config.trackAppLifecycle) {
                application.registerActivityLifecycleCallbacks(
                    ActivityLifecycleTracker(
                        tracker = newTracker,
                        shouldTrackScreenViews = config.trackScreenViews
                    )
                )
            }

            config.logger?.invoke("✅ KadaraAnalytics initialized")
        }
    }

    /**
     * Capture a custom event.
     *
     * @param event     Name of the event e.g. "Purchase Completed"
     * @param properties Additional data about the event
     */
    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        requireTracker().capture(event, properties)
    }

    /**
     * Identify the current user after login.
     * Merges pre-login anonymous events with the identified user on your server.
     *
     * @param userId  Your system's user ID
     * @param traits  User attributes e.g. name, email, plan
     */
    fun identify(userId: String, traits: Map<String, Any> = emptyMap()) {
        scope.launch {
            requireTracker().identityManager.identify(userId, traits)
            // Also send an identify event so your server knows about the merge
            requireTracker().capture(
                event = "User Identified",
                properties = mapOf("user_id" to userId) + traits
            )
        }
    }

    /**
     * Reset identity on logout. Clears user ID, starts a new session.
     * Anonymous ID is preserved to track pre-login behavior.
     */
    fun reset() {
        scope.launch {
            requireTracker().identityManager.reset()
            requireTracker().capture("User Logged Out")
        }
    }

    /**
     * Force an immediate flush of all pending events.
     * Useful to call before the app exits.
     */
    fun flush() {
        requireTracker().flush()
    }

    /**
     * Observe the count of events waiting to be sent.
     * Useful for a debug panel in your sandbox app.
     */
    fun observePendingCount(): Flow<Int> {
        return requireTracker().observePendingCount()
    }

    private fun requireTracker(): EventTracker {
        return tracker ?: error(
            "KadaraAnalytics not initialized. Call KadaraAnalytics.setup() in Application.onCreate()"
        )
    }
}

// ─── DSL Builder for clean configuration ─────────────────────────────────────

fun AnalyticsConfig.Builder.enableDebugLogging() {
    logger = { message -> android.util.Log.d("KadaraAnalytics", message) }
}
