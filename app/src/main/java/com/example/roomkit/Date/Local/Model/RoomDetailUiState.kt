package com.example.roomkit.Date.Local.Model

data class RoomDetailUiState(
    val roomId: Long = 0L,
    val roomNumber: String = "",
    val buildingId: Long = 0L,
    val buildingName: String = "",
    val isOccupied: Boolean = false,
    val monthlyRent: Long = 0L,

    // Active Tenant Information
    val tenancyId: Long? = null,
    val tenantId: Long? = null,
    val tenantName: String? = null,
    val tenantPhone: String? = null,
    val moveInDate: Long? = null,
    val deposit: Long? = null,

    // Meter Readings
    val periodLabel: String = "",
    val billingMonth: Int = 9,
    val billingYear: Int = 2026,
    val electricityPrevious: Long? = null,
    val electricityCurrent: Long? = null,
    val electricityUsage: Long? = null,
    val waterIsFixed: Boolean = false,
    val waterPrevious: Long? = null,
    val waterCurrent: Long? = null,
    val waterUsage: Long? = null,

    // Invoice
    val invoiceId: Long? = null,
    val invoiceStatus: String? = null, // "UNPAID", "PAID", "OVERDUE"
    val rentCharge: Long? = null,
    val electricityCharge: Long? = null,
    val waterCharge: Long? = null,
    val rubbishCharge: Long? = null,
    val totalInvoiceAmount: Long? = null,
    val invoiceDueDate: Long? = null
)