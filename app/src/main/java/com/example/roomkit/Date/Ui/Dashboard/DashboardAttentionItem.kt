@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Dashboard

enum class DashboardAttentionType {
    UNPAID,
    OVERDUE
}

data class DashboardAttentionItem(
    val type: DashboardAttentionType,
    val invoiceId: Long,
    val roomId: Long,
    val roomNumber: String,
    val buildingName: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String
)