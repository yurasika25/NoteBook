package com.notes.easynotebook.core.storage

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rateDataStore by preferencesDataStore(name = "rate_app_prefs")

internal object RateKeys {
    val HAS_RATED = booleanPreferencesKey("has_rated")
    val DO_NOT_ASK_AGAIN = booleanPreferencesKey("dont_ask_again")
    val LAST_SHOWN_AT_MS = longPreferencesKey("last_shown_at_ms")

    // NEW: usage gating
    val FIRST_OPEN_AT_MS = longPreferencesKey("first_open_at_ms")
    val SESSIONS_COUNT = intPreferencesKey("sessions_count")
}

data class RateState(
    val hasRated: Boolean,
    val doNotAskAgain: Boolean,
    val lastShownAtMs: Long,
    val firstOpenAtMs: Long,
    val sessionsCount: Int
)

class RatePrefs(private val appContext: Context) {

    val state: Flow<RateState> = appContext.rateDataStore.data.map { p ->
        RateState(
            hasRated = p[RateKeys.HAS_RATED] ?: false,
            doNotAskAgain = p[RateKeys.DO_NOT_ASK_AGAIN] ?: false,
            lastShownAtMs = p[RateKeys.LAST_SHOWN_AT_MS] ?: 0L,
            firstOpenAtMs = p[RateKeys.FIRST_OPEN_AT_MS] ?: 0L,
            sessionsCount = p[RateKeys.SESSIONS_COUNT] ?: 0
        )
    }

    suspend fun setLastShown(nowMs: Long) {
        appContext.rateDataStore.edit { it[RateKeys.LAST_SHOWN_AT_MS] = nowMs }
    }

    suspend fun markRated() {
        appContext.rateDataStore.edit { it[RateKeys.HAS_RATED] = true }
    }

    suspend fun setDoNotAskAgain(value: Boolean) {
        appContext.rateDataStore.edit { it[RateKeys.DO_NOT_ASK_AGAIN] = value }
    }

    /**
     * Call once per app session (e.g., onResume of main screen).
     * - stores firstOpenAt if not set
     * - increments sessionsCount
     */
    suspend fun onSessionStart(nowMs: Long) {
        appContext.rateDataStore.edit { prefs ->
            val first = prefs[RateKeys.FIRST_OPEN_AT_MS] ?: 0L
            if (first == 0L) prefs[RateKeys.FIRST_OPEN_AT_MS] = nowMs

            val current = prefs[RateKeys.SESSIONS_COUNT] ?: 0
            prefs[RateKeys.SESSIONS_COUNT] = current + 1
        }
    }
}
