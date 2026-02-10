package com.notes.easynotebook.feature.rate

import com.notes.easynotebook.core.storage.RateState
import java.util.concurrent.TimeUnit

class RateGate(
    private val intervalMs: Long = TimeUnit.DAYS.toMillis(7)
) {
    fun shouldShow(state: RateState, nowMs: Long): Boolean {
        if (state.hasRated) return false
        if (state.doNotAskAgain) return false
        return (nowMs - state.lastShownAtMs) >= intervalMs
    }
}