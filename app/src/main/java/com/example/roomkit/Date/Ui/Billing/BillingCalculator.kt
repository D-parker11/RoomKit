package com.example.roomkit.Date.Billing

/** Pure billing arithmetic in whole KHR. No exchange-rate conversion. */
object BillingCalculator {
    data class BillingInput(
        val rentKhr: Long,
        val previousElectricity: Long,
        val currentElectricity: Long,
        val electricityRateKhr: Long,
        val isWaterMeter: Boolean,
        val previousWater: Long,
        val currentWater: Long,
        val waterRateKhr: Long,
        val fixedWaterFeeKhr: Long,
        val rubbishFeeKhr: Long,
        val otherChargesKhr: Long = 0L,
        val discountKhr: Long = 0L,
        val electricityCarriedUsage: Long = 0L,
        val waterCarriedUsage: Long = 0L
    )

    data class BillingResult(
        val electricityUsage: Long,
        val electricityChargeKhr: Long,
        val waterUsage: Long,
        val waterChargeKhr: Long,
        val rubbishFeeKhr: Long,
        val otherChargesKhr: Long,
        val utilitySubtotalKhr: Long,
        val rentKhr: Long,
        val discountKhr: Long,
        val totalKhr: Long
    )

    fun calculate(input: BillingInput): BillingResult {
        require(listOf(input.rentKhr, input.electricityRateKhr, input.waterRateKhr,
            input.fixedWaterFeeKhr, input.rubbishFeeKhr, input.otherChargesKhr,
            input.discountKhr, input.electricityCarriedUsage, input.waterCarriedUsage).all { it >= 0L }) { "Amounts cannot be negative." }
        require(input.previousElectricity >= 0 && input.currentElectricity >= input.previousElectricity) {
            "Electricity reading cannot be negative or lower than the previous reading."
        }
        val electricityUsage = Math.addExact(input.electricityCarriedUsage, input.currentElectricity - input.previousElectricity)
        val electricityCharge = Math.multiplyExact(electricityUsage, input.electricityRateKhr)
        val waterUsage = if (input.isWaterMeter) {
            require(input.previousWater >= 0 && input.currentWater >= input.previousWater) {
                "Water reading cannot be negative or lower than the previous reading."
            }
            Math.addExact(input.waterCarriedUsage, input.currentWater - input.previousWater)
        } else 0L
        val waterCharge = if (input.isWaterMeter) Math.multiplyExact(waterUsage, input.waterRateKhr)
        else input.fixedWaterFeeKhr
        val utilities = listOf(electricityCharge, waterCharge, input.rubbishFeeKhr, input.otherChargesKhr)
            .fold(0L) { sum, value -> Math.addExact(sum, value) }
        val subtotal = Math.addExact(input.rentKhr, utilities)
        require(input.discountKhr <= subtotal) { "Discount cannot exceed the invoice subtotal." }
        return BillingResult(electricityUsage, electricityCharge, waterUsage, waterCharge,
            input.rubbishFeeKhr, input.otherChargesKhr, utilities, input.rentKhr,
            input.discountKhr, subtotal - input.discountKhr)
    }

    fun resolveInvoiceStatus(
        totalKhr: Long,
        amountPaidKhr: Long,
        dueDateTimestamp: Long,
        currentTimestamp: Long = System.currentTimeMillis()
    ): String = when {
        amountPaidKhr >= totalKhr -> "PAID"
        currentTimestamp > dueDateTimestamp -> "OVERDUE"
        amountPaidKhr > 0L -> "PARTIALLY_PAID"
        else -> "ISSUED"
    }
}
