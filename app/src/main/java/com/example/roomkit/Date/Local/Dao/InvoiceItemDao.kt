package com.example.roomkit.Date.Local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.roomkit.Date.Local.Entity.InvoiceItemEntity

@Dao
interface InvoiceItemDao {
    @Query("SELECT * FROM invoice_items WHERE invoiceId=:invoiceId ORDER BY sortOrder, itemCode")
    suspend fun getForInvoice(invoiceId: Long): List<InvoiceItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<InvoiceItemEntity>)

    // Triggers reject deletion for issued, paid, partial, overdue and void invoices.
    @Query("DELETE FROM invoice_items WHERE invoiceId=:invoiceId")
    suspend fun deleteForDraft(invoiceId: Long)
}
