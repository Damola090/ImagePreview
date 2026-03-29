package com.example.image_preview.domain.model

import java.util.UUID

/**
 * Represents a single analytics event captured by the SDK.
 * This is the core data structure that flows through the entire pipeline.
 */
data class Event(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val properties: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val distinctId: String,
    val sessionId: String,
    val deviceInfo: DeviceInfo,
    val retryCount: Int = 0
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
    val appPackage: String,
    val networkType: String,
    val screenDensity: String,
    val locale: String
)

data class UserIdentity(
    val anonymousId: String,       // always present, generated on first launch
    val userId: String? = null,    // set after login
    val traits: Map<String, Any> = emptyMap()
)
