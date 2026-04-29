package com.example.halliplanner

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AvailabilityHelper {
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale("es", "MX"))
    private val dateTimeFormat = SimpleDateFormat("d/M/yyyy HH:mm", Locale("es", "MX"))

    fun namesFromText(value: String): Set<String> {
        return value.split(",", ";", "\n")
            .map { normalizeName(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun normalizeName(value: String): String {
        return value
            .substringBefore("(")
            .trim()
            .lowercase(Locale.ROOT)
    }

    fun rangesOverlap(
        startDateA: String,
        startTimeA: String,
        endDateA: String,
        endTimeA: String,
        startDateB: String,
        startTimeB: String,
        endDateB: String,
        endTimeB: String
    ): Boolean {
        val startA = parseDateTime(startDateA, startTimeA.ifBlank { "00:00" }) ?: return false
        val endA = parseDateTime(endDateA.ifBlank { startDateA }, endTimeA.ifBlank { "23:59" }) ?: startA
        val startB = parseDateTime(startDateB, startTimeB.ifBlank { "00:00" }) ?: return false
        val endB = parseDateTime(endDateB.ifBlank { startDateB }, endTimeB.ifBlank { "23:59" }) ?: startB
        return startA <= endB && startB <= endA
    }

    fun dateTimeInsideRange(date: String, time: String, startDate: String, startTime: String, endDate: String, endTime: String): Boolean {
        val point = parseDateTime(date, time.ifBlank { "00:00" }) ?: return false
        val start = parseDateTime(startDate, startTime.ifBlank { "00:00" }) ?: return false
        val end = parseDateTime(endDate.ifBlank { startDate }, endTime.ifBlank { "23:59" }) ?: start
        return point in start..end
    }

    fun parseDateTime(date: String, time: String): Long? {
        if (date.isBlank()) return null
        return runCatching {
            dateTimeFormat.parse("$date ${time.ifBlank { "00:00" }}")?.time
        }.getOrNull()
    }

    fun today(): String {
        val calendar = Calendar.getInstance()
        return dateFormat.format(calendar.time)
    }
}
