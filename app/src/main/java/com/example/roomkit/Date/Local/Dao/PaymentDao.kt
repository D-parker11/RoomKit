
package com.example.roomkit.Date.Local.Dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT p.* FROM payments p JOIN invoices i ON i.invoiceId=p.invoiceId WHERE i.tenancyId=:id ORDER BY p.paymentId")
    suspend fun getForTenancy(id: Long): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY paymentDate DESC, paymentId DESC")
    fun observePaymentsForInvoice(invoiceId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId")
    suspend fun getPaymentsForInvoice(invoiceId: Long): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(amountKhr), 0) FROM payments WHERE invoiceId = :invoiceId AND status = 'ACTIVE'")
    suspend fun getTotalPaidForInvoice(invoiceId: Long): Long

    @Query("UPDATE payments SET status = 'VOIDED', voidedAt = :voidedAt, voidReason = :reason WHERE paymentId = :paymentId AND status = 'ACTIVE' AND paymentMethod != 'DEPOSIT'")
    suspend fun voidPayment(paymentId: Long, reason: String, voidedAt: Long): Int

    @Query("SELECT * FROM payments WHERE paymentId = :paymentId LIMIT 1")
    suspend fun getPaymentById(paymentId: Long): PaymentEntity?

    @Query("SELECT * FROM payments")
    fun observeAllPayments(): Flow<List<PaymentEntity>>
}