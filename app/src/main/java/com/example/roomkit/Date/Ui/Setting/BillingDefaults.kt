package com.example.roomkit.Date.Settings

data class BillingDefaults(
    val dueDay: Int = 5,
    val electricityRate: Long = 1000,
    val waterMetered: Boolean = false,
    val fixedWaterFee: Long = 20000,
    val waterRate: Long = 1500,
    val rubbishFee: Long = 10000
) {
    fun validated(): BillingDefaults {
        require(dueDay in 1..28) { "Due day must be between 1 and 28." }
        require(listOf(electricityRate, fixedWaterFee, waterRate, rubbishFee).all { it >= 0 }) {
            "Rates and fees must be zero or greater."
        }
        return this
    }
}
