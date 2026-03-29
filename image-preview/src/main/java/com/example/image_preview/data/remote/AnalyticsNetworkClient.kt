package com.example.image_preview.data.remote

import com.example.image_preview.data.local.EventEntity
import kotlinx.coroutines.delay
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow

// ─── Retrofit API interface ───────────────────────────────────────────────────

data class BatchPayload(
    val batch: List<Map<String, Any>>,
    val sentAt: Long = System.currentTimeMillis()
)

interface AnalyticsApi {
    @POST("batch")
    suspend fun sendBatch(
        @Header("Authorization") apiKey: String,
        @Body payload: BatchPayload
    ): Response<Unit>
}

// ─── Network Client ───────────────────────────────────────────────────────────

class AnalyticsNetworkClient(
    baseUrl: String,
    private val apiKey: String
) {
    private val api: AnalyticsApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AnalyticsApi::class.java)

    /**
     * Send a batch with exponential backoff retry.
     * Returns true if successful, false if should give up.
     */
    suspend fun sendBatch(
        events: List<Map<String, Any>>,
        maxRetries: Int = 3
    ): NetworkResult {
        var attempt = 0

        while (attempt <= maxRetries) {
            try {
                val response = api.sendBatch(
                    apiKey = "Bearer $apiKey",
                    payload = BatchPayload(batch = events)
                )

                return when {
                    response.isSuccessful -> NetworkResult.Success

                    // Rate limited — respect the server
                    response.code() == 429 -> {
                        val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 60
                        NetworkResult.RateLimited(retryAfterSeconds = retryAfter)
                    }

                    // Server error — retry
                    response.code() >= 500 -> {
                        attempt++
                        if (attempt > maxRetries) NetworkResult.Failure("Server error ${response.code()}")
                        else {
                            delay(exponentialBackoff(attempt))
                            continue
                        }
                    }

                    // Client error (4xx except 429) — don't retry, bad data
                    else -> NetworkResult.PermanentFailure("Client error ${response.code()}")
                }

            } catch (e: IOException) {
                // Network unavailable — retry with backoff
                attempt++
                if (attempt > maxRetries) return NetworkResult.Failure("Network error: ${e.message}")
                delay(exponentialBackoff(attempt))

            } catch (e: HttpException) {
                return NetworkResult.Failure("HTTP error: ${e.message}")
            }
        }

        return NetworkResult.Failure("Max retries exceeded")
    }

    /**
     * Exponential backoff: 2^attempt seconds, capped at 60 seconds.
     * attempt=1 → 2s, attempt=2 → 4s, attempt=3 → 8s
     */
    private fun exponentialBackoff(attempt: Int): Long {
        return min(2.0.pow(attempt).toLong() * 1000L, 60_000L)
    }
}

sealed class NetworkResult {
    object Success : NetworkResult()
    data class Failure(val reason: String) : NetworkResult()
    data class PermanentFailure(val reason: String) : NetworkResult()
    data class RateLimited(val retryAfterSeconds: Long) : NetworkResult()
}
