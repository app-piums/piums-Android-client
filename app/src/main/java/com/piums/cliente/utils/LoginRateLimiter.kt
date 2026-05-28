package com.piums.cliente.utils

import android.content.Context

/**
 * Client-side brute-force protection. Lockout persists across app restarts via SharedPreferences.
 * 3 attempts  → 30 s lockout
 * 5 attempts  → 5 min lockout
 * 10 attempts → 15 min lockout
 */
class LoginRateLimiter(context: Context) {

    private val prefs = context.getSharedPreferences("piums_rate_limiter", Context.MODE_PRIVATE)

    private var failureCount: Int
        get()      = prefs.getInt(KEY_FAILURE_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_FAILURE_COUNT, value).apply()

    private var lockedUntilMs: Long
        get()      = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKED_UNTIL, value).apply()

    fun shouldBlock(): String? {
        val now = System.currentTimeMillis()
        val until = lockedUntilMs
        if (until > now) {
            val remaining = ((until - now) / 1000).toInt()
            return when {
                remaining >= 60 -> "Demasiados intentos. Espera ${remaining / 60} min ${remaining % 60} s."
                else            -> "Demasiados intentos. Espera $remaining s."
            }
        }
        return null
    }

    fun recordFailure() {
        val count = failureCount + 1
        failureCount = count
        val now = System.currentTimeMillis()
        lockedUntilMs = now + when {
            count >= 10 -> 15 * 60 * 1000L
            count >= 5  -> 5  * 60 * 1000L
            count >= 3  -> 30 * 1000L
            else        -> 0L
        }
    }

    fun reset() {
        prefs.edit().remove(KEY_FAILURE_COUNT).remove(KEY_LOCKED_UNTIL).apply()
    }

    companion object {
        private const val KEY_FAILURE_COUNT = "failure_count"
        private const val KEY_LOCKED_UNTIL  = "locked_until_ms"
    }
}
