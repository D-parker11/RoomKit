package com.example.roomkit.Date.Local.Model

data class MeterReadingUiState(
    val roomId: Long = 0L,
    val roomNumber: String = "",
    val tenantName: String = "",
    val billingMonth: Int = 9,
    val billingYear: Int = 2026,
    val billingMonthText: String = "September 2026",
    val hasActiveTenancy: Boolean = false,

    // Electricity
    val previousElectricity: Long = 0L,
    val currentElectricityInput: String = "",
    val electricityUsage: Long? = null,
    val electricityError: String? = null,

    // Water
    val isWaterFixed: Boolean = false,
    val fixedWaterFee: Long = 0L,
    val previousWater: Long = 0L,
    val currentWaterInput: String = "",
    val waterUsage: Long? = null,
    val waterError: String? = null,

    // Rubbish / Waste
    val rubbishFee: Long = 5000L,
    val currency: String = "KHR",

    // Lock / Form Status
    val isLockedByInvoice: Boolean = false,
    val isLoading: Boolean = true,
    val history: List<com.example.roomkit.Date.Local.Entity.MeterReadingEntity> = emptyList(),
    val electricityCarriedUsage: Long = 0L,
    val waterCarriedUsage: Long = 0L,
    val replacementType: String = "ELECTRICITY",
    val replacementClosing: String = "",
    val replacementOpening: String = "",
    val replacementReason: String = "",
    val isSaving: Boolean = false,
    val globalError: String? = null,
    val isSaveSuccessful: Boolean = false
) {
    val canSave: Boolean
        get() = hasActiveTenancy &&
                !isLockedByInvoice &&
                !isSaving && !isLoading && !isSaveSuccessful &&
                electricityError == null &&
                (isWaterFixed || waterError == null) &&
                currentElectricityInput.isNotBlank() &&
                (isWaterFixed || currentWaterInput.isNotBlank())
}