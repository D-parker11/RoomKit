@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

/** Later months are shown in the account summary, but are not collected on this invoice. */
val InvoiceDetailUiState.collectibleBalanceKhr: Long
    get() = Math.addExact(previousUnpaidKhr, balanceKhr)
