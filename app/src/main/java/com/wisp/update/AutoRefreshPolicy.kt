package com.wisp.update

object AutoRefreshPolicy {
    const val MIN_INTERVAL_MS: Long = 900_000L

    fun isDue(lastAutoRefreshAt: Long, now: Long, minIntervalMs: Long = MIN_INTERVAL_MS): Boolean {
        if (lastAutoRefreshAt <= 0) return true
        if (lastAutoRefreshAt > now) return true
        return now - lastAutoRefreshAt >= minIntervalMs
    }
}
