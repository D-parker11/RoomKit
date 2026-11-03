package com.example.roomkit.Date.Dao

import androidx.room.Dao
import androidx.room.Query
import com.example.roomkit.Date.Reports.ReportTotals

@Dao
interface ReportDao {
    // One SELECT gives a consistent snapshot across all report sections.
    @Query("""
WITH balances AS (
 SELECT i.*, MAX(i.totalKhr - COALESCE(p.paid,0),0) AS remaining
 FROM invoices i
 LEFT JOIN (SELECT invoiceId,SUM(amountKhr) AS paid FROM payments
 WHERE UPPER(TRIM(status))='ACTIVE' GROUP BY invoiceId) p ON p.invoiceId=i.invoiceId
 WHERE UPPER(TRIM(i.status)) IN ('ISSUED','UNPAID','PARTIAL','PARTIALLY_PAID','PAID','OVERDUE')
), selected AS (SELECT * FROM balances WHERE billingMonth=:month AND billingYear=:year),
receipts AS (
 SELECT p.* FROM payments p JOIN invoices i ON i.invoiceId=p.invoiceId
 WHERE UPPER(TRIM(p.status))='ACTIVE' AND UPPER(TRIM(i.status)) IN ('ISSUED','UNPAID','PARTIAL','PARTIALLY_PAID','PAID','OVERDUE')
 AND p.paymentDate>=:start AND p.paymentDate<:end
)
SELECT
(SELECT COALESCE(SUM(totalKhr),0) FROM selected) AS billed,
(SELECT COALESCE(SUM(remaining),0) FROM selected) AS remaining,
(SELECT COUNT(*) FROM selected) AS invoiceCount,
(SELECT COUNT(*) FROM selected WHERE remaining=0) AS paidCount,
(SELECT COUNT(*) FROM selected WHERE remaining>0 AND remaining<totalKhr) AS partialCount,
(SELECT COUNT(*) FROM selected WHERE remaining>0 AND dueDate<:today) AS overdueCount,
(SELECT COALESCE(SUM(remaining),0) FROM selected WHERE remaining>0 AND dueDate<:today) AS overdueAmount,
(SELECT COALESCE(SUM(remaining),0) FROM balances) AS allOutstanding,
(SELECT COALESCE(SUM(remaining),0) FROM balances WHERE remaining>0 AND dueDate<:today) AS allOverdue,
(SELECT COALESCE(SUM(amountKhr),0) FROM receipts WHERE UPPER(TRIM(paymentMethod))<>'DEPOSIT') AS received,
(SELECT COALESCE(SUM(amountKhr),0) FROM receipts WHERE UPPER(TRIM(paymentMethod))='DEPOSIT') AS depositApplied,
(SELECT COALESCE(SUM(amountKhr),0) FROM receipts WHERE UPPER(TRIM(paymentMethod))='CASH') AS cash,
(SELECT COALESCE(SUM(amountKhr),0) FROM receipts WHERE UPPER(TRIM(paymentMethod)) IN ('BANK','BANK_TRANSFER','ABA','WING')) AS bank,
(SELECT COALESCE(SUM(amountKhr),0) FROM receipts WHERE UPPER(TRIM(paymentMethod)) IN ('KHQR','QR')) AS khqr,
(SELECT COALESCE(SUM(amountKhr),0) FROM receipts WHERE UPPER(TRIM(paymentMethod)) NOT IN ('CASH','BANK','BANK_TRANSFER','ABA','WING','KHQR','QR','DEPOSIT')) AS otherPayments,
(SELECT COALESCE(SUM(rentKhr),0) FROM selected) AS rent,
(SELECT COALESCE(SUM(electricityChargeKhr),0) FROM selected) AS electricity,
(SELECT COALESCE(SUM(waterChargeKhr),0) FROM selected) AS water,
(SELECT COALESCE(SUM(rubbishFeeKhrSnapshot),0) FROM selected) AS rubbish,
(SELECT COALESCE(SUM(otherChargesKhr),0) FROM selected) AS otherCharges,
(SELECT COALESCE(SUM(discountKhr),0) FROM selected) AS discount,
(SELECT COUNT(*) FROM rooms WHERE UPPER(TRIM(status))<>'ARCHIVED') AS rooms,
(SELECT COUNT(*) FROM rooms r WHERE UPPER(TRIM(r.status))<>'ARCHIVED' AND EXISTS (SELECT 1 FROM tenancies t WHERE t.roomId=r.roomId AND UPPER(TRIM(t.status))='ACTIVE')) AS occupied,
(SELECT COALESCE(SUM(refundKhr),0) FROM move_out_settlements WHERE refundPaid=0 AND refundKhr>0) AS pendingRefund,
(SELECT COUNT(*) FROM move_out_settlements WHERE refundPaid=0 AND refundKhr>0) AS pendingRefundCount,
(SELECT COALESCE(SUM(electricityUsage),0) FROM selected) AS electricityUsage,
(SELECT COALESCE(SUM(waterUsage),0) FROM selected WHERE UPPER(TRIM(waterBillingMethod))<>'FIXED') AS waterUsage
    """)
    suspend fun load(month: Int, year: Int, start: Long, end: Long, today: Long): ReportTotals

    @Query("""
        SELECT COALESCE(SUM(p.amountKhr),0)
        FROM payments p JOIN invoices i ON i.invoiceId=p.invoiceId
        WHERE UPPER(TRIM(p.status))='ACTIVE'
          AND UPPER(TRIM(p.paymentMethod))<>'DEPOSIT'
          AND UPPER(TRIM(i.status)) IN ('ISSUED','UNPAID','PARTIAL','PARTIALLY_PAID','PAID','OVERDUE')
          AND p.paymentDate>=:start AND p.paymentDate<:end
    """)
    suspend fun received(start: Long, end: Long): Long
}
