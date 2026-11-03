package com.example.roomkit.Date.Repositories

/** Pure rules also exercised by the supplied JVM tests. */
object MeterSaveValidation {
    fun period(month: Int, year: Int) {
        require(month in 1..12 && year in 1..9999) { "Invalid billing period." }
    }
    fun unchanged(expected: Any, actual: Any) {
        check(expected == actual) { "Tenant or meter details changed. Reopen the meter screen before saving." }
    }
    fun reading(previous: Long, current: Long, expectedPrevious: Long) {
        require(previous >= 0L && current >= 0L) { "Meter readings cannot be negative." }
        check(previous == expectedPrevious) { "The previous reading changed. Reopen the meter screen." }
        require(current >= previous) { "Current reading cannot be lower than the previous reading." }
    }
    fun replacement(closing: Long, opening: Long, minimumClosing: Long) {
        require(opening >= 0 && closing >= minimumClosing) { "Replacement values do not cover the old meter reading." }
    }
    fun noLaterReading(exists: Boolean) {
        check(!exists) { "A later reading exists. Earlier readings cannot be edited here." }
    }
}
