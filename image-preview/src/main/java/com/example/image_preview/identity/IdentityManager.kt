package com.example.image_preview.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.image_preview.domain.model.UserIdentity
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.analyticsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "kadara_analytics_identity")

/**
 * Manages the user's identity across sessions.
 *
 * Key concepts:
 * - Anonymous ID: generated on first launch, never changes, persisted forever
 * - User ID: set when developer calls identify(), null until then
 * - Session ID: changes after 30 minutes of inactivity
 *
 * This is the same pattern PostHog, Mixpanel, and Amplitude use.
 */
class IdentityManager(private val context: Context) {

    companion object {
        private val KEY_ANONYMOUS_ID = stringPreferencesKey("anonymous_id")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_LAST_ACTIVE = longPreferencesKey("last_active")

        private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    }

    // Session ID lives in memory — it resets when app process dies (intentional)
    private var currentSessionId: String = generateSessionId()
    private var lastActiveTime: Long = System.currentTimeMillis()

    suspend fun getIdentity(): UserIdentity {
        val prefs = context.analyticsDataStore.data.first()

        // Get or create anonymous ID — this is permanent
        val anonymousId = prefs[KEY_ANONYMOUS_ID] ?: run {
            val newId = UUID.randomUUID().toString()
            context.analyticsDataStore.edit { it[KEY_ANONYMOUS_ID] = newId }
            newId
        }

        val userId = prefs[KEY_USER_ID]

        return UserIdentity(
            anonymousId = anonymousId,
            userId = userId
        )
    }

    /**
     * Called when the user logs in.
     * After this, all events will have both anonymousId and userId attached,
     * allowing your backend to merge the pre-login and post-login history.
     */
    suspend fun identify(userId: String, traits: Map<String, Any> = emptyMap()) {
        context.analyticsDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
        }
    }

    /**
     * Called on logout — clears the user ID but keeps the anonymous ID.
     * The anonymous ID never gets wiped because it's your only link to
     * pre-identification events.
     */
    suspend fun reset() {
        context.analyticsDataStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
        }
        // New session on logout too
        currentSessionId = generateSessionId()
    }

    /**
     * Returns the current session ID, rotating it if the user has been
     * inactive for longer than SESSION_TIMEOUT_MS.
     */
    fun getSessionId(): String {
        val now = System.currentTimeMillis()
        if (now - lastActiveTime > SESSION_TIMEOUT_MS) {
            currentSessionId = generateSessionId()
        }
        lastActiveTime = now
        return currentSessionId
    }

    /**
     * Returns the distinctId — userId if identified, anonymousId otherwise.
     * This is what gets attached to every event as the primary identifier.
     */
    suspend fun getDistinctId(): String {
        val identity = getIdentity()
        return identity.userId ?: identity.anonymousId
    }

    private fun generateSessionId() = UUID.randomUUID().toString()
}
