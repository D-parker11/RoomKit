package com.example.roomkit.Date.Repositories

import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Local.Entity.InvoiceEntity

/** A sequence is scoped to a tenancy and meter type, not a room number. */
object MeterHistory {
    data class Basis(val previous: Long, val carried: Long = 0, val sequence: Int = 0) {
        fun usage(current: Long): Long {
            require(previous >= 0 && current >= previous && carried >= 0) { "Invalid meter reading." }
            return Math.addExact(carried, Math.subtractExact(current, previous))
        }
    }
    fun advance(base: Basis, events: List<MeterReadingEntity>): Basis {
        var result = base
        for (event in events.filter { it.readingType == "METERREPLACEMENT" && it.meterSequence > base.sequence }
            .sortedBy { it.meterSequence }) {
            check(event.meterSequence == Math.addExact(result.sequence, 1)) { "Meter replacement history has a gap." }
            require(event.previousReading >= result.previous && event.currentReading >= 0) { "Replacement closing reading is below the preceding reading." }
            result = Basis(event.currentReading, result.usage(event.previousReading), event.meterSequence)
        }
        return result
    }
    fun opening(rows: List<MeterReadingEntity>, fallback: Long): Basis {
        val initial = rows.firstOrNull { it.readingType == "INITIAL" }
        if (initial != null) return Basis(initial.currentReading, 0, initial.meterSequence)
        // Legacy baseline: use a saved monthly previous reading; do not invent an INITIAL event/date.
        val first = rows.filter { it.readingType == "MONTHLY" }
            .minWithOrNull(compareBy<MeterReadingEntity> { it.billingYear }.thenBy { it.billingMonth })
        return if (first != null) Basis(first.previousReading, first.carriedUsage, first.meterSequence)
        else Basis(fallback)
    }
    fun monthly(rows: List<MeterReadingEntity>, month: Int, year: Int, fallback: Long): Basis {
        val key = year * 12 + month
        val existing = rows.firstOrNull { it.readingType == "MONTHLY" && it.billingMonth == month && it.billingYear == year }
        if (existing != null) return Basis(existing.previousReading, existing.carriedUsage, existing.meterSequence)
        val prior = rows.filter { it.readingType == "MONTHLY" && it.billingYear * 12 + it.billingMonth < key }
            .maxWithOrNull(compareBy<MeterReadingEntity> { it.billingYear }.thenBy { it.billingMonth })
        val base = prior?.let { Basis(it.currentReading, 0, it.meterSequence) } ?: opening(rows, fallback)
        return advance(base, rows.filter { it.billingYear * 12 + it.billingMonth <= key })
    }
    fun minimumCurrent(rows: List<MeterReadingEntity>, basis: Basis): Long = maxOf(basis.previous,
        rows.filter { it.meterSequence == basis.sequence }.maxOfOrNull { it.currentReading } ?: basis.previous)
    fun settlementBasis(s: MoveOutSnapshot, type: String): MeterHistory.Basis {
        val rows = s.readings.filter { it.meterType == type }
        val official = s.invoices.filter { it.status.trim().uppercase() !in setOf("DRAFT","VOID","VOIDED") }
        val latest = official.filter { if(type == "ELECTRICITY") it.electricityCurrent != null else it.waterCurrent != null }
            .maxWithOrNull(compareBy<InvoiceEntity> { if(type == "ELECTRICITY") it.electricityMeterSequence else it.waterMeterSequence }
                .thenBy { if(type == "ELECTRICITY") it.electricityCurrent else it.waterCurrent })
        val base = latest?.let { MeterHistory.Basis(
            (if(type == "ELECTRICITY") it.electricityCurrent else it.waterCurrent)!!, 0,
            if(type == "ELECTRICITY") it.electricityMeterSequence else it.waterMeterSequence) }
            ?: MeterHistory.opening(rows, (if(type == "ELECTRICITY") s.room.initialElectricityReading else s.room.initialWaterReading)?.toLong() ?: 0L)
        return MeterHistory.advance(base, rows)
    }

}
