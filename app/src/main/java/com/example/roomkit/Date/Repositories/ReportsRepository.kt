package com.example.roomkit.Date.Reports

import com.example.roomkit.Date.Dao.ReportDao
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ReportsRepository(private val dao: ReportDao) {
    suspend fun load(month: YearMonth): ReportTotals {
        val zone = ZoneId.of("Asia/Phnom_Penh")
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.load(month.monthValue, month.year, start, end, today)
    }

    suspend fun dashboard(month: YearMonth): ReportDashboard {
        val zone = ZoneId.of("Asia/Phnom_Penh")
        val totals = load(month)
        val previous = load(month.minusMonths(1))
        val trend = (5 downTo 0).map { offset ->
            val period = month.minusMonths(offset.toLong())
            val start = period.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            TrendPoint(period, dao.received(start, end))
        }
        return ReportDashboard(totals, previous, trend)
    }
}

data class TrendPoint(val month: YearMonth, val amount: Long)
data class ReportDashboard(val totals: ReportTotals, val previous: ReportTotals, val trend: List<TrendPoint>)
