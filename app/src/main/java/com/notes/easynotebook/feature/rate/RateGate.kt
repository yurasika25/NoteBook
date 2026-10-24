package com.notes.easynotebook.feature.rate

import com.notes.easynotebook.core.storage.RateState
import java.util.concurrent.TimeUnit

class RateGate(
    private val intervalMs: Long = TimeUnit.DAYS.toMillis(14),
    private val minSessions: Int = 8,
    private val minTimeSinceFirstOpenMs: Long = TimeUnit.HOURS.toMillis(48),
    private val minNotesCount: Int = 5
) {
    fun shouldShow(
        state: RateState,
        nowMs: Long,
        notesCount: Int
    ): Boolean {
        if (state.hasRated) return false
        if (state.doNotAskAgain) return false

        // NEW: don't show to brand new users
        if (notesCount < minNotesCount) return false
        if (state.sessionsCount < minSessions) return false

        // if firstOpenAt not yet set for some reason, be conservative
        if (state.firstOpenAtMs == 0L) return false
        if (nowMs - state.firstOpenAtMs < minTimeSinceFirstOpenMs) return false

        // biweekly rule (Later -> show after 14 days)
        return (nowMs - state.lastShownAtMs) >= intervalMs
    }
}
