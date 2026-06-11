package com.piums.cliente.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object Format {

    fun price(cents: Int): String = "$${String.format("%,.2f", cents / 100.0)}"

    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(iso.take(19)) ?: return ""
            SimpleDateFormat("dd MMM yyyy", Locale("es")).format(date)
        }.getOrDefault("")
    }

    fun shortDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(iso.take(10)) ?: return ""
            SimpleDateFormat("d MMM", Locale("es")).format(date)
        }.getOrDefault(iso?.take(10) ?: "")
    }

    fun dateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(iso.take(19)) ?: return ""
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")).format(date)
        }.getOrDefault("")
    }

    fun relativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(iso.take(19)) ?: return ""
            val diff = System.currentTimeMillis() - date.time
            when {
                diff < TimeUnit.MINUTES.toMillis(1)  -> "Ahora"
                diff < TimeUnit.HOURS.toMillis(1)    -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
                diff < TimeUnit.DAYS.toMillis(1)     -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
                diff < TimeUnit.DAYS.toMillis(7)     -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
                else -> date(iso)
            }
        }.getOrDefault("")
    }

    fun initialsOf(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }
}
