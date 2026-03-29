package com.example.image_preview.flush

import android.content.Context
import androidx.work.*
import com.example.image_preview.data.local.AnalyticsDatabase
import com.example.image_preview.data.local.EventStatus
import com.example.image_preview.data.remote.AnalyticsNetworkClient
import com.example.image_preview.data.remote.NetworkResult
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that flushes pending events to the server.
 * WorkManager guarantees this runs even if the app is killed,
 * the device restarts, or the network was unavailable when first attempted.
 */
class FlushWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "kadara_analytics_flush"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val BATCH_SIZE = 50
        private const val MAX_RETRIES = 3

        /**
         * Schedule periodic background flushing every 15 minutes.
         * Call this once during SDK initialization.
         */
        fun schedule(context: Context, apiKey: String, baseUrl: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<FlushWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_API_KEY to apiKey,
                        KEY_BASE_URL to baseUrl
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // don't replace if already scheduled
                request
            )
        }

        /**
         * Trigger an immediate one-time flush (e.g. when batch size threshold is hit).
         */
        fun flush(context: Context, apiKey: String, baseUrl: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<FlushWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_API_KEY to apiKey, KEY_BASE_URL to baseUrl))
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val apiKey = inputData.getString(KEY_API_KEY) ?: return Result.failure()
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.failure()

        val dao = AnalyticsDatabase.getInstance(applicationContext).eventDao()
        val networkClient = AnalyticsNetworkClient(baseUrl, apiKey)
        val gson = Gson()

        // Get a batch of pending events
        val pending = dao.getPendingEvents(limit = BATCH_SIZE)
        if (pending.isEmpty()) return Result.success()

        // Mark them as SENDING so another worker doesn't pick them up
        dao.updateStatus(pending.map { it.id }, EventStatus.SENDING)

        // Serialize to the payload format your server expects
        val payload = pending.map { entity ->
            mapOf(
                "id" to entity.id,
                "event" to entity.name,
                "properties" to gson.fromJson(entity.propertiesJson, Map::class.java),
                "timestamp" to entity.timestamp,
                "distinct_id" to entity.distinctId,
                "session_id" to entity.sessionId,
                "device" to gson.fromJson(entity.deviceInfoJson, Map::class.java)
            )
        }

        return when (val result = networkClient.sendBatch(payload)) {
            is NetworkResult.Success -> {
                // Delete successfully sent events
                dao.deleteByIds(pending.map { it.id })
                Result.success()
            }

            is NetworkResult.RateLimited -> {
                // Put back to pending, WorkManager will retry
                dao.updateStatus(pending.map { it.id }, EventStatus.PENDING)
                Result.retry()
            }

            is NetworkResult.Failure -> {
                // Increment retry count, put back to pending
                dao.incrementRetryCount(pending.map { it.id })
                val exceededRetries = pending.filter { it.retryCount >= MAX_RETRIES }
                if (exceededRetries.isNotEmpty()) {
                    // Move to dead letter — stop retrying these
                    dao.updateStatus(exceededRetries.map { it.id }, EventStatus.FAILED)
                }
                val stillPending = pending.filter { it.retryCount < MAX_RETRIES }
                if (stillPending.isNotEmpty()) {
                    dao.updateStatus(stillPending.map { it.id }, EventStatus.PENDING)
                }
                Result.retry()
            }

            is NetworkResult.PermanentFailure -> {
                // Bad data — mark as failed, don't retry
                dao.updateStatus(pending.map { it.id }, EventStatus.FAILED)
                Result.failure()
            }
        }
    }
}
