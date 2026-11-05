package com.example.roomkit.Date.Billing

import org.junit.Assert.*
import org.junit.Test

class BillingCalculatorTest {
    private fun fixture() = BillingCalculator.BillingInput(
        rentKhr=400_000, previousElectricity=890, currentElectricity=905, electricityRateKhr=1_000,
        isWaterMeter=true, previousWater=60, currentWater=72, waterRateKhr=1_500,
        fixedWaterFeeKhr=20_000, rubbishFeeKhr=5_000
    )
    @Test fun meteredBillUsesWholeKhr() {
        val r=BillingCalculator.calculate(fixture())
        assertEquals(15L,r.electricityUsage);assertEquals(15_000L,r.electricityChargeKhr)
        assertEquals(12L,r.waterUsage);assertEquals(18_000L,r.waterChargeKhr)
        assertEquals(438_000L,r.totalKhr)
    }
    @Test fun fixedWaterDoesNotBillMeteredUsage() {
        val r=BillingCalculator.calculate(fixture().copy(isWaterMeter=false))
        assertEquals(0L,r.waterUsage);assertEquals(20_000L,r.waterChargeKhr);assertEquals(440_000L,r.totalKhr)
    }
    @Test fun otherChargesAndDiscountAreAppliedOnce() {
        assertEquals(441_000L,BillingCalculator.calculate(fixture().copy(otherChargesKhr=5_000,discountKhr=2_000)).totalKhr)
    }
    @Test fun zeroUsageRetainsOnlyRentAndFees() {
        val r=BillingCalculator.calculate(fixture().copy(currentElectricity=890,currentWater=60))
        assertEquals(0L,r.electricityChargeKhr);assertEquals(0L,r.waterChargeKhr);assertEquals(405_000L,r.totalKhr)
    }
    @Test fun decreasingElectricityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(fixture().copy(currentElectricity=889)) }
    }
    @Test fun decreasingWaterIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(fixture().copy(currentWater=59)) }
    }
    @Test fun negativeMoneyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(fixture().copy(rentKhr=-1)) }
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(fixture().copy(discountKhr=-1)) }
    }
    @Test fun excessiveDiscountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(fixture().copy(discountKhr=438_001)) }
    }
    @Test fun fullDiscountCreatesZeroTotal() {
        assertEquals(0L,BillingCalculator.calculate(fixture().copy(discountKhr=438_000)).totalKhr)
    }
    @Test fun replacementCarryIsNotLost() {
        val r=BillingCalculator.calculate(fixture().copy(previousElectricity=0,currentElectricity=15,electricityCarriedUsage=20))
        assertEquals(35L,r.electricityUsage);assertEquals(35_000L,r.electricityChargeKhr)
    }
    @Test fun multiplicationOverflowIsRejected() {
        assertThrows(ArithmeticException::class.java) { BillingCalculator.calculate(fixture().copy(electricityRateKhr=Long.MAX_VALUE)) }
    }
    @Test fun subtotalOverflowIsRejected() {
        assertThrows(ArithmeticException::class.java) { BillingCalculator.calculate(fixture().copy(rentKhr=Long.MAX_VALUE)) }
    }
}
