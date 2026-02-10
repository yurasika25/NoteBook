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
}

data class RateState(
    val hasRated: Boolean,
    val doNotAskAgain: Boolean,
    val lastShownAtMs: Long
)

class RatePrefs(private val appContext: Context) {

    val state: Flow<RateState> = appContext.rateDataStore.data.map { p ->
        RateState(
            hasRated = p[RateKeys.HAS_RATED] ?: false,
            doNotAskAgain = p[RateKeys.DO_NOT_ASK_AGAIN] ?: false,
            lastShownAtMs = p[RateKeys.LAST_SHOWN_AT_MS] ?: 0L
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
}
