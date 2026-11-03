package com.example.roomkit.Date.Local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.roomkit.Date.Local.Entity.BillingDraftAdjustmentEntity

@Dao
interface BillingDraftAdjustmentDao {
    @Query("SELECT * FROM billing_draft_adjustments WHERE tenancyId=:tenancyId AND billingMonth=:month AND billingYear=:year")
    suspend fun get(tenancyId: Long, month: Int, year: Int): BillingDraftAdjustmentEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: BillingDraftAdjustmentEntity)
    @Query("DELETE FROM billing_draft_adjustments WHERE tenancyId=:tenancyId AND billingMonth=:month AND billingYear=:year")
    suspend fun delete(tenancyId: Long, month: Int, year: Int)
}
