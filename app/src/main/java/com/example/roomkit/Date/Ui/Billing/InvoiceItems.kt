package com.example.roomkit.Date.Billing

import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.InvoiceItemEntity

object InvoiceItems {
    val codes = setOf("RENT", "ELECTRICITY", "WATER", "RUBBISH", "OTHER", "DISCOUNT")

    fun fromInvoice(i: InvoiceEntity): List<InvoiceItemEntity> {
        val fixed = i.waterBillingMethod.trim().equals("FIXED", true)
        fun line(code: String, label: String, amount: Long, order: Int,
                 quantity: Long? = 1L, unit: String = "FIXED", rate: Long? = amount) =
            InvoiceItemEntity(i.invoiceId, code, label, quantity, unit, rate, amount, order)
        return listOf(
            line("RENT", if (i.invoiceKind == "FINAL") "Additional rent" else "Monthly Rent", i.rentKhr, 0),
            line("ELECTRICITY", "Electricity", i.electricityChargeKhr, 1,
                i.electricityUsage, "kWh", i.electricityRateKhrSnapshot),
            line("WATER", if (fixed) "Water — Fixed" else "Water — Metered", i.waterChargeKhr, 2,
                if (fixed) 1L else i.waterUsage, if (fixed) "FIXED" else "m³",
                if (fixed) i.waterChargeKhr else i.waterRateKhrSnapshot),
            line("RUBBISH", "Rubbish / Waste", i.rubbishFeeKhrSnapshot, 3),
            line("OTHER", "Other charges", i.otherChargesKhr, 4),
            line("DISCOUNT", "Discount", Math.negateExact(i.discountKhr), 5,
                rate = i.discountKhr)
        ).also { validate(i, it) }
    }

    fun validate(i: InvoiceEntity, rows: List<InvoiceItemEntity>) {
        require(rows.size == codes.size && rows.map { it.itemCode }.toSet() == codes) {
            "Invoice items are incomplete. Reopen the invoice."
        }
        val expected = mapOf("RENT" to i.rentKhr, "ELECTRICITY" to i.electricityChargeKhr,
            "WATER" to i.waterChargeKhr, "RUBBISH" to i.rubbishFeeKhrSnapshot,
            "OTHER" to i.otherChargesKhr, "DISCOUNT" to Math.negateExact(i.discountKhr))
        rows.forEach { row ->
            require(row.invoiceId == i.invoiceId && row.amountKhr == expected[row.itemCode]) {
                "Stored items do not match the invoice snapshot."
            }
            require(if (row.itemCode == "DISCOUNT") row.amountKhr <= 0L else row.amountKhr >= 0L)
            require(row.quantity == null || row.quantity >= 0L)
            require(row.unitPriceKhr == null || row.unitPriceKhr >= 0L)
        }
        require(rows.fold(0L) { sum, row -> Math.addExact(sum, row.amountKhr) } == i.totalKhr) {
            "Stored invoice items do not add up to the saved total."
        }
        require(utilitySubtotal(rows) == i.utilitySubtotalKhr) { "Stored utility subtotal is inconsistent." }
    }

    fun utilitySubtotal(rows: List<InvoiceItemEntity>): Long = rows
        .filter { it.itemCode in setOf("ELECTRICITY", "WATER", "RUBBISH", "OTHER") }
        .fold(0L) { sum, row -> Math.addExact(sum, row.amountKhr) }
}
