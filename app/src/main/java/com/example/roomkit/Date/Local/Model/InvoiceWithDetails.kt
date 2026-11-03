package com.example.roomkit.Date.Local.Model

import androidx.room.Embedded
import com.example.roomkit.Date.Local.Entity.InvoiceEntity

data class InvoiceWithDetails(
    @Embedded
    val invoice: InvoiceEntity,
    val tenantName: String,
    val tenantPhone: String,
    val roomNumber: String,
    val buildingName: String
)