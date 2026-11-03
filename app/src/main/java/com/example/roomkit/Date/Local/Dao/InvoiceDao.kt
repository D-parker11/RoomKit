package com.example.roomkit.Date.Local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.roomkit.Date.Local.Model.InvoiceDetailWithItems
import androidx.room.Update
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Model.InvoiceWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE tenancyId = :id ORDER BY invoiceId")
    suspend fun getForTenancy(id: Long): List<InvoiceEntity>


    @Query("""
        SELECT * FROM invoices
        WHERE invoiceId = :invoiceId
        LIMIT 1
    """)
    suspend fun getInvoiceById(
        invoiceId: Long
    ): InvoiceEntity?

    @Query("""
        SELECT * FROM invoices
        WHERE invoiceId = :invoiceId
        LIMIT 1
    """)
    fun observeInvoiceById(
        invoiceId: Long
    ): Flow<InvoiceEntity?>

    @Query("""SELECT * FROM invoices WHERE tenancyId=:tenancyId
        AND billingMonth=:month AND billingYear=:year AND invoiceKind='REGULAR'
        ORDER BY CASE WHEN UPPER(TRIM(status)) IN ('VOID','VOIDED') THEN 1 ELSE 0 END,
            revision DESC, invoiceId DESC LIMIT 1""")
    suspend fun getInvoiceForMonth(tenancyId: Long, month: Int, year: Int): InvoiceEntity?

    @Query("""SELECT * FROM invoices WHERE tenancyId=:tenancyId
        AND billingMonth=:month AND billingYear=:year AND invoiceKind='REGULAR'
        ORDER BY CASE WHEN UPPER(TRIM(status)) IN ('VOID','VOIDED') THEN 1 ELSE 0 END,
            revision DESC, invoiceId DESC LIMIT 1""")
    fun observeInvoiceForMonth(tenancyId: Long, month: Int, year: Int): Flow<InvoiceEntity?>

    @Query("""
        SELECT * FROM invoices
        WHERE tenancyId = :tenancyId
        ORDER BY billingYear DESC, billingMonth DESC
    """)
    fun observeInvoicesForTenancy(
        tenancyId: Long
    ): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        ORDER BY billingYear DESC, billingMonth DESC, invoiceId DESC
    """)
    fun observeAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE balanceKhr > 0
          AND status IN (
              'ISSUED',
              'UNPAID',
              'PARTIAL',
              'PARTIALLY_PAID',
              'OVERDUE'
          )
        ORDER BY dueDate ASC, invoiceId ASC
    """)
    suspend fun getUnpaidInvoices(): List<InvoiceEntity>

    @Query("""
        SELECT
            i.*,
            t.fullName AS tenantName,
            t.phoneNumber AS tenantPhone,
            r.roomNumber AS roomNumber,
            b.name AS buildingName
        FROM invoices i
        INNER JOIN tenancies tc ON i.tenancyId = tc.tenancyId
        INNER JOIN tenants t ON tc.tenantId = t.tenantId
        INNER JOIN rooms r ON tc.roomId = r.roomId
        INNER JOIN buildings b ON r.buildingId = b.buildingId
        WHERE i.invoiceId = :invoiceId
        LIMIT 1
    """)
    suspend fun getInvoiceDetailById(
        invoiceId: Long
    ): InvoiceWithDetails?

    @Query("""
        SELECT
            i.*,
            t.fullName AS tenantName,
            t.phoneNumber AS tenantPhone,
            r.roomNumber AS roomNumber,
            b.name AS buildingName
        FROM invoices i
        INNER JOIN tenancies tc ON i.tenancyId = tc.tenancyId
        INNER JOIN tenants t ON tc.tenantId = t.tenantId
        INNER JOIN rooms r ON tc.roomId = r.roomId
        INNER JOIN buildings b ON r.buildingId = b.buildingId
        WHERE i.invoiceId = :invoiceId
        LIMIT 1
    """)
    fun observeInvoiceDetailById(
        invoiceId: Long
    ): Flow<InvoiceWithDetails?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(
        invoice: InvoiceEntity
    ): Long

    @Update
    suspend fun updateInvoice(
        invoice: InvoiceEntity
    )

    @Query("""
        UPDATE invoices
        SET status = :status
        WHERE invoiceId = :invoiceId
    """)
    suspend fun updateStatus(
        invoiceId: Long,
        status: String
    )

    @Query("""
        UPDATE invoices
        SET amountPaidKhr = :amountPaid,
            balanceKhr = :balance,
            status = :status
        WHERE invoiceId = :invoiceId
    """)
    suspend fun updatePaymentBalances(
        invoiceId: Long,
        amountPaid: Long,
        balance: Long,
        status: String
    )

    @Query("""
        UPDATE invoices
        SET status = 'OVERDUE'
        WHERE dueDate < :currentTimestamp
          AND balanceKhr > 0
          AND status IN (
              'ISSUED',
              'UNPAID',
              'PARTIAL',
              'PARTIALLY_PAID'
          )
    """)
    suspend fun markOverdueInvoices(
        currentTimestamp: Long = System.currentTimeMillis()
    )
    @Query("""
        SELECT EXISTS(SELECT 1 FROM payments
        WHERE invoiceId = :invoiceId AND UPPER(TRIM(status)) = 'ACTIVE')
    """)
    suspend fun hasActivePayments(invoiceId: Long): Boolean

    @Query("""
        SELECT EXISTS(SELECT 1 FROM move_out_settlements WHERE finalInvoiceId = :invoiceId)
    """)
    suspend fun isLinkedToMoveOutSettlement(invoiceId: Long): Boolean

    @Query("""
        UPDATE invoices
        SET status = 'VOID', balanceKhr = 0, voidedAt = :voidedAt, voidReason = :reason
        WHERE invoiceId = :invoiceId
          AND status IN ('ISSUED', 'UNPAID', 'OVERDUE')
          AND amountPaidKhr = 0
          AND NOT EXISTS(
              SELECT 1 FROM payments p WHERE p.invoiceId = invoices.invoiceId
              AND UPPER(TRIM(p.status)) = 'ACTIVE'
          )
          AND NOT EXISTS(
              SELECT 1 FROM move_out_settlements s WHERE s.finalInvoiceId = invoices.invoiceId
          )
    """)
    suspend fun voidUnpaidInvoice(invoiceId: Long, reason: String, voidedAt: Long): Int

    @Query("SELECT * FROM invoices WHERE replacesInvoiceId=:originalId LIMIT 1")
    suspend fun getReplacementForInvoice(originalId: Long): InvoiceEntity?

    @Query("""SELECT EXISTS(SELECT 1 FROM invoices WHERE tenancyId=:tenancyId
        AND billingMonth=:month AND billingYear=:year AND invoiceKind=:kind
        AND UPPER(TRIM(status)) NOT IN ('VOID','VOIDED'))""")
    suspend fun hasCurrentInvoiceForKind(tenancyId: Long, month: Int, year: Int, kind: String): Boolean

    @Query("""SELECT COALESCE(MAX(revision),0) FROM invoices WHERE tenancyId=:tenancyId
        AND billingMonth=:month AND billingYear=:year AND invoiceKind=:kind""")
    suspend fun getMaxInvoiceRevision(tenancyId: Long, month: Int, year: Int, kind: String): Int

    @Query("""SELECT EXISTS(SELECT 1 FROM invoices WHERE tenancyId=:tenancyId
        AND billingMonth=:month AND billingYear=:year
        AND UPPER(TRIM(status)) IN ('ISSUED','UNPAID','PARTIAL','PARTIALLY_PAID','PAID','OVERDUE'))""")
    suspend fun hasIssuedInvoiceForPeriod(tenancyId: Long, month: Int, year: Int): Boolean


    /** Only unused regular drafts can be permanently removed. Never delete audit history. */
    @Query("""DELETE FROM invoices WHERE invoiceId=:id
        AND UPPER(TRIM(status))='DRAFT' AND invoiceKind='REGULAR'
        AND replacesInvoiceId IS NULL AND amountPaidKhr=0 AND balanceKhr=0
        AND NOT EXISTS(SELECT 1 FROM payments WHERE invoiceId=:id)
        AND NOT EXISTS(SELECT 1 FROM invoices WHERE replacesInvoiceId=:id)
        AND NOT EXISTS(SELECT 1 FROM move_out_settlements WHERE finalInvoiceId=:id)""")
    suspend fun deleteUnusedRegularDraft(id: Long): Int
    @Transaction
    @Query("""
        SELECT
            i.*,
            t.fullName AS tenantName,
            t.phoneNumber AS tenantPhone,
            r.roomNumber AS roomNumber,
            b.name AS buildingName
        FROM invoices i
        INNER JOIN tenancies tc ON i.tenancyId = tc.tenancyId
        INNER JOIN tenants t ON tc.tenantId = t.tenantId
        INNER JOIN rooms r ON tc.roomId = r.roomId
        INNER JOIN buildings b ON r.buildingId = b.buildingId
        WHERE i.invoiceId = :invoiceId
        LIMIT 1
    """)
    fun observeInvoiceDetailWithItems(invoiceId: Long): Flow<InvoiceDetailWithItems?>

    @Transaction
    @Query("""
        SELECT
            i.*,
            t.fullName AS tenantName,
            t.phoneNumber AS tenantPhone,
            r.roomNumber AS roomNumber,
            b.name AS buildingName
        FROM invoices i
        INNER JOIN tenancies tc ON i.tenancyId = tc.tenancyId
        INNER JOIN tenants t ON tc.tenantId = t.tenantId
        INNER JOIN rooms r ON tc.roomId = r.roomId
        INNER JOIN buildings b ON r.buildingId = b.buildingId
        WHERE i.invoiceId = :invoiceId
        LIMIT 1
    """)
    suspend fun getInvoiceDetailWithItems(invoiceId: Long): InvoiceDetailWithItems?
}
