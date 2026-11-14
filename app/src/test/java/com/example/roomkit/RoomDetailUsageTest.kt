package com.example.roomkit

import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Ui.Room.RoomDetailFragment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class RoomDetailUsageTest {
    // Exercise the actual UI helper shared by electricity and metered water.
    private fun displayedUsage(previous: Long?, current: Long?, usage: Long?): Long? {
        val method = RoomDetailFragment::class.java.getDeclaredMethod(
            "validUsage", java.lang.Long::class.java, java.lang.Long::class.java,
            java.lang.Long::class.java)
        method.isAccessible = true
        return method.invoke(RoomDetailFragment(), previous, current, usage) as Long?
    }

    private fun reading(type: String, previous: Long, current: Long, carried: Long = 0) =
        MeterReadingEntity(tenancyId = 1, roomId = 1, meterType = type,
            previousReading = previous, currentReading = current, carriedUsage = carried,
            billingMonth = 11, billingYear = 2026)

    @Test fun replacementUsageIncludesOldMeterConsumptionForBothUtilities() {
        for (type in listOf("ELECTRICITY", "WATER")) {
            val monthly = reading(type, previous = 0, current = 5, carried = 10)
            assertEquals(15L, displayedUsage(monthly.previousReading,
                monthly.currentReading, monthly.usage))
        }
    }

    @Test fun ordinaryReadingsKeepTheirUsageForBothUtilities() {
        for (monthly in listOf(reading("ELECTRICITY", 100, 115), reading("WATER", 50, 56))) {
            assertEquals(monthly.currentReading - monthly.previousReading,
                displayedUsage(monthly.previousReading, monthly.currentReading, monthly.usage))
        }
    }

    @Test fun missingUsageFallsBackToValidReadingDifference() {
        assertEquals(15L, displayedUsage(100, 115, null))
        assertEquals(null, displayedUsage(100, 99, null))
        assertEquals(null, displayedUsage(null, null, null))
        assertEquals(null, displayedUsage(0, 5, -1))
    }
}
